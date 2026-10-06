package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesEngineTest {

    @Test
    fun saveSucceedsWhenRollIsAtOrBelowAttribute() {
        val rng = FixedRandomSource(12)

        val result = RulesEngine(rng).save(Attribute.STR, 12)

        assertTrue(result.success)
        assertEquals(12, result.roll)
    }

    @Test
    fun saveOneAlwaysSucceeds() {
        val rng = FixedRandomSource(20)

        val result = RulesEngine(rng).save(Attribute.DEX, 1, forcedRoll = 1)

        assertTrue(result.success)
    }

    @Test
    fun saveTwentyAlwaysFails() {
        val result = RulesEngine(FixedRandomSource(20)).save(Attribute.WIL, 20)

        assertFalse(result.success)
    }

    @Test
    fun damageIsReducedByArmor() {
        val character = CharacterState(
            str = 10,
            dex = 10,
            wil = 10,
            hp = 6,
            maxHp = 6,
            armor = 2
        )

        val result = RulesEngine(FixedRandomSource(1)).applyDamage(character, 5)

        assertEquals(3, result.newState.hp)
        assertEquals(10, result.newState.str)
    }

    @Test
    fun damageBelowZeroBecomesCriticalDamageUsingExcess() {
        val character = CharacterState(
            str = 10,
            dex = 10,
            wil = 10,
            hp = 2,
            maxHp = 6,
            armor = 0
        )

        val result = RulesEngine(FixedRandomSource(1)).applyDamage(character, 5)

        assertEquals(0, result.newState.hp)
        assertEquals(7, result.newState.str)
        assertTrue(result.events.any { it is RuleEvent.CriticalDamage })
    }

    @Test
    fun armorCannotExceedThree() {
        val character = CharacterState(
            str = 10,
            dex = 10,
            wil = 10,
            hp = 6,
            maxHp = 6,
            armor = 3
        )

        val result = RulesEngine(FixedRandomSource(1)).setArmor(character, 8)

        assertEquals(3, result.newState.armor)
    }
}
