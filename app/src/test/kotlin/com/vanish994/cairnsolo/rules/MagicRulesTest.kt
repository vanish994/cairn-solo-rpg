package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MagicRulesTest {
    private fun state(vararg items: InventoryItem): CharacterState =
        CharacterState(10, 10, 10, 6, 6, 0, inventory = items.toList())

    @Test
    fun spellbookContainsOneSpellAndOccupiesOneSlot() {
        val book = MagicItems.spellbook("book-1", "detect-magic")
        assertEquals(1, book.slotCost)
        assertTrue("spellbook" in book.tags)
        assertTrue("spell:detect-magic" in book.tags)
        assertEquals("Detect Magic", SpellCatalog.find("detect-magic")?.name)
    }

    @Test
    fun spellbookCastingAddsFatigueAndKeepsBook() {
        val book = MagicItems.spellbook("book-1", "detect-magic")
        val result = MagicRules(RulesEngine(FixedRandomSource(1))).cast(state(book), "book-1")
        assertEquals(1, result.state.fatigue)
        assertEquals("book-1", result.state.inventory.single { it.id == "book-1" }.id)
        assertIs<MagicEvent.FatigueAdded>(result.events.first())
        assertIs<MagicEvent.SpellCast>(result.events.last())
    }

    @Test
    fun scrollIsPettyAndDisappearsWithoutFatigue() {
        val scroll = MagicItems.scroll("scroll-1", "read-mind")
        assertEquals(0, scroll.slotCost)
        val result = MagicRules(RulesEngine(FixedRandomSource(1))).cast(state(scroll), "scroll-1")
        assertTrue(result.state.inventory.isEmpty())
        assertEquals(0, result.state.fatigue)
        assertTrue(result.events.any { it is MagicEvent.ItemConsumed })
    }

    @Test
    fun relicConsumesOneUseWithoutFatigue() {
        val relic = MagicItems.relic("relic-1", "thicket", 2)
        val result = MagicRules(RulesEngine(FixedRandomSource(1))).cast(state(relic), "relic-1")
        assertEquals(1, result.state.inventory.single().uses)
        assertEquals(0, result.state.fatigue)
    }

    @Test
    fun dangerousCastingCanRequireWilSaveAndDestroySpellbookOnFailure() {
        val book = MagicItems.spellbook("book-1", "disassemble")
        val result = MagicRules(RulesEngine(FixedRandomSource(20))).cast(
            state(book), "book-1", requiresWilSave = true,
            failure = SpellFailureConsequence.DESTROY_SPELLBOOK
        )
        assertFalseBookGone(result.state)
        assertEquals(1, result.state.fatigue)
        assertTrue(result.events.any { it is MagicEvent.CastingSaveFailed })
        assertTrue(result.events.any { it is MagicEvent.SpellbookDestroyed })
    }

    @Test
    fun unknownOrNonMagicItemsAreRejected() {
        assertFailsWith<IllegalArgumentException> { MagicItems.spellbook("bad", "missing") }
        assertFailsWith<IllegalStateException> {
            MagicRules(RulesEngine(FixedRandomSource(1))).cast(state(InventoryItem("rock")), "rock")
        }
    }

    private fun assertFalseBookGone(state: CharacterState) {
        assertTrue(state.inventory.none { it.id == "book-1" })
    }
}
