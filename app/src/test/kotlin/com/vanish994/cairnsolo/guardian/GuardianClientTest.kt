package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.WeaponProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardianClientTest {
    @Test
    fun parsesStructuredAttackIntentWithoutTreatingItAsAMechanicalResult() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A tensão aumenta.","sceneTitle":"Praça","sceneDescription":"Pedras úmidas.","actionIntent":{"type":"ATTACK","targetId":"mercenario-01","weaponId":"adaga"},"ruleRequest":null,"suggestedActions":[],"canonProposals":[],"growthEvidenceProposals":[],"growthChangeProposals":[]}"""
        )

        val intent = assertNotNull(response.actionIntent)
        assertEquals(GuardianActionType.ATTACK, intent.type)
        assertEquals("mercenario-01", intent.targetId)
        assertEquals("adaga", intent.weaponId)
        assertNull(response.ruleRequest)
    }

    @Test
    fun malformedStructuredAttackIntentIsIgnoredRatherThanExecuting() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A cena continua.","sceneTitle":"Praça","sceneDescription":"Pedras úmidas.","actionIntent":{"type":"ATTACK","targetId":"mercenario-01","weaponId":42},"ruleRequest":null,"suggestedActions":[]}"""
        )

        assertNull(response.actionIntent)
    }

    @Test
    fun parseBeginCombatProposalRetainsNarrativeAndStats() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{
              "narration":"Uma figura bloqueia o caminho.",
              "sceneTitle":"Passagem estreita",
              "sceneDescription":"A névoa cobre a trilha.",
              "ruleRequest":{
                "type":"BEGIN_COMBAT",
                "encounter":{
                  "opponents":[{
                    "opponentId":"wolf-alpha",
                    "narrative":{
                      "name":"Lobo cinzento",
                      "appearance":"Grande, com uma orelha rasgada.",
                      "behavior":"Ronda em círculos e rosna.",
                      "intent":"Protege a carcaça atrás de si.",
                      "context":"Encontrado na trilha ao anoitecer."
                    },
                    "stats":{"str":5,"dex":7,"wil":3,"hp":4,"maxHp":4,"armor":1},
                    "weapon":{"id":"fangs","damage":"d6","blast":false,"ranged":false}
                  }]
                }
              },
              "suggestedActions":[]
            }"""
        )

        assertEquals("Uma figura bloqueia o caminho.", response.narration)
        val encounter = assertNotNull(response.ruleRequest?.encounter)
        val opponent = encounter.opponents.single()
        assertEquals("wolf-alpha", opponent.opponentId)
        assertEquals("Lobo cinzento", opponent.narrative.name)
        assertEquals("Protege a carcaça atrás de si.", opponent.narrative.intent)
        assertEquals(5, opponent.stats.str)
        assertEquals(4, opponent.stats.hp)
        assertEquals(1, opponent.stats.armor)
        assertEquals("d6", opponent.weapon.damage)
        assertFalse(opponent.weapon.ranged)
    }

    @Test
    fun beginCombatParsesTwoDistinctOpponents() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{
              "narration":"Dois cultistas bloqueiam a trilha.",
              "sceneTitle":"Trilha",
              "sceneDescription":"A trilha está tomada.",
              "ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponents":[
                {"opponentId":"cultist-a","narrative":{"name":"Cultista da lamparina","appearance":"Manto cinza.","behavior":"Protege a chama.","intent":"Avança com uma adaga.","context":"À esquerda."},"stats":{"str":5,"dex":7,"wil":8,"hp":4,"maxHp":4,"armor":1},"weapon":{"id":"ritual-dagger","damage":"d4","blast":false,"ranged":false}},
                {"opponentId":"cultist-b","narrative":{"name":"Cultista do sino","appearance":"Capuz vermelho.","behavior":"Mantém distância.","intent":"Aponta uma lança.","context":"À direita."},"stats":{"str":8,"dex":4,"wil":6,"hp":6,"maxHp":6,"armor":2},"weapon":{"id":"rusted-spear","damage":"d8","blast":false,"ranged":true}}
              ],"moraleLeaderId":"cultist-b"}},
              "suggestedActions":[]
            }"""
        )

        assertEquals("Dois cultistas bloqueiam a trilha.", response.narration)
        val encounter = assertNotNull(response.ruleRequest?.encounter)
        assertEquals("cultist-b", encounter.moraleLeaderId)
        assertEquals(listOf("cultist-a", "cultist-b"), encounter.opponents.map { it.opponentId })
        val first = encounter.opponents[0]
        val second = encounter.opponents[1]
        assertEquals("Cultista da lamparina", first.narrative.name)
        assertEquals("Manto cinza.", first.narrative.appearance)
        assertEquals(5, first.stats.str)
        assertEquals(4, first.stats.hp)
        assertEquals("ritual-dagger", first.weapon.id)
        assertEquals("d4", first.weapon.damage)
        assertEquals("Cultista do sino", second.narrative.name)
        assertEquals("Capuz vermelho.", second.narrative.appearance)
        assertEquals(8, second.stats.str)
        assertEquals(6, second.stats.hp)
        assertEquals("rusted-spear", second.weapon.id)
        assertEquals("d8", second.weapon.damage)
    }

    @Test
    fun rejectsDuplicateOpponentIds() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A cena continua apesar da proposta inválida.","sceneTitle":"Trilha","sceneDescription":"Escura.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponents":[
              {"opponentId":"cultist-a","narrative":{"name":"Primeiro","appearance":"Manto.","behavior":"Espera.","intent":"Ataca.","context":"À esquerda."},"stats":{"str":5,"dex":7,"wil":8,"hp":4,"maxHp":4,"armor":1},"weapon":{"id":"dagger-a","damage":"d4","blast":false,"ranged":false}},
              {"opponentId":"cultist-a","narrative":{"name":"Segundo","appearance":"Capuz.","behavior":"Avança.","intent":"Ataca.","context":"À direita."},"stats":{"str":8,"dex":4,"wil":6,"hp":6,"maxHp":6,"armor":2},"weapon":{"id":"spear-b","damage":"d8","blast":false,"ranged":true}}
            ]}},"suggestedActions":[]}"""
        )

        assertEquals("A cena continua apesar da proposta inválida.", response.narration)
        assertEquals("BEGIN_COMBAT", response.ruleRequest?.type)
        assertNull(response.ruleRequest?.encounter)

        val firstOpponent = GuardianOpponentProposal(
            "cultist-a", CombatOpponentNarrative("Primeiro", "Manto.", "Espera.", "Ataca.", "À esquerda."),
            CharacterState(5, 7, 8, 4, 4, 1), WeaponProfile("dagger-a", "d4")
        )
        val duplicateOpponent = firstOpponent.copy(opponentId = "cultist-a")
        assertFailsWith<IllegalArgumentException> {
            GuardianEncounterProposal(opponents = listOf(firstOpponent, duplicateOpponent))
        }
    }

    @Test
    fun invalidBeginCombatProposalIsRetainedForVisibleRejection() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A cena continua.","sceneTitle":"Trilha","sceneDescription":"Escura.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponentId":"wolf"}},"suggestedActions":[]}"""
        )

        assertEquals("A cena continua.", response.narration)
        assertEquals("BEGIN_COMBAT", response.ruleRequest?.type)
        assertNull(response.ruleRequest?.encounter)
    }

    @Test
    fun unsupportedWeaponDamageIsRetainedForVisibleRejection() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A cena continua.","sceneTitle":"Trilha","sceneDescription":"Escura.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponents":[{"opponentId":"wolf-alpha","narrative":{"name":"Lobo","appearance":"Grande.","behavior":"Rosna.","intent":"Ataca.","context":"Na trilha."},"stats":{"str":5,"dex":7,"wil":3,"hp":4,"maxHp":4,"armor":1},"weapon":{"id":"fangs","damage":"d20","blast":false,"ranged":false}}]}},"suggestedActions":[]}"""
        )

        assertEquals("A cena continua.", response.narration)
        assertEquals("BEGIN_COMBAT", response.ruleRequest?.type)
        assertNull(response.ruleRequest?.encounter)
    }

    @Test
    fun fractionalOpponentStatsAreRetainedForVisibleRejection() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A cena continua.","sceneTitle":"Trilha","sceneDescription":"Escura.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponents":[{"opponentId":"cultist-a","narrative":{"name":"Cultista","appearance":"Manto escuro.","behavior":"Observa.","intent":"Protege o altar.","context":"Na capela."},"stats":{"str":5,"dex":7,"wil":8,"hp":1.00000000000000001,"maxHp":2.00000000000000001,"armor":1},"weapon":{"id":"ritual-dagger","damage":"d4","blast":false,"ranged":false}}]}},"suggestedActions":[]}"""
        )

        assertEquals("A cena continua.", response.narration)
        assertEquals("BEGIN_COMBAT", response.ruleRequest?.type)
        assertNull(response.ruleRequest?.encounter)
    }

    @Test
    fun parseSuggestedActionsFiltersBlankExcessAndNonStringEntries() {
        val overlongSuggestion = "x".repeat(161)
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{
              "narration":"A narração permanece.",
              "sceneTitle":"Taverna",
              "sceneDescription":"Uma pista conhecida aguarda atenção.",
              "suggestedActions":[
                "Examine a pista já conhecida",
                "   ",
                42,
                "Pergunte ao taverneiro sobre a pista",
                {"not":"a string"},
                "$overlongSuggestion",
                "Consulte o registro disponível",
                "Quarta ação"
              ]
            }"""
        )

        assertEquals(
            listOf(
                "Examine a pista já conhecida",
                "Pergunte ao taverneiro sobre a pista",
                "Consulte o registro disponível"
            ),
            response.suggestedActions
        )
        assertTrue(response.suggestedActions.size <= 3)
        assertTrue(response.suggestedActions.all { it.isNotBlank() && it.length <= 160 })
    }

    @Test
    fun parseSuggestedActionsEnforces160CharacterBoundary() {
        val atLimit = "a".repeat(160)
        val overLimit = "b".repeat(161)
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A narração permanece.","sceneTitle":"Taverna","sceneDescription":"Uma pista conhecida aguarda atenção.","suggestedActions":["$atLimit","$overLimit"]}"""
        )

        assertEquals(listOf(atLimit), response.suggestedActions)
        assertEquals("A narração permanece.", response.narration)
    }

    @Test
    fun emptySuggestedActionsDoNotDropNarration() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A narração permanece.","sceneTitle":"Taverna","sceneDescription":"Uma pista conhecida aguarda atenção.","suggestedActions":[]}"""
        )

        assertEquals("A narração permanece.", response.narration)
        assertTrue(response.suggestedActions.isEmpty())
    }

    @Test
    fun malformedSuggestionsDoNotDropNarration() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A narração permanece.","sceneTitle":"Taverna","sceneDescription":"Uma pista conhecida aguarda atenção.","suggestedActions":{"unexpected":"object"}}"""
        )

        assertEquals("A narração permanece.", response.narration)
        assertTrue(response.suggestedActions.isEmpty())
    }

    @Test
    fun missingSuggestedActionsDoNotDropNarrationOrScene() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A narração permanece.","sceneTitle":"Taverna","sceneDescription":"Uma pista conhecida aguarda atenção."}"""
        )

        assertEquals("A narração permanece.", response.narration)
        assertEquals("Taverna", response.sceneTitle)
        assertTrue(response.suggestedActions.isEmpty())
    }

    @Test
    fun nullSuggestedActionsDoNotDropNarrationOrScene() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A narração permanece.","sceneTitle":"Taverna","sceneDescription":"Uma pista conhecida aguarda atenção.","suggestedActions":null}"""
        )

        assertEquals("A narração permanece.", response.narration)
        assertEquals("Taverna", response.sceneTitle)
        assertTrue(response.suggestedActions.isEmpty())
    }

    @Test
    fun authorizedRuleResultIsSerializedSeparatelyFromPlayerIntent() {
        val payload = guardianRequestPayload(
            newCharacter("Mara", 10, 11, 12),
            playerIntent = "CONTINUAR_NARRATIVA",
            ruleResult = "Ataque: 4 bruto; 1 absorvido; 3 HP aplicados.",
            encounterContext = listOf(
                GuardianNarrativeOpponentContext(
                    "cultist-a",
                    CombatOpponentNarrative(
                        "Cultista da lamparina", "Manto cinza.", "Observa a trilha.", "Protege o altar.", "À esquerda."
                    )
                ),
                GuardianNarrativeOpponentContext(
                    "cultist-b",
                    CombatOpponentNarrative(
                        "Cultista do sino", "Capuz vermelho.", "Sussurra um cântico.", "Guarda a saída.", "À direita."
                    )
                )
            )
        )

        val encounterContexts = payload.getJSONArray("encounterContext")
        assertEquals("CONTINUAR_NARRATIVA", payload.getString("playerIntent"))
        assertEquals("Ataque: 4 bruto; 1 absorvido; 3 HP aplicados.", payload.getString("ruleResult"))
        assertEquals(2, encounterContexts.length())
        assertEquals("cultist-a", encounterContexts.getJSONObject(0).getString("opponentId"))
        assertEquals("Cultista da lamparina", encounterContexts.getJSONObject(0)
            .getJSONObject("narrative").getString("name"))
        assertEquals("cultist-b", encounterContexts.getJSONObject(1).getString("opponentId"))
        assertEquals("Cultista do sino", encounterContexts.getJSONObject(1)
            .getJSONObject("narrative").getString("name"))
        assertEquals("Mara", payload.getJSONObject("campaign").getJSONObject("character").getString("name"))
        assertFalse(payload.getString("playerIntent").contains("Ataque:"))
    }

    @Test
    fun campaignOpeningUsesInternalMarkerAndFreshCampaignContext() {
        val initial = newCharacter("Mara", 10, 11, 12)
        val state = initial.copy(
            campaign = initial.campaign.copy(campaignSeed = "opening-seed")
        )
        val payload = guardianRequestPayload(state, playerIntent = "INICIAR_CAMPANHA")
        val campaign = payload.getJSONObject("campaign")

        assertEquals("INICIAR_CAMPANHA", payload.getString("playerIntent"))
        assertEquals("opening-seed", campaign.getString("campaignSeed"))
        assertNull(state.campaign.guardianInteractionId)
        assertEquals(0, campaign.getJSONArray("recentHistory").length())
        assertFalse(campaign.has("guardianInteractionId"))
    }
}
