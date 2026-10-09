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
    fun guardianResponseSchemaRequiresTypeSpecificRuleRequestFields() {
        val responseSchema = guardianResponseSchema()
        val responseRequired = responseSchema.getAsJsonArray("required").map { it.asString }.toSet()
        assertTrue("actionIntent" in responseRequired)
        val actionIntent = responseSchema.getAsJsonObject("properties").getAsJsonObject("actionIntent")
        assertTrue(actionIntent.getAsJsonArray("anyOf").any { it.asJsonObject.get("type")?.asString == "null" })
        val attackIntentSchema = actionIntent.getAsJsonArray("anyOf").single {
            it.asJsonObject.get("type")?.asString == "object"
        }.asJsonObject
        assertEquals(setOf("type", "targetId", "targetName", "weaponId"), attackIntentSchema.getAsJsonObject("properties").keySet())
        assertEquals(setOf("type", "targetId", "targetName", "weaponId"), attackIntentSchema.getAsJsonArray("required").map { it.asString }.toSet())

        val ruleRequest = guardianResponseSchema()
            .getAsJsonObject("properties")
            .getAsJsonObject("ruleRequest")
        val alternatives = ruleRequest.getAsJsonArray("anyOf").map { it.asJsonObject }
        assertTrue(alternatives.any { it.get("type").asString == "null" })

        fun branch(types: Set<String>) = alternatives.single { alternative ->
            alternative.get("type")?.asString == "object" &&
                alternative.getAsJsonObject("properties")
                    .getAsJsonObject("type")
                    .getAsJsonArray("enum")
                    .map { it.asString }
                    .toSet() == types
        }

        val save = branch(setOf("SAVE"))
        assertEquals(listOf("type", "attribute"), save.getAsJsonArray("required").map { it.asString })
        assertEquals(setOf("type", "attribute"), save.getAsJsonObject("properties").keySet())
        assertEquals(
            setOf("STR", "DEX", "WIL"),
            save.getAsJsonObject("properties").getAsJsonObject("attribute").getAsJsonArray("enum").map { it.asString }.toSet()
        )

        val damage = branch(setOf("DAMAGE", "FATIGUE"))
        assertEquals(listOf("type", "amount"), damage.getAsJsonArray("required").map { it.asString })
        val amount = damage.getAsJsonObject("properties").getAsJsonObject("amount")
        assertEquals("integer", amount.get("type").asString)
        assertEquals(1, amount.get("minimum").asInt)

        val noPayload = branch(setOf("REST", "STABILIZE_CRITICAL", "RECOVER_SCAR"))
        assertEquals(listOf("type"), noPayload.getAsJsonArray("required").map { it.asString })

        val combat = branch(setOf("BEGIN_COMBAT"))
        assertEquals(listOf("type", "encounter"), combat.getAsJsonArray("required").map { it.asString })
        val encounter = combat.getAsJsonObject("properties").getAsJsonObject("encounter")
        assertEquals(combatWireEncounterSchema(), encounter)
        assertEquals(setOf("opponentsJson", "moraleLeaderId"), encounter.getAsJsonObject("properties").keySet())
        assertEquals(setOf("opponentsJson"), encounter.getAsJsonArray("required").map { it.asString }.toSet())
        assertFalse(encounter.get("additionalProperties").asBoolean)
        val reward = branch(setOf("REWARD"))
        assertEquals(listOf("type", "id", "status", "amountGp", "itemCatalogIds"), reward.getAsJsonArray("required").map { it.asString })
        assertEquals(setOf("type", "id", "status", "amountGp", "itemCatalogIds"), reward.getAsJsonObject("properties").keySet())
        assertTrue(alternatives.filter { it.get("type")?.asString == "object" }
            .all { !it.get("additionalProperties").asBoolean })
    }

    @Test
    fun promptRequiresTypeSpecificRuleRequestFields() {
        val prompt = guardianSystemPrompt().lowercase()

        assertTrue(prompt.contains("save") && prompt.contains("attribute") && prompt.contains("str, dex ou wil"))
        assertTrue(prompt.contains("damage") && prompt.contains("fatigue") && prompt.contains("amount"))
        assertTrue(prompt.contains("inteiro") && prompt.contains("1"))
        assertTrue(prompt.contains("begin_combat") && prompt.contains("encounter"))
        assertTrue(prompt.contains("actionintent") && prompt.contains("targetid") && prompt.contains("targetname") && prompt.contains("weaponid"))
        assertTrue(prompt.contains("ids estáveis") && prompt.contains("campanha"))
        assertTrue(prompt.contains("não acrescente rolagem de acerto com d20"))
        assertTrue(prompt.contains("ausência de cadastro mecânico não invalida a ação"))
    }

    @Test
    fun campaignOpeningPromptTreatsStartMarkerAsInternalAndRequiresVariety() {
        val prompt = guardianSystemPrompt().lowercase()

        assertTrue(prompt.contains("iniciar_campanha"))
        assertTrue(prompt.contains("não mencione") || prompt.contains("não o mencione"))
        assertTrue(prompt.contains("não use prólogo"))
        assertTrue(prompt.contains("não solicite novamente dados já coletados"))
        assertTrue(prompt.contains("mundo") && prompt.contains("campaignseed"))
        assertTrue(prompt.contains("sugestões") || prompt.contains("suggestedactions"))
    }

    @Test
    fun systemPromptUsesTheConsolidatedProjectPromptResource() {
        val prompt = guardianSystemPrompt()

        assertTrue(prompt.startsWith("CAIRN SOLO RPG — GUARDIÃO NARRATIVO"))
        assertTrue(prompt.contains("CAMPANHAS E MUNDO DINÂMICO"))
        assertTrue(prompt.contains("ABERTURA DE CAMPANHA"))
        assertTrue(prompt.lowercase().contains("rules engine é a única autoridade"))
        assertFalse(prompt.contains("Cinzália"))
    }

    @Test
    fun campaignOpeningNormalizationRemovesMechanicalRequestButKeepsNarrationAndActions() {
        val response = JsonParser.parseString(
            """{"narration":"A névoa cobre a passagem.","suggestedActions":["Examinar as marcas"],"actionIntent":{"type":"ATTACK","targetId":"npc-1","targetName":"Viajante","weaponId":null},"ruleRequest":{"type":"REWARD","id":"opening-pay","status":"PAID","amountGp":12,"itemCatalogIds":[]}}"""
        ).asJsonObject

        val normalized = normalizeCampaignOpening(response, "INICIAR_CAMPANHA")

        assertTrue(normalized.get("ruleRequest").isJsonNull)
        assertTrue(normalized.get("actionIntent").isJsonNull)
        assertEquals("A névoa cobre a passagem.", normalized.get("narration").asString)
        assertEquals("Examinar as marcas", normalized.getAsJsonArray("suggestedActions")[0].asString)
        assertEquals(0, normalized.getAsJsonArray("growthEvidenceProposals").size())
        assertEquals(0, normalized.getAsJsonArray("growthChangeProposals").size())
    }

    @Test
    fun ordinaryIntentKeepsMechanicalRequestDuringNormalization() {
        val response = JsonParser.parseString(
            """{"narration":"A porta range.","ruleRequest":{"type":"SAVE","attribute":"DEX"}}"""
        ).asJsonObject

        val normalized = normalizeCampaignOpening(response, "Abro a porta")

        assertEquals("SAVE", normalized.getAsJsonObject("ruleRequest").get("type").asString)
    }

    @Test
    fun rewardSchemaRequiresGpStatusAndAtMostFiveCatalogIdsWithoutUnsupportedTextKeywords() {
        val schema = rewardRequestSchema()
        val properties = schema.getAsJsonObject("properties")
        val id = properties.getAsJsonObject("id")
        val items = properties.getAsJsonObject("itemCatalogIds")
        val item = items.getAsJsonObject("items")

        assertEquals("object", schema.get("type").asString)
        assertEquals(
            setOf("type", "id", "status", "amountGp", "itemCatalogIds"),
            schema.getAsJsonArray("required").map { it.asString }.toSet()
        )
        assertEquals(0, properties.getAsJsonObject("amountGp").get("minimum").asInt)
        assertEquals(Int.MAX_VALUE, properties.getAsJsonObject("amountGp").get("maximum").asInt)
        assertEquals(5, items.get("maxItems").asInt)
        assertEquals("string", item.get("type").asString)
        assertEquals(
            setOf("OFFERED", "PAID"),
            properties.getAsJsonObject("status").getAsJsonArray("enum").map { it.asString }.toSet()
        )
        assertFalse(schema.get("additionalProperties").asBoolean)
        listOf(id, item).forEach { textSchema ->
            assertFalse(textSchema.has("pattern"))
            assertFalse(textSchema.has("minLength"))
            assertFalse(textSchema.has("maxLength"))
        }
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
        val normalized = normalizeRewardProposal(validRewardResponse())

        assertEquals("A recompensa foi entregue.", normalized.get("narration").asString)
        assertEquals(2, normalized.getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds").size())
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
            validRewardResponse().apply {
                val ids = getAsJsonObject("ruleRequest").getAsJsonArray("itemCatalogIds")
                repeat(4) { ids.add("unknown-$it") }
            },
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
        assertTrue(prompt.contains("não invente conversão de cobre"))
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
    fun serializedEncounterIsExpandedAndValidatedBeforeReturningToTheApp() {
        val response = validEncounterResponse()
        val encounter = response.getAsJsonObject("ruleRequest").getAsJsonObject("encounter")
        val profiles = encounter.remove("opponents")
        encounter.addProperty("opponentsJson", profiles.toString())
        encounter.addProperty("moraleLeaderId", "cultist-a")

        val normalized = normalizeCombatProposal(response)
        val expanded = normalized.getAsJsonObject("ruleRequest").getAsJsonObject("encounter")

        assertEquals(setOf("opponents", "moraleLeaderId"), expanded.keySet())
        assertEquals("cultist-a", expanded.get("moraleLeaderId").asString)
        assertEquals(3, expanded.getAsJsonArray("opponents")[0].asJsonObject
            .getAsJsonObject("stats").get("hp").asInt)
    }

    @Test
    fun malformedSerializedEncounterIsRemovedWithoutLosingNarration() {
        val response = JsonParser.parseString(
            """{"narration":"A cena continua.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponentsJson":"não é JSON"}}}"""
        ).asJsonObject

        val normalized = normalizeCombatProposal(response)

        assertEquals("A cena continua.", normalized.get("narration").asString)
        assertFalse(normalized.getAsJsonObject("ruleRequest").has("encounter"))
    }

    @Test
    fun serializedEncounterWithInvalidCairnArmorIsRejectedAfterExpansion() {
        val response = validEncounterResponse()
        val encounter = response.getAsJsonObject("ruleRequest").getAsJsonObject("encounter")
        val opponents = encounter.getAsJsonArray("opponents")
        opponents[0].asJsonObject.getAsJsonObject("stats").addProperty("armor", 4)
        encounter.remove("opponents")
        encounter.addProperty("opponentsJson", opponents.toString())

        val normalized = normalizeCombatProposal(response)

        assertEquals("A cena continua.", normalized.get("narration").asString)
        assertFalse(normalized.getAsJsonObject("ruleRequest").has("encounter"))
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
            .substringAfter("sugestões e resposta", missingDelimiterValue = "")
            .substringBefore("\n\n")

        assertTrue(suggestionInstruction.isNotBlank())
        assertTrue(suggestionInstruction.contains("recomendações opcionais"))
        assertTrue(suggestionInstruction.contains("availableactions"))
        assertTrue(suggestionInstruction.contains("scene"))
        assertTrue(suggestionInstruction.contains("contexto da campanha"))
        assertTrue(suggestionInstruction.contains("não limitam ações livres"))
    }

    @Test
    fun promptDefinesEncounterContextAsApprovedOpponentListWithoutMechanicalAuthority() {
        val prompt = guardianSystemPrompt().lowercase()
        assertTrue(prompt.contains("knownnpcs"))
        assertTrue(prompt.contains("combatprofile"))
        assertTrue(prompt.contains("perfil narrativo aprovado") || prompt.contains("perfil mecânico") )
        assertTrue(prompt.contains("rules engine é a única autoridade"))
        assertTrue(prompt.contains("nunca invente rolagem"))
    }

    @Test
    fun promptDefinesCompleteCombatProposalFieldsForShallowWireSchema() {
        val prompt = guardianSystemPrompt().lowercase()
        val requiredGuidance = listOf(
            "begin_combat", "perfis novos", "oponentes participantes", "str", "dex", "wil", "hp", "maxhp",
            "armor", "weapon", "d4", "d12", "aplicativo mostra o perfil para aprovação"
        )

        requiredGuidance.forEach { term ->
            val present = when (term) {
                "oponentes participantes" -> prompt.contains("oponente participante")
                else -> prompt.contains(term)
            }
            assertTrue(present, "Prompt is missing combat proposal guidance: $term")
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

    private fun validRewardResponse() = JsonParser.parseString(
        """{"narration":"A recompensa foi entregue.","ruleRequest":{"type":"REWARD","id":"quest-pay","status":"PAID","amountGp":12,"itemCatalogIds":["dagger","dagger"]}}"""
    ).asJsonObject
}
