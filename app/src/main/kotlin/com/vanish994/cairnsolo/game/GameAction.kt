package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.RuleEvent
import com.vanish994.cairnsolo.rules.RulesEngine

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
    data class RestCompleted(val hpRecovered: Int, val fatigueRecovered: Int) : GameEvent
}

/**
 * Single entry point for campaign actions.
 */
class GameActionResolver(
    private val exploration: ExplorationEngine,
    private val rules: RulesEngine
) {
    fun resolve(state: GameState, action: GameAction): GameResult {
        return when (action) {
            GameAction.ExploreContinue ->
                exploration.resolve(state, ExplorationAction.CONTINUE).toGameResult()

            GameAction.ExploreInvestigate ->
                exploration.resolve(state, ExplorationAction.INVESTIGATE).toGameResult()

            GameAction.ExploreRest -> {
                val result = rules.safeRest(state.campaign.rules)
                val nextState = state.withRules(result.newState)
                val hpRecovered = result.events
                    .filterIsInstance<RuleEvent.HpRecovered>()
                    .sumOf { it.amount }
                val fatigueRecovered = result.events
                    .filterIsInstance<RuleEvent.FatigueRecovered>()
                    .sumOf { it.amount }

                GameResult(
                    state = nextState,
                    events = listOf(
                        GameEvent.RestCompleted(
                            hpRecovered = hpRecovered,
                            fatigueRecovered = fatigueRecovered
                        )
                    )
                )
            }
        }
    }

    private fun ExplorationResult.toGameResult(): GameResult =
        GameResult(
            state = state,
            events = events.map { event ->
                when (event) {
                    is ExplorationEvent.Advanced ->
                        GameEvent.SceneAdvanced(event.destination)
                    is ExplorationEvent.Investigated ->
                        GameEvent.SceneInvestigated(event.detail)
                    ExplorationEvent.RestRequested ->
                        error("Exploration REST must be resolved by RulesEngine")
                }
            }
        )
}
