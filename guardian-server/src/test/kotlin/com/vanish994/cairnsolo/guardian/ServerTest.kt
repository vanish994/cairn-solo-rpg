package com.vanish994.cairnsolo.guardian

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerTest {
    @Test
    fun encounterSchemaRejectsBlankNarrativeAndIdentifiers() {
        val properties = JsonParser.parseString(
            """{"ruleRequest":{"anyOf":[{"type":"null"},{"type":"object","properties":{"type":{"type":"string","enum":["BEGIN_COMBAT"]},"encounter":{"type":"object","properties":{"opponentId":{},"narrative":{"properties":{"name":{},"appearance":{},"behavior":{},"intent":{},"context":{}}},"weapon":{"properties":{"id":{}}}}}}}]}}"""
        ).asJsonObject

        addEncounterStringPatterns(properties)

        val beginCombat = properties.getAsJsonObject("ruleRequest").getAsJsonArray("anyOf")[1].asJsonObject
        val encounter = beginCombat.getAsJsonObject("properties").getAsJsonObject("encounter").getAsJsonObject("properties")
        assertEquals("^\\S(?:.*\\S)?$", encounter.getAsJsonObject("opponentId").get("pattern").asString)
        assertEquals("\\S", encounter.getAsJsonObject("narrative").getAsJsonObject("properties")
            .getAsJsonObject("name").get("pattern").asString)
        assertEquals("\\S", encounter.getAsJsonObject("weapon").getAsJsonObject("properties")
            .getAsJsonObject("id").get("pattern").asString)
    }

    @Test
    fun inconsistentHpProposalIsMadeIncompleteWithoutLosingNarration() {
        val response = JsonParser.parseString(
            """{"narration":"A cena continua.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"stats":{"hp":5,"maxHp":4}}}}"""
        ).asJsonObject

        val normalized = normalizeCombatProposal(response)

        assertEquals("A cena continua.", normalized.get("narration").asString)
        assertEquals("BEGIN_COMBAT", normalized.getAsJsonObject("ruleRequest").get("type").asString)
        assertFalse(normalized.getAsJsonObject("ruleRequest").has("encounter"))
    }

    @Test
    fun consistentHpProposalIsPreserved() {
        val response = JsonParser.parseString(
            """{"ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"stats":{"hp":3,"maxHp":4}}}}"""
        ).asJsonObject

        val normalized = normalizeCombatProposal(response)

        assertEquals(3, normalized.getAsJsonObject("ruleRequest")
            .getAsJsonObject("encounter").getAsJsonObject("stats").get("hp").asInt)
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
        assertTrue(suggestionInstruction.contains("availableactions"))
        assertTrue(suggestionInstruction.contains("cena"))
        assertTrue(suggestionInstruction.contains("cânone") || suggestionInstruction.contains("worldcanon"))
        assertTrue(suggestionInstruction.contains("apoiad") || suggestionInstruction.contains("derivad") || suggestionInstruction.contains("basead"))
        assertTrue(suggestionInstruction.contains("não invent"))
    }
}
