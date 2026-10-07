package com.vanish994.cairnsolo.rules

enum class Watch { MORNING, AFTERNOON, NIGHT }
enum class PathType(val penalty: Int, val lostOdds: Int) { ROAD(0, 0), TRAIL(1, 2), WILDERNESS(2, 3) }
enum class TravelDistance(val penalty: Int) { SHORT(1), MEDIUM(2), LONG(3) }
enum class Terrain(val penalty: Int) { EASY(0), TOUGH(1), PERILOUS(2) }
enum class Weather(val penalty: Int) { NICE(0), FAIR(0), UNPLEASANT(1), INCLEMENT(1), EXTREME(1), CATASTROPHIC(99) }
enum class Season { SPRING, SUMMER, FALL, WINTER }
data class WildernessState(
    val currentPoint: String,
    val destinationPoint: String? = null,
    val remainingWatches: Int = 0,
    val watch: Watch = Watch.MORNING,
    val path: PathType = PathType.ROAD,
    val distance: TravelDistance = TravelDistance.SHORT,
    val terrain: Terrain = Terrain.EASY,
    val weather: Weather = Weather.NICE,
    val nightTravel: Boolean = false,
    val lost: Boolean = false,
    val rations: Int = 0,
    val deprived: Boolean = false,
    val previousWeatherWasExtreme: Boolean = false
) {
    init { require(remainingWatches >= 0); require(rations >= 0) }
}

enum class WildernessAction { TRAVEL, EXPLORE, SUPPLY, MAKE_CAMP, RECOVER_WAY }

sealed interface WildernessEvent {
    data class TravelPlanned(val watches: Int) : WildernessEvent
    data class TravelProgress(val watchesRemaining: Int) : WildernessEvent
    data class Arrived(val point: String) : WildernessEvent
    data object Lost : WildernessEvent
    data object WayRecovered : WildernessEvent
    data class SupplyFound(val rations: Int) : WildernessEvent
    data object CampMade : WildernessEvent
    data object CampDenied : WildernessEvent
    data class WeatherRolled(val weather: Weather) : WildernessEvent
    data class EventRolled(val result: Int) : WildernessEvent
    data object Encounter : WildernessEvent
    data object Sign : WildernessEvent
    data object Environment : WildernessEvent
    data object Loss : WildernessEvent
    data object Exhaustion : WildernessEvent
    data object Discovery : WildernessEvent
    data object FatigueRequired : WildernessEvent
}

data class WildernessResult(val state: WildernessState, val character: CharacterState, val events: List<WildernessEvent>)

data class TravelPlan(val destination: String, val watches: Int, val lostOdds: Int)

class WildernessRules(private val random: RandomSource, private val rules: RulesEngine = RulesEngine(random)) {
    fun planTravel(state: WildernessState, destination: String): TravelPlan {
        require(destination.isNotBlank())
        val watches = state.path.penalty + state.distance.penalty + state.terrain.penalty + state.weather.penalty + if (state.nightTravel) 1 else 0
        return TravelPlan(destination, watches.coerceAtLeast(1), state.path.lostOdds)
    }

    fun startTravel(state: WildernessState, destination: String): WildernessState {
        val plan = planTravel(state, destination)
        return state.copy(destinationPoint = plan.destination, remainingWatches = plan.watches, lost = false)
    }

    fun act(state: WildernessState, character: CharacterState, action: WildernessAction, participants: Int = 1): WildernessResult {
        require(participants > 0)
        return when (action) {
            WildernessAction.TRAVEL -> travel(state, character)
            WildernessAction.EXPLORE -> eventAction(state, character, participants)
            WildernessAction.SUPPLY -> supply(state, character, participants)
            WildernessAction.MAKE_CAMP -> makeCamp(state, character)
            WildernessAction.RECOVER_WAY -> WildernessResult(state.copy(lost = false), character, listOf(WildernessEvent.WayRecovered))
        }
    }

    fun rollWeather(season: Season, state: WildernessState): Pair<WildernessState, WildernessEvent> {
        val rolled = when (random.d6()) {
            1 -> if (season == Season.SPRING || season == Season.SUMMER) Weather.NICE else Weather.FAIR
            2 -> if (season == Season.SUMMER) Weather.NICE else if (season == Season.WINTER) Weather.UNPLEASANT else Weather.FAIR
            3 -> if (season == Season.SPRING || season == Season.SUMMER) Weather.FAIR else Weather.UNPLEASANT
            4 -> if (season == Season.SPRING || season == Season.SUMMER) Weather.UNPLEASANT else Weather.INCLEMENT
            5 -> if (season == Season.WINTER) Weather.EXTREME else Weather.INCLEMENT
            else -> Weather.EXTREME
        }
        val weather = if (rolled == Weather.EXTREME && state.previousWeatherWasExtreme) Weather.CATASTROPHIC else rolled
        return state.copy(weather = weather, previousWeatherWasExtreme = rolled == Weather.EXTREME) to WildernessEvent.WeatherRolled(weather)
    }

    private fun travel(state: WildernessState, character: CharacterState): WildernessResult {
        require(state.destinationPoint != null) { "Travel has no destination" }
        if (state.lost) return WildernessResult(state, character, listOf(WildernessEvent.Lost))
        if (state.remainingWatches <= 0) return WildernessResult(state, character, listOf(WildernessEvent.TravelProgress(0)))
        val lost = state.path.lostOdds > 0 && random.d6() <= state.path.lostOdds
        val remaining = state.remainingWatches - 1
        val events = mutableListOf<WildernessEvent>()
        if (lost) {
            events += WildernessEvent.Lost
            return WildernessResult(state.copy(lost = true), character, events)
        }
        if (state.nightTravel) repeat(2) { events += rollWildernessEvent(random.d6()) }
        else events += rollWildernessEvent(random.d6())
        val next = state.copy(remainingWatches = remaining)
        events += if (remaining == 0) WildernessEvent.Arrived(state.destinationPoint) else WildernessEvent.TravelProgress(remaining)
        return WildernessResult(if (remaining == 0) next.copy(currentPoint = state.destinationPoint, destinationPoint = null) else next, character, events)
    }

    private fun eventAction(state: WildernessState, character: CharacterState, participants: Int): WildernessResult {
        val event = rollWildernessEvent(random.d6())
        return WildernessResult(state, character, listOf(event))
    }

    private fun supply(state: WildernessState, character: CharacterState, participants: Int): WildernessResult {
        val die = when (participants) { 1 -> 4; 2 -> 6; 3 -> 8; 4 -> 10; else -> 12 }
        val found = random.roll(die)
        return WildernessResult(state.copy(rations = state.rations + found), character, listOf(WildernessEvent.SupplyFound(found)))
    }

    private fun makeCamp(state: WildernessState, character: CharacterState): WildernessResult {
        if (state.rations <= 0) return WildernessResult(state.copy(deprived = true), character, listOf(WildernessEvent.CampDenied, WildernessEvent.FatigueRequired))
        val nextCharacter = rules.safeRest(character.copy(deprived = false, deprivedDays = 0))
        return WildernessResult(state.copy(rations = state.rations - 1, deprived = false), nextCharacter.newState, listOf(WildernessEvent.CampMade))
    }

    private fun rollWildernessEvent(roll: Int): WildernessEvent = when (roll) {
        1 -> WildernessEvent.Encounter
        2 -> WildernessEvent.Sign
        3 -> WildernessEvent.Environment
        4 -> WildernessEvent.Loss
        5 -> WildernessEvent.Exhaustion
        else -> WildernessEvent.Discovery
    }
}
