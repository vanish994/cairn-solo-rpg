package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RulesEngineTest {

    private fun state(
        hp: Int = 6,
        maxHp: Int = 6,
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
        maxHp = maxHp,
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
    fun exactZeroHpUsesHpLostForScarResult() {
        val result = RulesEngine(FixedRandomSource(1, 1, 12)).applyDamage(state(hp = 2), 2)
        assertEquals(Scar.RATTLING, result.newState.scar)
        assertEquals(Scar.RATTLING, (result.events.last() as RuleEvent.ScarTriggered).scar)
        assertEquals(2, (result.events.last() as RuleEvent.ScarTriggered).roll)
    }

    @Test
    fun lastingScarRecordsLocationAndMayIncreaseMaxHp() {
        val result = RulesEngine(FixedRandomSource(10, 2)).applyDamage(state(hp = 1, maxHp = 1), 1)
        assertEquals(Scar.LASTING, result.newState.scar)
        assertEquals("hands", result.newState.lastingScar)
        assertEquals(2, result.newState.maxHp)
    }

    @Test
    fun brokenLimbStoresLocationUntilRecovery() {
        val engine = RulesEngine(FixedRandomSource(10, 4))
        val damaged = engine.applyDamage(state(hp = 4, maxHp = 4), 4)
        assertEquals(Scar.BROKEN_LIMB, damaged.newState.scar)
        assertEquals("arm", damaged.newState.brokenLimb)
        assertEquals(ScarRecovery.BROKEN_LIMB, damaged.newState.scarRecovery)
        val recovered = engine.recoverScar(damaged.newState)
        assertEquals(8, recovered.newState.hp)
        assertEquals(8, recovered.newState.maxHp)
        assertEquals(null, recovered.newState.brokenLimb)
        assertEquals(null, recovered.newState.scarRecovery)
    }

    @Test
    fun headWoundImprovesSelectedAttributeWithoutBreakingMaximumInvariant() {
        val result = RulesEngine(FixedRandomSource(10, 6)).applyDamage(
            state(hp = 6, maxHp = 6, str = 1), 6
        )
        assertEquals(Scar.HEAD_WOUND, result.newState.scar)
        assertEquals(18, result.newState.wil)
        assertEquals(18, result.newState.maxWil)
        assertEquals(Attribute.WIL, result.newState.scarAttribute)
    }

    @Test
    fun deafenedAndSunderedResolveWillSaves() {
        val deafened = RulesEngine(FixedRandomSource(10, 1, d12Value = 12, d4Value = 3))
            .applyDamage(state(hp = 8, maxHp = 8), 8)
        assertEquals(Scar.DEAFENED, deafened.newState.scar)
        assertTrue(deafened.newState.deafened)
        assertEquals(13, deafened.newState.maxWil)

        val sundered = RulesEngine(FixedRandomSource(10, 1))
            .applyDamage(state(hp = 10, maxHp = 10), 10)
        assertEquals(Scar.SUNDERED, sundered.newState.scar)
        assertTrue(sundered.newState.sundered)
        assertEquals(11, sundered.newState.maxWil)
    }

    @Test
    fun mortalWoundRequiresScarRecovery() {
        val engine = RulesEngine(FixedRandomSource(10, 2))
        val damaged = engine.applyDamage(state(hp = 11, maxHp = 11), 11)
        assertEquals(Scar.MORTAL_WOUND, damaged.newState.scar)
        assertTrue(damaged.newState.deprived)
        assertTrue(damaged.newState.critical)
        assertEquals(ScarRecovery.MORTAL_WOUND, damaged.newState.scarRecovery)
        val recovered = engine.recoverScar(damaged.newState)
        assertEquals(4, recovered.newState.hp)
        assertEquals(4, recovered.newState.maxHp)
        assertFalse(recovered.newState.critical)
        assertFalse(recovered.newState.deprived)
    }

    @Test
    fun doomedResolvesOnNextCriticalSave() {
        val survived = RulesEngine(FixedRandomSource(1, 2)).applyDamage(
            state(hp = 1, maxHp = 12).copy(doomed = true), 2
        )
        assertFalse(survived.newState.dead)
        assertFalse(survived.newState.doomed)
        assertEquals(12, survived.newState.maxHp)

        val fatal = RulesEngine(FixedRandomSource(20, 2)).applyDamage(
            state(hp = 1, maxHp = 12).copy(doomed = true, str = 5), 2
        )
        assertTrue(fatal.newState.dead)
        assertTrue(fatal.newState.doomed)
    }

    @Test
    fun scarImmediateEffectsAreAppliedToAuthoritativeState() {
        val walloped = RulesEngine(FixedRandomSource(1)).applyDamage(state(hp = 3), 3)
        assertEquals(Scar.WALLOPED, walloped.newState.scar)
        assertTrue(walloped.newState.deprived)

        val hamstrung = RulesEngine(FixedRandomSource(1)).applyDamage(state(hp = 7, maxHp = 7), 7)
        assertEquals(Scar.HAMSTRUNG, hamstrung.newState.scar)
        assertTrue(hamstrung.newState.hamstrung)
    }

    @Test
    fun fillingAllInventorySlotsReducesHpToZero() {
        val full = state(hp = 6, inventory = List(9) { InventoryItem("item-$it") })
        val result = RulesEngine(FixedRandomSource(1)).addItem(full, InventoryItem("last"))
        assertEquals(0, result.newState.hp)
        assertEquals(10, result.newState.usedSlots)
        assertEquals(1, result.events.size)
        assertIs<RuleEvent.InventoryChanged>(result.events.single())
    }

    @Test
    fun criticalFailureMarksCharacterDead() {
        val result = RulesEngine(FixedRandomSource(20)).applyDamage(state(hp = 1, str = 1), 2)
        assertTrue(result.newState.dead)
        assertTrue(result.newState.critical)
    }

    @Test
    fun failedCriticalSaveLeavesCharacterCriticalButNotDead() {
        val result = RulesEngine(FixedRandomSource(20)).applyDamage(state(hp = 1, str = 5), 2)
        assertFalse(result.newState.dead)
        assertTrue(result.newState.critical)
        assertEquals(4, result.newState.str)
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
