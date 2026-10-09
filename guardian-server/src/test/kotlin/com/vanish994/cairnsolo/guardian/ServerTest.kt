package com.vanish994.cairnsolo.guardian

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerTest {
    @Test
    fun guardianResponseSchemaUsesOnlyGeminiSupportedKeywords() {
        val unsupported = mutableSetOf<String>()
        val supported = setOf(
            "type", "properties", "required", "additionalProperties", "items", "anyOf", "prefixItems",
            "enum", "minimum", "maximum", "minItems", "maxItems", "format", "title", "description"
        )

        fun inspect(schema: com.google.gson.JsonObject, path: String) {
            schema.keySet().filterNot { it in supported }.forEach { unsupported += "$path.$it" }
            schema.get("properties")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.entrySet()
                ?.forEach { property ->
                    property.value.takeIf { it.isJsonObject }?.asJsonObject?.let { inspect(it, "$path.${property.key}") }
                }
            schema.get("items")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.let { inspect(it, "$path.items") }
            listOf("anyOf", "prefixItems").forEach { keyword ->
                schema.get(keyword)
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.forEachIndexed { index, child ->
                        child.takeIf { it.isJsonObject }?.asJsonObject?.let { inspect(it, "$path.$keyword[$index]") }
                    }
            }
        }

        inspect(guardianResponseSchema(), "$")

        assertTrue(unsupported.isEmpty(), "Unsupported Gemini schema keywords remain: $unsupported")
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
    fun guardianResponseSchemaKeepsRuleRequestShallowAndNullable() {
        val ruleRequest = guardianResponseSchema()
            .getAsJsonObject("properties")
            .getAsJsonObject("ruleRequest")
        val properties = ruleRequest.getAsJsonObject("properties")
        val allowedTypes = setOf(
            "SAVE", "DAMAGE", "FATIGUE", "REST", "STABILIZE_CRITICAL", "RECOVER_SCAR", "BEGIN_COMBAT"
        )

        assertEquals(listOf("object", "null"), ruleRequest.getAsJsonArray("type").map { it.asString })
        assertEquals(setOf("type"), properties.keySet())
        assertEquals(allowedTypes, properties.getAsJsonObject("type").getAsJsonArray("enum").map { it.asString }.toSet())
        assertEquals(listOf("type"), ruleRequest.getAsJsonArray("required").map { it.asString })
        assertTrue(ruleRequest.get("additionalProperties").asBoolean)
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
        val response = validEncounterResponse()

        val normalized = normalizeCombatProposal(response)

        assertEquals(3, normalized.getAsJsonObject("ruleRequest")
            .getAsJsonObject("encounter").getAsJsonArray("opponents")[0].asJsonObject
            .getAsJsonObject("stats").get("hp").asInt)
    }

    @Test
    fun fractionalHpProposalIsRejectedWithoutLosingNarration() {
        val response = validEncounterResponse()
        val stats = response.getAsJsonObject("ruleRequest").getAsJsonObject("encounter")
            .getAsJsonArray("opponents")[0].asJsonObject.getAsJsonObject("stats")
        stats.addProperty("hp", 1.9)
        stats.addProperty("maxHp", 2.1)

        assertEncounterRejectedWithoutLosingNarration(response)
    }

    @Test
    fun invalidArmorWeaponNarrativeOrIdentifiersAreRejectedWithoutLosingNarration() {
        val invalidResponses = listOf(
            validEncounterResponse().apply {
                getAsJsonObject("ruleRequest").getAsJsonObject("encounter").getAsJsonArray("opponents")[0]
                    .asJsonObject.addProperty("opponentId", "  ")
            },
            validEncounterResponse().apply {
                getAsJsonObject("ruleRequest").getAsJsonObject("encounter").getAsJsonArray("opponents")[0]
                    .asJsonObject.getAsJsonObject("stats").addProperty("armor", 4)
            },
            validEncounterResponse().apply {
                getAsJsonObject("ruleRequest").getAsJsonObject("encounter").getAsJsonArray("opponents")[0]
                    .asJsonObject.getAsJsonObject("weapon").addProperty("damage", "d20")
            },
            validEncounterResponse().apply {
                getAsJsonObject("ruleRequest").getAsJsonObject("encounter").getAsJsonArray("opponents")[0]
                    .asJsonObject.getAsJsonObject("narrative").addProperty("name", "  ")
            },
            validEncounterResponse().apply {
                getAsJsonObject("ruleRequest").getAsJsonObject("encounter").getAsJsonArray("opponents")[0]
                    .asJsonObject.getAsJsonObject("weapon").addProperty("id", "  ")
            }
        )

        invalidResponses.forEach(::assertEncounterRejectedWithoutLosingNarration)
    }

    @Test
    fun suggestedActionsSchemaRequiresOneToThreeStringItems() {
        val schema = suggestedActionsSchema()
        val items = schema.getAsJsonObject("items")

        assertEquals("array", schema.get("type").asString)
        assertEquals(1, schema.get("minItems").asInt)
        assertEquals(3, schema.get("maxItems").asInt)
        assertEquals("string", items.get("type").asString)
        assertFalse(items.has("minLength"))
        assertFalse(items.has("maxLength"))
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

    @Test
    fun promptDefinesEncounterContextAsApprovedOpponentListWithoutMechanicalAuthority() {
        val prompt = guardianSystemPrompt().lowercase()
        val encounterInstruction = prompt
            .substringAfter("se encountercontext estiver presente", missingDelimiterValue = "")
            .substringBefore("\n\n")

        assertTrue(encounterInstruction.isNotBlank())
        assertTrue(encounterInstruction.contains("lista") || encounterInstruction.contains("array"))
        assertTrue(encounterInstruction.contains("opponentid"))
        assertTrue(encounterInstruction.contains("narrative"))
        assertTrue(encounterInstruction.contains("aprovad"))
        assertTrue(prompt.contains("ruleresult") && prompt.contains("única autoridade"))
        assertTrue(encounterInstruction.contains("não autoriza") && encounterInstruction.contains("resultados mecânicos"))
    }

    @Test
    fun promptDefinesCompleteCombatProposalFieldsForShallowWireSchema() {
        val prompt = guardianSystemPrompt().lowercase()
        val requiredGuidance = listOf(
            "begin_combat", "1 a 8", "opponentid", "narrative", "appearance", "behavior", "intent", "context",
            "stats", "str", "dex", "wil", "hp", "maxhp", "armor", "weapon", "damage", "blast", "ranged",
            "moraleleaderid", "maxhp >= hp", "0 a 3", "d4", "d12"
        )

        requiredGuidance.forEach { term ->
            assertTrue(prompt.contains(term), "Prompt is missing combat proposal guidance: $term")
        }
    }

    private fun assertEncounterRejectedWithoutLosingNarration(response: com.google.gson.JsonObject) {
        val normalized = normalizeCombatProposal(response)

        assertEquals("A cena continua.", normalized.get("narration").asString)
        assertEquals("BEGIN_COMBAT", normalized.getAsJsonObject("ruleRequest").get("type").asString)
        assertFalse(normalized.getAsJsonObject("ruleRequest").has("encounter"))
    }

    private fun validEncounterResponse() = JsonParser.parseString(
        """{"narration":"A cena continua.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponents":[{"opponentId":"cultist-a","narrative":{"name":"Cultista","appearance":"Manto escuro.","behavior":"Observa a passagem.","intent":"Protege o altar.","context":"Na capela."},"stats":{"str":5,"dex":7,"wil":8,"hp":3,"maxHp":4,"armor":1},"weapon":{"id":"ritual-dagger","damage":"d4","blast":false,"ranged":false}}]}}}"""
    ).asJsonObject
}
