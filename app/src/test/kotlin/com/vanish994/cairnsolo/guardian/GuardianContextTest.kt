package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CampaignHistoryEntry
import com.vanish994.cairnsolo.game.HistoryEventType
import com.vanish994.cairnsolo.game.HistorySource
import com.vanish994.cairnsolo.game.WorldGenerator
import com.vanish994.cairnsolo.game.WorldSeed
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.FixedRandomSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GuardianContextTest {
    @Test
    fun contextContainsOnlyControlledSections() {
        val base = newCharacter("Mara", 10, 11, 12)
        val world = WorldGenerator(FixedRandomSource(d6Value = 3, d20Value = 10)).generate(WorldSeed("camp", "ruínas"))
        val context = GuardianContextBuilder.from(base.copy(campaign = base.campaign.copy(worldState = world)))

        assertEquals("Mara", context.character.name)
        assertEquals("prologue", context.scene.id)
        assertNotNull(context.world)
        assertTrue(context.canon.locations.isEmpty())
        assertTrue(context.growth.evidence.isEmpty())
        assertTrue(context.recentHistory.isEmpty())
        assertFalse(context.character.combat != null)
    }

    @Test
    fun contextLimitsNarrativeCollectionsAndDoesNotExposeOpponentStats() {
        val base = newCharacter("Mara", 10, 11, 12)
        val history = (0 until 30).map { index ->
            CampaignHistoryEntry(
                "h-$index", index.toLong(), HistoryEventType.NARRATION,
                "evento $index", HistorySource.SYSTEM
            )
        }
        val state = base.copy(campaign = base.campaign.copy(history = history))
        val context = GuardianContextBuilder.from(state)

        assertEquals(12, context.recentHistory.size)
        assertTrue(context.character.combat == null)
        assertEquals(18L, context.recentHistory.first().turn)
        assertEquals(30L, context.recentHistory.last().turn)
    }

    @Test
    fun contextContainsControlledStatsAndConditions() {
        val original = newCharacter("Mara", 10, 11, 12)
        val state = original.copy(
            campaign = original.campaign.copy(
                rules = original.campaign.rules.copy(deprived = true, fatigue = 2, critical = true)
            )
        )
        val character = GuardianContextBuilder.from(state).character

        assertEquals(10, character.str)
        assertEquals(11, character.dex)
        assertEquals(12, character.wil)
        assertTrue("DEPRIVED" in character.conditions)
        assertTrue("FATIGUE:2" in character.conditions)
        assertTrue("CRITICAL" in character.conditions)
    }
}
