package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.newCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GuardianClientTest {
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
                }
              },
              "suggestedActions":[]
            }"""
        )

        assertEquals("Uma figura bloqueia o caminho.", response.narration)
        val encounter = assertNotNull(response.ruleRequest?.encounter)
        assertEquals("wolf-alpha", encounter.opponentId)
        assertEquals("Lobo cinzento", encounter.narrative.name)
        assertEquals("Protege a carcaça atrás de si.", encounter.narrative.intent)
        assertEquals(5, encounter.stats.str)
        assertEquals(4, encounter.stats.hp)
        assertEquals(1, encounter.stats.armor)
        assertEquals("d6", encounter.weapon.damage)
        assertFalse(encounter.weapon.ranged)
    }

    @Test
    fun invalidBeginCombatProposalDoesNotDiscardNarration() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A cena continua.","sceneTitle":"Trilha","sceneDescription":"Escura.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponentId":"wolf"}},"suggestedActions":[]}"""
        )

        assertEquals("A cena continua.", response.narration)
        assertNull(response.ruleRequest)
    }

    @Test
    fun unsupportedWeaponDamageDoesNotBecomeAnExecutableEncounter() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A cena continua.","sceneTitle":"Trilha","sceneDescription":"Escura.","ruleRequest":{"type":"BEGIN_COMBAT","encounter":{"opponentId":"wolf-alpha","narrative":{"name":"Lobo","appearance":"Grande.","behavior":"Rosna.","intent":"Ataca.","context":"Na trilha."},"stats":{"str":5,"dex":7,"wil":3,"hp":4,"maxHp":4,"armor":1},"weapon":{"id":"fangs","damage":"d20","blast":false,"ranged":false}}},"suggestedActions":[]}"""
        )

        assertEquals("A cena continua.", response.narration)
        assertNull(response.ruleRequest)
    }

    @Test
    fun authorizedRuleResultIsSerializedSeparatelyFromPlayerIntent() {
        val payload = guardianRequestPayload(
            newCharacter("Mara", 10, 11, 12),
            playerIntent = "CONTINUAR_NARRATIVA",
            ruleResult = "Ataque: 4 bruto; 1 absorvido; 3 HP aplicados.",
            encounterContext = com.vanish994.cairnsolo.game.CombatOpponentNarrative(
                "Lobo cinzento", "Grande.", "Ronda em círculos.", "Protege a carcaça.", "Na trilha."
            )
        )

        assertEquals("CONTINUAR_NARRATIVA", payload.getString("playerIntent"))
        assertEquals("Ataque: 4 bruto; 1 absorvido; 3 HP aplicados.", payload.getString("ruleResult"))
        assertEquals("Lobo cinzento", payload.getJSONObject("encounterContext").getString("name"))
        assertEquals("Mara", payload.getJSONObject("campaign").getJSONObject("character").getString("name"))
        assertFalse(payload.getString("playerIntent").contains("Ataque:"))
    }
}
