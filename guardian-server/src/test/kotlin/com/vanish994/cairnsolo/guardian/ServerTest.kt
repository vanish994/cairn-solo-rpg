package com.vanish994.cairnsolo.guardian

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerTest {
    @Test
    fun encounterSchemaRejectsBlankNarrativeAndIdentifiers() {
        val properties = combatEncounterSchema().getAsJsonObject("properties")
        val opponent = properties.getAsJsonObject("opponents").getAsJsonObject("items").getAsJsonObject("properties")
        assertEquals("^\\S(?:.*\\S)?$", opponent.getAsJsonObject("opponentId").get("pattern").asString)
        assertEquals("\\S", opponent.getAsJsonObject("narrative").getAsJsonObject("properties")
            .getAsJsonObject("name").get("pattern").asString)
        assertEquals("\\S", opponent.getAsJsonObject("weapon").getAsJsonObject("properties")
            .getAsJsonObject("id").get("pattern").asString)
    }

    @Test
    fun encounterSchemaRequiresCompleteOpponentList() {
        val schema = combatEncounterSchema()
        val required = schema.getAsJsonArray("required").map { it.asString }.toSet()
        val properties = schema.getAsJsonObject("properties")
        val opponents = properties.getAsJsonObject("opponents")
        val opponent = opponents.getAsJsonObject("items")
        val opponentProperties = opponent.getAsJsonObject("properties")

        assertEquals("array", opponents.get("type").asString)
        assertEquals(1, opponents.get("minItems").asInt)
        assertEquals(8, opponents.get("maxItems").asInt)
        assertTrue("opponents" in required)
        assertFalse("moraleLeaderId" in required)
        assertEquals("string", properties.getAsJsonObject("moraleLeaderId").get("type").asString)
        assertEquals(
            setOf("opponentId", "narrative", "stats", "weapon"),
            opponent.getAsJsonArray("required").map { it.asString }.toSet()
        )
        assertEquals(
            setOf("name", "appearance", "behavior", "intent", "context"),
            opponentProperties.getAsJsonObject("narrative").getAsJsonArray("required").map { it.asString }.toSet()
        )
        assertEquals(
            setOf("str", "dex", "wil", "hp", "maxHp", "armor"),
            opponentProperties.getAsJsonObject("stats").getAsJsonArray("required").map { it.asString }.toSet()
        )
        assertEquals(
            setOf("id", "damage", "blast", "ranged"),
            opponentProperties.getAsJsonObject("weapon").getAsJsonArray("required").map { it.asString }.toSet()
        )
    }

    @Test
    fun inconsistentHpProposalIsMadeIncompleteWithoutLosingNarration() {
        val response = JsonParser.parseString(
            """{"narration":"A cena continua.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponents":[{"opponentId":"cultist-a","stats":{"hp":5,"maxHp":4}}]}}}"""
        ).asJsonObject

        val normalized = normalizeCombatProposal(response)

        assertEquals("A cena continua.", normalized.get("narration").asString)
        assertEquals("BEGIN_COMBAT", normalized.getAsJsonObject("ruleRequest").get("type").asString)
        assertFalse(normalized.getAsJsonObject("ruleRequest").has("encounter"))
    }

    @Test
    fun consistentHpProposalIsPreserved() {
        val response = JsonParser.parseString(
            """{"ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponents":[{"opponentId":"cultist-a","stats":{"hp":3,"maxHp":4}}]}}}"""
        ).asJsonObject

        val normalized = normalizeCombatProposal(response)

        assertEquals(3, normalized.getAsJsonObject("ruleRequest")
            .getAsJsonObject("encounter").getAsJsonArray("opponents")[0].asJsonObject
            .getAsJsonObject("stats").get("hp").asInt)
    }

    @Test
    fun suggestedActionsSchemaRequiresOneToThreeBoundedStrings() {
        val schema = suggestedActionsSchema()
        val items = schema.getAsJsonObject("items")

        assertEquals("array", schema.get("type").asString)
        assertEquals(1, schema.get("minItems").asInt)
        assertEquals(3, schema.get("maxItems").asInt)
        assertEquals("string", items.get("type").asString)
        assertEquals(1, items.get("minLength").asInt)
        assertEquals(160, items.get("maxLength").asInt)
    }

    @Test
    fun promptGroundsActionsInSceneCanonAndAvailableActions() {
        val prompt = guardianSystemPrompt().lowercase()
        val suggestionInstruction = prompt
            .substringAfter("ações sugeridas (suggestedactions):", missingDelimiterValue = "")
            .substringBefore("\n\n")

        assertTrue(suggestionInstruction.isNotBlank())
        assertTrue(suggestionInstruction.contains("objetivo imediato"))
        assertTrue(suggestionInstruction.contains("availableactions"))
        assertTrue(suggestionInstruction.contains("cena"))
        assertTrue(suggestionInstruction.contains("cânone") && Regex("\\bcanon\\b").containsMatchIn(suggestionInstruction))
        assertTrue(suggestionInstruction.contains("apoiad") || suggestionInstruction.contains("derivad") || suggestionInstruction.contains("basead"))
        assertTrue(suggestionInstruction.contains("não invent"))
    }
}
