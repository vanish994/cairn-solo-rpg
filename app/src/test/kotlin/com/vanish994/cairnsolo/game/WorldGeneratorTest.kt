package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.FixedRandomSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldGeneratorTest {
    private val seed = WorldSeed("As Cinzas de Vald", "uma fronteira assombrada")

    @Test
    fun generationIsDeterministicForSameSeedAndRandomSequence() {
        val first = WorldGenerator(FixedRandomSource(d20Value = 12, d6Value = 4)).generate(seed)
        val second = WorldGenerator(FixedRandomSource(d20Value = 12, d6Value = 4)).generate(seed)
        assertEquals(first, second)
    }

    @Test
    fun differentNarrativeSeedsCanSelectDifferentStartingSettlements() {
        val names = (1..8).map { index ->
            val seeded = seed.copy(narrativeSeed = "campaign-seed-$index")
            WorldGenerator(FixedRandomSource(d20Value = 12, d6Value = 4))
                .generate(seeded)
                .settlements
                .first()
                .name
        }

        assertTrue(names.toSet().size > 1, "Different campaign seeds should vary the starting settlement: $names")
    }

    @Test
    fun generatedWorldContainsPlayableStartingStructure() {
        val world = WorldGenerator(FixedRandomSource(d20Value = 1, d6Value = 3)).generate(seed)
        assertEquals("As Cinzas de Vald", world.campaignName)
        assertEquals(seed.startingPoint, world.currentLocationId)
        assertTrue(world.settlements.isNotEmpty())
        assertTrue(world.landmarks.isNotEmpty())
        assertTrue(world.dungeons.isNotEmpty())
        assertTrue(world.npcs.size >= 2)
        assertTrue(world.rumors.size >= 3)
        assertTrue(world.treasures.isNotEmpty())
    }

    @Test
    fun factionsHaveThreeToFiveProgressiveGoalsAndObstacles() {
        val world = WorldGenerator(FixedRandomSource(d20Value = 20, d6Value = 6)).generate(seed)
        assertTrue(world.factions.size in 2..5)
        assertTrue(world.factions.all { it.goals.size in 3..5 && it.obstacle.isNotBlank() })
        assertTrue(world.threats.size >= 2)
        assertTrue(world.threats.all { it.factionId != null })
    }

    @Test
    fun startingDungeonIsAttachedToStartingSettlement() {
        val world = WorldGenerator(FixedRandomSource(d20Value = 4, d6Value = 2)).generate(seed)
        val dungeon = world.dungeons.single()
        assertEquals(world.currentLocationId, dungeon.entranceLocationId)
        assertEquals(false, dungeon.discovered)
        assertTrue(world.treasures.single().locationId == dungeon.id)
    }
}
