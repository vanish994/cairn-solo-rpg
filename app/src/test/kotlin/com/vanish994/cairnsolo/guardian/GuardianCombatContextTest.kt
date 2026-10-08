package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CombatOpponentNarrative
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
                opponentId = "wolf-alpha",
                opponent = CharacterState(str = 5, dex = 7, wil = 3, hp = 2, maxHp = 4, armor = 1),
                opponentWeapon = WeaponProfile("fangs", "d6"),
                round = 3,
                playerCanAct = false,
                opponentNarrative = narrative
            )
        ))

        val combat = GuardianContextBuilder.from(state).toJson()
            .getJSONObject("character")
            .getJSONObject("combat")
        val opponent = combat.getJSONObject("opponent")
        val profile = opponent.getJSONObject("narrative")

        assertEquals("wolf-alpha", combat.getString("opponentId"))
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
}
