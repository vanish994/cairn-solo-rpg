package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MarketplaceTest {
    private fun state(gold: Int = 0) = CharacterState(10, 10, 10, 6, 6, 0)

    @Test
    fun catalogContainsOfficialArmorWeaponsTransportAndHirelings() {
        assertEquals(1, MarketplaceCatalog.find("shield")?.item?.armor)
        assertEquals("d10", MarketplaceCatalog.find("halberd")?.item?.damage)
        assertEquals(2, MarketplaceCatalog.find("chainmail")?.item?.slots)
        assertEquals("4 slots", MarketplaceCatalog.find("horse")?.description)
        assertNotNull(MarketplaceCatalog.find("hireling-scholar"))
    }

    @Test
    fun purchaseDeductsGoldAndAddsGearToInventory() {
        val result = MarketplaceRules().purchase(state(), 10, "sling")
        assertEquals(5, result.goldRemaining)
        assertEquals("sling", result.state.inventory.single().id)
        assertEquals("d6", result.state.inventory.single().damage)
    }

    @Test
    fun purchaseRejectsInsufficientGoldAndUnknownEntries() {
        assertFailsWith<IllegalArgumentException> { MarketplaceRules().purchase(state(), 4, "sling") }
        assertFailsWith<IllegalStateException> { MarketplaceRules().purchase(state(), 100, "not-real") }
    }

    @Test
    fun bulkyMarketplaceItemConsumesTwoSlots() {
        val result = MarketplaceRules().purchase(state(), 40, "chainmail")
        assertEquals(2, result.state.usedSlots)
    }

    @Test
    fun creditGoldAddsGpAndRejectsOverflow() {
        val rules = MarketplaceRules()

        assertEquals(12, rules.creditGold(0, 12))
        assertEquals(Int.MAX_VALUE, rules.creditGold(Int.MAX_VALUE - 12, 12))
        assertFailsWith<IllegalArgumentException> { rules.creditGold(-1, 1) }
        assertFailsWith<IllegalArgumentException> { rules.creditGold(0, 0) }
        assertFailsWith<IllegalArgumentException> { rules.creditGold(0, -1) }
        assertFailsWith<ArithmeticException> { rules.creditGold(Int.MAX_VALUE, 1) }
    }
}
