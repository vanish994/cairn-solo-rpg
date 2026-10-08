package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.WeaponProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CombatPersistenceTest {
    private val narrative = CombatOpponentNarrative(
        name = "Lobo cinzento",
        appearance = "Grande, com uma orelha rasgada.",
        behavior = "Ronda em círculos.",
        intent = "Protege a carcaça.",
        context = "Encontro na trilha ao anoitecer."
    )

    private fun combatState() = CombatState(
        opponentId = "wolf-alpha",
        opponent = CharacterState(str = 5, dex = 7, wil = 3, hp = 2, maxHp = 4, armor = 1),
        opponentWeapon = WeaponProfile("fangs", "d6", ranged = true),
        round = 3,
        playerCanAct = false,
        opponentNarrative = narrative
    )

    @Test
    fun activeCombatRoundTripsOpponentNarrativeAndMechanicalState() {
        val base = newCharacter("Mara", 10, 11, 12)
        val original = base.copy(campaign = base.campaign.copy(combat = combatState()))

        val restored = assertNotNull(GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(original)))

        assertEquals(combatState(), restored.campaign.combat)
    }

    @Test
    fun legacySaveWithoutOpponentNarrativeUsesSafeDefault() {
        val base = newCharacter("Mara", 10, 11, 12)
        val original = base.copy(campaign = base.campaign.copy(combat = combatState()))
        val legacy = GameStatePersistenceCodec.encode(original).toMutableMap().apply {
            remove("combatNarrativeName")
            remove("combatNarrativeAppearance")
            remove("combatNarrativeBehavior")
            remove("combatNarrativeIntent")
            remove("combatNarrativeContext")
            remove("combatWeaponBlast")
            remove("combatWeaponRanged")
        }

        val restored = assertNotNull(GameStatePersistenceCodec.decode(legacy))

        assertEquals(CombatOpponentNarrative("wolf-alpha"), restored.campaign.combat?.opponentNarrative)
        assertEquals(false, restored.campaign.combat?.opponentWeapon?.ranged)
    }
}
