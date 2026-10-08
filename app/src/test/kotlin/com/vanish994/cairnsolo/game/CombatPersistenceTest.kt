package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.WeaponProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private fun multiOpponentCombatState() = CombatState(
        opponents = listOf(
            CombatOpponentState(
                id = "wolf-alpha",
                narrative = narrative,
                stats = CharacterState(
                    str = 3, dex = 8, wil = 4, hp = 0, maxHp = 6, armor = 2,
                    maxStr = 5, maxDex = 9, maxWil = 6
                ),
                weapon = WeaponProfile("fangs", "d8", blast = true, ranged = true),
                status = CombatOpponentStatus.DEFEATED
            ),
            CombatOpponentState(
                id = "wolf-beta",
                narrative = CombatOpponentNarrative(
                    name = "Lobo de pelo branco",
                    appearance = "Uma cicatriz cruza o focinho.",
                    behavior = "Avança sem hesitar.",
                    intent = "Defende o líder.",
                    context = "Vindo da encosta norte."
                ),
                stats = CharacterState(
                    str = 7, dex = 4, wil = 6, hp = 3, maxHp = 5, armor = 1,
                    maxStr = 8, maxDex = 6, maxWil = 7
                ),
                weapon = WeaponProfile("claws", "d6", blast = false, ranged = false),
                status = CombatOpponentStatus.ACTIVE
            )
        ),
        moraleLeaderId = "wolf-beta",
        resolvedMoraleTriggers = setOf(CombatMoraleTrigger.FIRST_CASUALTY),
        round = 4,
        playerCanAct = false
    )

    @Test
    fun multipleOpponentsRoundTrip() {
        val base = newCharacter("Mara", 10, 11, 12)
        val originalCombat = multiOpponentCombatState()
        val original = base.copy(campaign = base.campaign.copy(combat = originalCombat))

        val restored = assertNotNull(GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(original)))

        assertEquals(originalCombat, restored.campaign.combat)
    }

    @Test
    fun legacySingleOpponentSaveLoadsAsSingleEntry() {
        val base = newCharacter("Mara", 10, 11, 12)
        val originalCombat = combatState()
        val original = base.copy(campaign = base.campaign.copy(combat = originalCombat))
        val legacy = GameStatePersistenceCodec.encode(original).toMutableMap().apply {
            remove("combatOpponentCount")
            keys.filter { it.startsWith("combatOpponent_") }.toList().forEach { remove(it) }
            remove("combatMoraleLeaderId")
            remove("combatResolvedMoraleTriggers")
        }

        val restored = assertNotNull(GameStatePersistenceCodec.decode(legacy))
        val restoredCombat = assertNotNull(restored.campaign.combat)

        assertEquals(1, restoredCombat.opponents.size)
        val originalOpponent = originalCombat.opponents.single()
        val restoredOpponent = restoredCombat.opponents.single()
        assertEquals(originalOpponent.id, restoredOpponent.id)
        assertEquals(originalOpponent.weapon, restoredOpponent.weapon)
        assertEquals(originalOpponent.narrative, restoredOpponent.narrative)
        assertEquals(originalOpponent.stats, restoredOpponent.stats)
        assertEquals(originalCombat.round, restoredCombat.round)
        assertEquals(originalCombat.playerCanAct, restoredCombat.playerCanAct)
    }

    @Test
    fun corruptOpponentWeaponDoesNotDiscardWholeCampaign() {
        val base = newCharacter("Mara", 10, 11, 12)
        val originalCombat = multiOpponentCombatState()
        val original = base.copy(campaign = base.campaign.copy(combat = originalCombat))
        val corrupted = GameStatePersistenceCodec.encode(original).toMutableMap().apply {
            put("combatOpponent_0_weaponDamage", "4 STR")
        }

        val restored = assertNotNull(GameStatePersistenceCodec.decode(corrupted))
        val restoredCombat = assertNotNull(restored.campaign.combat)

        assertEquals(2, restoredCombat.opponents.size)
        assertEquals("d4", restoredCombat.opponents[0].weapon.damage)
        assertEquals(originalCombat.opponents[1], restoredCombat.opponents[1])
    }

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

    @Test
    fun invalidPersistedOpponentDamageFallsBackToD4() {
        val base = newCharacter("Mara", 10, 11, 12)
        val original = base.copy(campaign = base.campaign.copy(combat = combatState()))
        val corrupted = GameStatePersistenceCodec.encode(original).toMutableMap().apply {
            put("combatWeaponDamage", "4 STR")
        }

        val restored = assertNotNull(GameStatePersistenceCodec.decode(corrupted))

        assertEquals("d4", restored.campaign.combat?.opponentWeapon?.damage)
    }

    @Test
    fun combatStateRejectsUnsupportedOpponentDamage() {
        assertFailsWith<IllegalArgumentException> {
            CombatState(
                opponentId = "wolf-alpha",
                opponent = CharacterState(str = 5, dex = 7, wil = 3, hp = 2, maxHp = 4, armor = 1),
                opponentWeapon = WeaponProfile("fangs", "4 STR"),
                opponentNarrative = CombatOpponentNarrative("Lobo")
            )
        }
    }
}
