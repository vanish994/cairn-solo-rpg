package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.WorldGenerator
import com.vanish994.cairnsolo.game.WorldSeed
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.FixedRandomSource
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardianContextTest {
    @Test
    fun contextContainsOnlyControlledSections() {
        val base = newCharacter("Mara", 10, 11, 12)
        val world = WorldGenerator(FixedRandomSource(d6Value = 3, d20Value = 10)).generate(WorldSeed("camp", "ruínas"))
        val json = GuardianContextBuilder.from(base.copy(campaign = base.campaign.copy(worldState = world))).toJson()

        assertTrue(json.has("character"))
        assertTrue(json.has("scene"))
        assertTrue(json.has("canon"))
        assertTrue(json.has("growth"))
        assertTrue(json.has("recentHistory"))
        assertTrue(json.has("world"))
        assertFalse(json.has("rules"))
        assertFalse(json.has("profile"))
        assertFalse(json.has("combatState"))
        assertFalse(json.has("history"))
        assertFalse(json.has("worldState"))
        assertFalse(json.has("campaign"))
    }

    @Test
    fun contextLimitsNarrativeCollectionsAndDoesNotExposeOpponentStats() {
        val base = newCharacter("Mara", 10, 11, 12)
        val history = (0 until 30).map { index ->
            com.vanish994.cairnsolo.game.CampaignHistoryEntry(
                "h-$index", index.toLong(), com.vanish994.cairnsolo.game.HistoryEventType.NARRATION,
                "evento $index", com.vanish994.cairnsolo.game.HistorySource.SYSTEM
            )
        }
        val state = base.copy(campaign = base.campaign.copy(history = history))
        val json = GuardianContextBuilder.from(state).toJson()
        assertEquals(12, json.getJSONArray("recentHistory").length())
        assertFalse(json.getJSONObject("character").has("opponent"))
        assertFalse(json.getJSONObject("character").has("rules"))
    }

    @Test
    fun contextSerializesControlledStatsAndConditions() {
        val base = newCharacter("Mara", 10, 11, 12).copy(
            campaign = newCharacter("Mara", 10, 11, 12).campaign.copy(
                rules = newCharacter("Mara", 10, 11, 12).campaign.rules.copy(deprived = true, fatigue = 2, critical = true)
            )
        )
        val character = GuardianContextBuilder.from(base).toJson().getJSONObject("character")
        val stats = character.getJSONObject("stats")
        assertEquals(10, stats.getInt("str"))
        assertTrue(character.getJSONArray("conditions").toString().contains("DEPRIVED"))
        assertTrue(character.getJSONArray("conditions").toString().contains("FATIGUE:2"))
        assertTrue(character.getJSONArray("conditions").toString().contains("CRITICAL"))
    }
}
