package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesEngineTest {

    private fun state(
        hp: Int = 6,
        str: Int = 10,
        armor: Int = 0,
        inventory: List<InventoryItem> = emptyList(),
        fatigue: Int = 0,
        deprived: Boolean = false
    ) = CharacterState(
        str = str,
        dex = 10,
        wil = 10,
        hp = hp,
        maxHp = 6,
        armor = armor,
        inventory = inventory,
        fatigue = fatigue,
        deprived = deprived
    )

    @Test
    fun saveSucceedsWhenRollIsAtOrBelowAttribute() {
        val result = RulesEngine(FixedRandomSource(12)).save(state(str = 12), Attribute.STR)
        assertTrue(result.success)
        assertEquals(12, result.roll)
    }

    @Test
    fun saveOneAlwaysSucceeds() {
        assertTrue(RulesEngine(FixedRandomSource(1)).save(state(str = 1), Attribute.DEX).success)
    }

    @Test
    fun saveTwentyAlwaysFails() {
        assertFalse(RulesEngine(FixedRandomSource(20)).save(state(), Attribute.WIL).success)
    }

    @Test
    fun damageIsReducedByArmor() {
        val result = RulesEngine(FixedRandomSource(1)).applyDamage(state(hp = 6, armor = 2), 5)
        assertEquals(3, result.newState.hp)
        assertEquals(10, result.newState.str)
        assertFalse(result.newState.critical)
    }

    @Test
    fun criticalDamageSubtractsExcessFromStrAndResolvesSave() {
        val result = RulesEngine(FixedRandomSource(1)).applyDamage(state(hp = 2, str = 10), 5)
        assertEquals(0, result.newState.hp)
        assertEquals(7, result.newState.str)
        assertTrue(result.newState.critical)
        val event = result.events.filterIsInstance<RuleEvent.CriticalDamage>().single()
        assertEquals(3, event.excessDamage)
        assertTrue(event.saveSuccess)
    }

    @Test
    fun exactZeroHpCreatesScarResult() {
        val result = RulesEngine(FixedRandomSource(1)).applyDamage(state(hp = 2), 2)
        assertEquals(Scar.RATTLING, result.newState.scar)
        assertEquals(Scar.RATTLING, (result.events.last() as RuleEvent.ScarTriggered).scar)
    }

    @Test
    fun fillingAllInventorySlotsDropsHpToZero() {
        val full = state(inventory = List(9) { InventoryItem("item-$it") })
        val result = RulesEngine(FixedRandomSource(1)).addItem(full, InventoryItem("last"))
        assertEquals(0, result.newState.hp)
        assertEquals(10, result.newState.usedSlots)
    }

    @Test
    fun criticalFailureMarksCharacterDead() {
        val result = RulesEngine(FixedRandomSource(20)).applyDamage(state(hp = 1, str = 1), 2)
        assertTrue(result.newState.dead)
        assertTrue(result.newState.critical)
    }

    @Test
    fun criticalSuccessKeepsCharacterAlive() {
        val result = RulesEngine(FixedRandomSource(1)).applyDamage(state(hp = 1, str = 5), 2)
        assertFalse(result.newState.dead)
        assertTrue(result.newState.critical)
    }

    @Test
    fun armorCannotExceedThree() {
        val result = RulesEngine(FixedRandomSource(1)).setArmor(state(), 3)
        assertEquals(3, result.newState.armor)
    }

    @Test
    fun armorAboveThreeIsRejected() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            RulesEngine(FixedRandomSource(1)).setArmor(state(), 4)
        }
    }

    @Test
    fun inventoryUsesPettyDefaultAndBulkySlots() {
        val engine = RulesEngine(FixedRandomSource(1))
        var result = engine.addItem(state(), InventoryItem("coin", petty = true))
        result = engine.addItem(result.newState, InventoryItem("rope"))
        result = engine.addItem(result.newState, InventoryItem("pack", slots = 2))

        assertEquals(3, result.newState.usedSlots)
        assertEquals(7, result.newState.freeSlots)
    }

    @Test
    fun inventoryCannotExceedTenSlots() {
        val engine = RulesEngine(FixedRandomSource(1))
        val full = state(inventory = List(10) { InventoryItem("item-$it") })

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            engine.addItem(full, InventoryItem("extra"))
        }
    }

    @Test
    fun fatigueConsumesInventorySlots() {
        val result = RulesEngine(FixedRandomSource(1)).addFatigue(state(), 2)
        assertEquals(2, result.newState.fatigue)
        assertEquals(2, result.newState.usedSlots)
    }

    @Test
    fun safeRestRecoversHpAndFatigueWhenNotDeprived() {
        val result = RulesEngine(FixedRandomSource(1))
            .safeRest(state(hp = 2, fatigue = 2))

        assertEquals(6, result.newState.hp)
        assertEquals(0, result.newState.fatigue)
        assertFalse(result.newState.critical)
    }

    @Test
    fun deprivedCharacterCannotRecoverFromSafeRest() {
        val result = RulesEngine(FixedRandomSource(1))
            .safeRest(state(hp = 2, fatigue = 2, deprived = true))

        assertEquals(2, result.newState.hp)
        assertEquals(2, result.newState.fatigue)
    }

    @Test
    fun removeItemFreesItsSlots() {
        val engine = RulesEngine(FixedRandomSource(1))
        val withItem = engine.addItem(state(), InventoryItem("bulky", slots = 2)).newState
        val result = engine.removeItem(withItem, "bulky")
        assertEquals(0, result.newState.usedSlots)
        assertEquals(10, result.newState.freeSlots)
    }
}
