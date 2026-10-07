package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DungeonRulesTest {
    private val character = CharacterState(10, 10, 10, 3, 6, 0)
    private fun state(
        light: DungeonLight = DungeonLight.TORCH,
        safe: Boolean = true,
        danger: Boolean = false,
        cycles: Int = 0,
        oil: Int = 0
    ) = DungeonState("crypt", cyclesInLocation = cycles, light = light, safeLocation = safe, dangerPresent = danger, lanternOilUses = oil)

    @Test
    fun turnAdvancesAndEnteringNewAreaRequestsDungeonEvent() {
        val result = DungeonRules(FixedRandomSource(1)).resolveTurn(
            state(), character, DungeonAction(DungeonActionKind.MOVE, enteredNewArea = true)
        )
        assertEquals(1, result.state.turn)
        assertIs<DungeonEvent.EventRollRequired>(result.events[1])
        assertIs<DungeonEvent.Encounter>(result.events.last())
    }

    @Test
    fun dungeonEventTableMapsSixToQuietAndFiveToExhaustion() {
        val quiet = DungeonRules(FixedRandomSource(1, d6Value = 6)).resolveTurn(
            state(), character, DungeonAction(DungeonActionKind.MOVE, fastMovement = true)
        )
        assertIs<DungeonEvent.Quiet>(quiet.events.last())
        val exhaustion = DungeonRules(FixedRandomSource(1, d6Value = 5)).resolveTurn(
            state(), character, DungeonAction(DungeonActionKind.MOVE, loud = true)
        )
        assertIs<DungeonEvent.Exhaustion>(exhaustion.events.last())
    }

    @Test
    fun restRequiresLightSafeLocationAndNoDangerAndRestoresHpOnly() {
        val rules = DungeonRules(FixedRandomSource(1))
        val rested = rules.resolveTurn(state(), character.copy(fatigue = 2), DungeonAction(DungeonActionKind.REST))
        assertEquals(6, rested.character.hp)
        assertEquals(2, rested.character.fatigue)
        assertIs<DungeonEvent.HpRestored>(rested.events.last())

        val denied = rules.resolveTurn(state(safe = false), character, DungeonAction(DungeonActionKind.REST))
        assertIs<DungeonEvent.RestDenied>(denied.events.last())
        assertEquals(3, denied.character.hp)
    }

    @Test
    fun torchCanBeLitThreeTimesAndExtinguishedWithoutResettingDegradation() {
        val rules = DungeonRules(FixedRandomSource(1))
        var current = state(light = DungeonLight.DARK)
        current = rules.lightTorch(current).state
        assertEquals(2, current.torchIgnitionsRemaining)
        current = rules.extinguish(current).state
        current = rules.lightTorch(current).state
        current = rules.extinguish(current).state
        current = rules.lightTorch(current).state
        assertEquals(0, current.torchIgnitionsRemaining)
        assertFailsWith<IllegalArgumentException> { rules.extinguish(rules.lightTorch(rules.extinguish(current).state).state) }
    }

    @Test
    fun lanternConsumesOilAndCanBeRelitAfterExtinguishing() {
        val rules = DungeonRules(FixedRandomSource(1))
        val lit = rules.lightLantern(state(light = DungeonLight.DARK, oil = 2)).state
        assertEquals(1, lit.lanternOilUses)
        val relit = rules.lightLantern(rules.extinguish(lit).state).state
        assertEquals(0, relit.lanternOilUses)
    }

    @Test
    fun panicSetsZeroHpBlocksActionsAndCanBeOvercomeByWilSave() {
        val rules = DungeonRules(FixedRandomSource(1))
        val panicked = rules.startPanic(state(), character)
        assertTrue(panicked.state.panicked)
        assertEquals(0, panicked.character.hp)
        val blocked = rules.resolveTurn(panicked.state, panicked.character, DungeonAction(DungeonActionKind.SEARCH))
        assertIs<DungeonEvent.PanicBlockedAction>(blocked.events.single())
        val recovered = rules.overcomePanic(panicked.state, character)
        assertFalse(recovered.state.panicked)
    }
}
