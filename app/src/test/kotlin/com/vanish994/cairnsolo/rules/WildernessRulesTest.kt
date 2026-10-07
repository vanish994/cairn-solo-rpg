package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WildernessRulesTest {
    private val character = CharacterState(10, 10, 10, 2, 6, 0, fatigue = 2)
    private fun state(
        path: PathType = PathType.ROAD,
        distance: TravelDistance = TravelDistance.SHORT,
        terrain: Terrain = Terrain.EASY,
        weather: Weather = Weather.NICE,
        night: Boolean = false,
        rations: Int = 0
    ) = WildernessState("village", path = path, distance = distance, terrain = terrain, weather = weather, nightTravel = night, rations = rations)

    @Test
    fun travelPlanCombinesPathDistanceTerrainWeatherAndNightPenalties() {
        val rules = WildernessRules(FixedRandomSource(1))
        val plan = rules.planTravel(state(PathType.WILDERNESS, TravelDistance.LONG, Terrain.PERILOUS, Weather.INCLEMENT, true), "ruins")
        assertEquals(9, plan.watches)
        assertEquals(3, plan.lostOdds)
    }

    @Test
    fun travelProgressesOneWatchAndArrivesAtDestination() {
        val rules = WildernessRules(FixedRandomSource(6, d6Value = 6))
        val started = rules.startTravel(state(), "ruins")
        val result = rules.act(started, character, WildernessAction.TRAVEL)
        assertEquals("ruins", result.state.currentPoint)
        assertEquals(null, result.state.destinationPoint)
        assertIs<WildernessEvent.Arrived>(result.events.last())
    }

    @Test
    fun trailCanLoseThePartyAndRecoverWayAsAnAction() {
        val rules = WildernessRules(FixedRandomSource(1, d6Value = 1))
        val started = rules.startTravel(state(path = PathType.TRAIL), "ruins")
        val lost = rules.act(started, character, WildernessAction.TRAVEL)
        assertTrue(lost.state.lost)
        assertIs<WildernessEvent.Lost>(lost.events.single())
        val recovered = rules.act(lost.state, character, WildernessAction.RECOVER_WAY)
        assertTrue(!recovered.state.lost)
        assertIs<WildernessEvent.WayRecovered>(recovered.events.single())
    }

    @Test
    fun supplyBountyScalesWithParticipants() {
        val rules = WildernessRules(FixedRandomSource(1, d6Value = 4, d8Value = 8))
        val result = rules.act(state(), character, WildernessAction.SUPPLY, participants = 3)
        assertEquals(8, result.state.rations)
        assertIs<WildernessEvent.SupplyFound>(result.events.single())
    }

    @Test
    fun makeCampConsumesRationRemovesFatigueAndRestoresHp() {
        val rules = WildernessRules(FixedRandomSource(1))
        val result = rules.act(state(rations = 1), character, WildernessAction.MAKE_CAMP)
        assertEquals(0, result.state.rations)
        assertEquals(6, result.character.hp)
        assertEquals(0, result.character.fatigue)
        assertIs<WildernessEvent.CampMade>(result.events.single())
    }

    @Test
    fun campWithoutRationLeavesPartyDeprived() {
        val result = WildernessRules(FixedRandomSource(1)).act(state(), character, WildernessAction.MAKE_CAMP)
        assertTrue(result.state.deprived)
        assertIs<WildernessEvent.CampDenied>(result.events.first())
    }

    @Test
    fun weatherExtremeTwiceBecomesCatastrophic() {
        val rules = WildernessRules(FixedRandomSource(1, d6Value = 6))
        val first = rules.rollWeather(Season.SPRING, state()).first
        val second = rules.rollWeather(Season.SPRING, first).first
        assertEquals(Weather.EXTREME, first.weather)
        assertEquals(Weather.CATASTROPHIC, second.weather)
    }

    @Test
    fun nightTravelRollsTwoWildernessEvents() {
        val rules = WildernessRules(FixedRandomSource(1, d6Value = 6))
        val started = rules.startTravel(state(night = true), "ruins")
        val result = rules.act(started, character, WildernessAction.TRAVEL)
        assertEquals(2, result.events.count { it is WildernessEvent.Discovery })
    }
}
