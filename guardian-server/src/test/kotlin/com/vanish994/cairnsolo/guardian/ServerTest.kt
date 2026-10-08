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
    fun invalidArmorWeaponOrNarrativeIsRejectedWithoutLosingNarration() {
        val invalidResponses = listOf(
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
            }
        )

        invalidResponses.forEach(::assertEncounterRejectedWithoutLosingNarration)
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
    fun rewardSchemaRequiresGpStatusAndAtMostFiveCatalogIds() {
        val schema = rewardRequestSchema()
        val properties = schema.getAsJsonObject("properties")

        assertEquals("object", schema.get("type").asString)
        assertEquals(
            setOf("type", "id", "status", "amountGp", "itemCatalogIds"),
            schema.getAsJsonArray("required").map { it.asString }.toSet()
        )
        assertEquals(0, properties.getAsJsonObject("amountGp").get("minimum").asInt)
        assertEquals(Int.MAX_VALUE, properties.getAsJsonObject("amountGp").get("maximum").asInt)
        assertEquals(5, properties.getAsJsonObject("itemCatalogIds").get("maxItems").asInt)
        assertEquals(
            setOf("OFFERED", "PAID"),
            properties.getAsJsonObject("status").getAsJsonArray("enum").map { it.asString }.toSet()
        )
        assertFalse(schema.get("additionalProperties").asBoolean)
    }

    @Test
    fun rewardSchemaBranchIsAddedToRuleRequestAlternatives() {
        val properties = JsonParser.parseString("""{"ruleRequest":{"anyOf":[]}}""").asJsonObject

        addRewardRequestSchema(properties)

        val alternatives = properties.getAsJsonObject("ruleRequest").getAsJsonArray("anyOf")
        assertEquals(1, alternatives.size())
        assertEquals("REWARD", alternatives[0].asJsonObject.getAsJsonObject("properties")
            .getAsJsonObject("type").getAsJsonArray("enum")[0].asString)
    }

    @Test
    fun rewardNormalizerPreservesValidProposalIncludingRepeatedCatalogIds() {
        val response = validRewardResponse()

        val normalized = normalizeRewardProposal(response)

        assertEquals("A recompensa foi entregue.", normalized.get("narration").asString)
        assertEquals(
            2,
            normalized.getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds").size()
        )
    }

    @Test
    fun rewardNormalizerCanonicalizesLowercaseType() {
        val response = validRewardResponse().apply {
            getAsJsonObject("ruleRequest").addProperty("type", "reward")
        }

        val normalized = normalizeRewardProposal(response)
        val request = normalized.getAsJsonObject("ruleRequest")

        assertEquals("A recompensa foi entregue.", normalized.get("narration").asString)
        assertEquals("REWARD", request.get("type").asString)
        assertEquals(2, request.getAsJsonArray("itemCatalogIds").size())
    }

    @Test
    fun malformedRewardProposalIsStrippedWithoutLosingNarrationOrRequestType() {
        val invalidResponses = listOf(
            validRewardResponse().apply { getAsJsonObject("ruleRequest").addProperty("amountGp", -1) },
            validRewardResponse().apply { getAsJsonObject("ruleRequest").addProperty("amountGp", 2147483648L) },
            validRewardResponse().apply { getAsJsonObject("ruleRequest").addProperty("amountGp", 1.5) },
            validRewardResponse().apply { getAsJsonObject("ruleRequest").addProperty("status", "PROMISED") },
            validRewardResponse().apply { getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds").add("unknown-1"); getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds").add("unknown-2"); getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds").add("unknown-3"); getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds").add("unknown-4"); getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds").add("unknown-5"); getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds").add("unknown-6") },
            validRewardResponse().apply { getAsJsonObject("ruleRequest").addProperty("copper", 12) },
            validRewardResponse().apply {
                getAsJsonObject("ruleRequest").addProperty("amountGp", 0)
                getAsJsonObject("ruleRequest").add("itemCatalogIds", JsonParser.parseString("[]"))
            }
        )

        invalidResponses.forEach { response ->
            val normalized = normalizeRewardProposal(response)
            val request = normalized.getAsJsonObject("ruleRequest")
            assertEquals("A recompensa foi entregue.", normalized.get("narration").asString)
            assertEquals("REWARD", request.get("type").asString)
            assertEquals(setOf("type"), request.keySet())
        }
    }

    @Test
    fun rewardPromptDistinguishesOfferedFromPaidAndUsesGpCatalogContext() {
        val prompt = guardianSystemPrompt().lowercase()

        assertTrue(prompt.contains("offered"))
        assertTrue(prompt.contains("paid"))
        assertTrue(prompt.contains("amountgp"))
        assertTrue(prompt.contains("rewardableitems"))
        assertTrue(prompt.contains("não converta cobre"))
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

    private fun validRewardResponse() = JsonParser.parseString(
        """{"narration":"A recompensa foi entregue.","ruleRequest":{"type":"REWARD","id":"quest-pay","status":"PAID","amountGp":12,"itemCatalogIds":["dagger","dagger"]}}"""
    ).asJsonObject
}
