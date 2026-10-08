package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.game.CombatOpponentState
import com.vanish994.cairnsolo.game.CombatOpponentStatus
import com.vanish994.cairnsolo.game.CombatState
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.WeaponProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GuardianCombatContextTest {
    @Test
    fun guardianReceivesOpponentNarrativeAndOnlyRequiredMechanicalFacts() {
        val narrative = CombatOpponentNarrative(
            name = "Lobo cinzento",
            appearance = "Grande, com uma orelha rasgada.",
            behavior = "Ronda em círculos.",
            intent = "Protege a carcaça.",
            context = "Encontro na trilha ao anoitecer."
        )
        val base = newCharacter("Mara", 10, 11, 12)
        val state = base.copy(campaign = base.campaign.copy(
            combat = CombatState(
                opponents = listOf(CombatOpponentState(
                    id = "wolf-alpha",
                    narrative = narrative,
                    stats = CharacterState(str = 5, dex = 7, wil = 3, hp = 2, maxHp = 4, armor = 1),
                    weapon = WeaponProfile("fangs", "d6")
                )),
                round = 3,
                playerCanAct = false
            )
        ))

        val combat = GuardianContextBuilder.from(state).toJson()
            .getJSONObject("character")
            .getJSONObject("combat")
        val opponent = combat.getJSONArray("opponents").getJSONObject(0)
        val profile = opponent.getJSONObject("narrative")

        assertEquals("wolf-alpha", opponent.getString("id"))
        assertEquals(3, combat.getInt("round"))
        assertFalse(combat.getBoolean("playerCanAct"))
        assertEquals("Lobo cinzento", profile.getString("name"))
        assertEquals("Protege a carcaça.", profile.getString("intent"))
        assertEquals(2, opponent.getInt("hp"))
        assertEquals(4, opponent.getInt("maxHp"))
        assertEquals(1, opponent.getInt("armor"))
        assertEquals("fangs", opponent.getJSONObject("weapon").getString("id"))
        assertFalse(opponent.has("str"))
        assertFalse(opponent.has("dex"))
        assertFalse(opponent.has("wil"))
    }

    @Test
    fun guardianReceivesTwoDistinctOpponentsWithPublicFactsOnly() {
        val firstNarrative = CombatOpponentNarrative(
            name = "Cultista da lamparina",
            appearance = "Manto cinza.",
            behavior = "Protege a chama.",
            intent = "Avança com uma adaga.",
            context = "À esquerda da trilha."
        )
        val secondNarrative = CombatOpponentNarrative(
            name = "Cultista do sino",
            appearance = "Capuz vermelho.",
            behavior = "Mantém distância.",
            intent = "Aponta uma lança.",
            context = "À direita da trilha."
        )
        val base = newCharacter("Mara", 10, 11, 12)
        val state = base.copy(campaign = base.campaign.copy(
            combat = CombatState(
                opponents = listOf(
                    CombatOpponentState(
                        id = "cultist-a",
                        narrative = firstNarrative,
                        stats = CharacterState(str = 5, dex = 7, wil = 8, hp = 4, maxHp = 4, armor = 1),
                        weapon = WeaponProfile("ritual-dagger", "d4"),
                        status = CombatOpponentStatus.ACTIVE
                    ),
                    CombatOpponentState(
                        id = "cultist-b",
                        narrative = secondNarrative,
                        stats = CharacterState(str = 8, dex = 4, wil = 6, hp = 0, maxHp = 6, armor = 2),
                        weapon = WeaponProfile("rusted-spear", "d8", ranged = true),
                        status = CombatOpponentStatus.DEFEATED
                    )
                ),
                moraleLeaderId = "cultist-a",
                round = 2,
                playerCanAct = true
            )
        ))

        val combat = GuardianContextBuilder.from(state).toJson()
            .getJSONObject("character")
            .getJSONObject("combat")
        val opponents = combat.getJSONArray("opponents")
        assertEquals(2, opponents.length())
        val first = opponents.getJSONObject(0)
        val second = opponents.getJSONObject(1)

        assertEquals(listOf("cultist-a", "cultist-b"), listOf(first.getString("id"), second.getString("id")))
        assertEquals("ACTIVE", first.getString("status"))
        assertEquals("DEFEATED", second.getString("status"))
        assertEquals("Cultista da lamparina", first.getJSONObject("narrative").getString("name"))
        assertEquals("Cultista do sino", second.getJSONObject("narrative").getString("name"))
        assertEquals("À esquerda da trilha.", first.getJSONObject("narrative").getString("context"))
        assertEquals("À direita da trilha.", second.getJSONObject("narrative").getString("context"))
        assertEquals(4, first.getInt("hp"))
        assertEquals(1, first.getInt("armor"))
        assertEquals("ritual-dagger", first.getJSONObject("weapon").getString("id"))
        assertEquals("d4", first.getJSONObject("weapon").getString("damage"))
        assertEquals(0, second.getInt("hp"))
        assertEquals(6, second.getInt("maxHp"))
        assertEquals(2, second.getInt("armor"))
        assertEquals("rusted-spear", second.getJSONObject("weapon").getString("id"))
        assertEquals("d8", second.getJSONObject("weapon").getString("damage"))
        listOf(first, second).forEach { opponent ->
            assertFalse(opponent.has("str"))
            assertFalse(opponent.has("dex"))
            assertFalse(opponent.has("wil"))
        }
    }
}
