package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.RolledCharacter
import com.vanish994.cairnsolo.rules.RulesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldIntegrationTest {
    private fun rolled() = RolledCharacter(10, 10, 10, 6, null, null, age = 30)

    @Test
    fun creatingCharacterGeneratesWorldOnceForNewCampaign() {
        val random = FixedRandomSource(d20Value = 10, d6Value = 3)
        val resolver = GameActionResolver(ExplorationEngine(random), RulesEngine(random))
        val result = resolver.resolve(
            newCharacter("Mara", 10, 10, 10),
            GameAction.CreateCharacter("Mara", rolled())
        )
        val world = assertNotNull(result.state.campaign.worldState)
        assertEquals("Mara", world.campaignName)
        assertEquals(world.settlements.first().id, world.currentLocationId)
        assertTrue(world.factions.size in 2..5)
        assertTrue(result.state.campaign.campaignSeed.isNotBlank())
    }

    @Test
    fun creatingCharacterStartsWithGeneratedWorldOpening() {
        val random = FixedRandomSource(d20Value = 10, d6Value = 3)
        val resolver = GameActionResolver(ExplorationEngine(random), RulesEngine(random))
        val result = resolver.resolve(GameAction.CreateCharacter("Mara", rolled()))
        val campaign = result.state.campaign
        val world = assertNotNull(campaign.worldState)

        assertEquals(world.currentLocationId, campaign.sceneId)
        assertEquals(world.settlements.first().name, campaign.sceneTitle)
        assertTrue(campaign.guardianMessage.contains(world.region.name))
        assertTrue(campaign.guardianMessage.contains(world.factions.first().name))
        assertTrue(campaign.guardianMessage != DEFAULT_GUARDIAN_PROLOGUE)
    }

    @Test
    fun worldStateRoundTripPreservesGeneratedWorld() {
        val world = WorldGenerator(FixedRandomSource(d20Value = 12, d6Value = 4)).generate(
            WorldSeed("Cinzas", "uma fronteira assombrada")
        )
        val original = newCharacter("Mara", 10, 10, 10).copy(
            campaign = newCharacter("Mara", 10, 10, 10).campaign.copy(worldState = world)
        )
        val restored = assertNotNull(GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(original)))
        assertEquals(world, restored.campaign.worldState)
    }

    @Test
    fun campaignSeedRoundTripPreservesNarrativeIdentity() {
        val original = newCharacter("Mara", 10, 10, 10)
        val restored = assertNotNull(GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(original)))
        assertEquals(original.campaign.campaignSeed, restored.campaign.campaignSeed)
    }

    @Test
    fun guardianContextReceivesWorldState() {
        val world = WorldGenerator(FixedRandomSource(d20Value = 1, d6Value = 2)).generate(WorldSeed("Vald", "a frontier"))
        val state = newCharacter("Mara", 10, 10, 10).copy(
            campaign = newCharacter("Mara", 10, 10, 10).campaign.copy(worldState = world)
        )
        assertEquals(world, MJContext.from(state).world)
    }
}
