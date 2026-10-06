package com.vanish994.cairnsolo.game

/**
 * Player intent is deliberately independent from UI and AI.
 * The MJ may propose an intent later, but only the game layer resolves it.
 */
sealed interface GameAction {
    data object ExploreContinue : GameAction
    data object ExploreInvestigate : GameAction
    data object ExploreRest : GameAction
}

/**
 * Stable output contract for the future MJ/context layer.
 * Narrative text is optional; mechanical events remain authoritative.
 */
data class GameResult(
    val state: GameState,
    val events: List<GameEvent>
)

sealed interface GameEvent {
    data class SceneAdvanced(val sceneId: String) : GameEvent
    data class SceneInvestigated(val detail: String) : GameEvent
    data object RestRequested : GameEvent
}

/**
 * Single entry point for campaign actions.
 */
class GameActionResolver(
    private val exploration: ExplorationEngine
) {
    fun resolve(state: GameState, action: GameAction): GameResult {
        val result = when (action) {
            GameAction.ExploreContinue ->
                exploration.resolve(state, ExplorationAction.CONTINUE)
            GameAction.ExploreInvestigate ->
                exploration.resolve(state, ExplorationAction.INVESTIGATE)
            GameAction.ExploreRest ->
                exploration.resolve(state, ExplorationAction.REST)
        }

        return GameResult(
            state = result.state,
            events = result.events.map { event ->
                when (event) {
                    is ExplorationEvent.Advanced ->
                        GameEvent.SceneAdvanced(event.destination)
                    is ExplorationEvent.Investigated ->
                        GameEvent.SceneInvestigated(event.detail)
                    ExplorationEvent.RestRequested ->
                        GameEvent.RestRequested
                }
            }
        )
    }
}
