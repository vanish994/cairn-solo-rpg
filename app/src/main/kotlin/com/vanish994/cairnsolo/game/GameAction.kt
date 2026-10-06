package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.RuleEvent
import com.vanish994.cairnsolo.rules.RulesEngine

sealed interface GameAction {
    data object ExploreContinue : GameAction
    data object ExploreInvestigate : GameAction
    data object ExploreRest : GameAction
    data object Rest : GameAction
    data class ApplyDamage(val amount: Int) : GameAction
    data class AddItem(val item: InventoryItem) : GameAction
    data class RemoveItem(val itemId: String) : GameAction
}

data class GameResult(
    val state: GameState,
    val events: List<GameEvent>
)

sealed interface GameEvent {
    data class SceneAdvanced(val sceneId: String) : GameEvent
    data class SceneInvestigated(val detail: String) : GameEvent
    data class RestCompleted(val hpRecovered: Int, val fatigueRecovered: Int) : GameEvent
    data class DamageApplied(val amount: Int) : GameEvent
    data class ItemAdded(val itemId: String) : GameEvent
    data class ItemRemoved(val itemId: String) : GameEvent
}

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

            GameAction.ExploreRest,
            GameAction.Rest -> resolveRest(state)

            is GameAction.ApplyDamage -> {
                val result = rules.applyDamage(state.campaign.rules, action.amount)
                GameResult(
                    state = state.withRules(result.newState),
                    events = listOf(GameEvent.DamageApplied(action.amount))
                )
            }

            is GameAction.AddItem -> {
                val result = rules.addItem(state.campaign.rules, action.item)
                GameResult(
                    state = state.withRules(result.newState),
                    events = listOf(GameEvent.ItemAdded(action.item.id))
                )
            }

            is GameAction.RemoveItem -> {
                val result = rules.removeItem(state.campaign.rules, action.itemId)
                GameResult(
                    state = state.withRules(result.newState),
                    events = listOf(GameEvent.ItemRemoved(action.itemId))
                )
            }
        }
    }

    private fun resolveRest(state: GameState): GameResult {
        val result = rules.safeRest(state.campaign.rules)
        val hpRecovered = result.events.filterIsInstance<RuleEvent.HpRecovered>().sumOf { it.amount }
        val fatigueRecovered = result.events.filterIsInstance<RuleEvent.FatigueRecovered>().sumOf { it.amount }

        return GameResult(
            state = state.withRules(result.newState),
            events = listOf(GameEvent.RestCompleted(hpRecovered, fatigueRecovered))
        )
    }

    private fun ExplorationResult.toGameResult(): GameResult =
        GameResult(
            state = state,
            events = events.map { event ->
                when (event) {
                    is ExplorationEvent.Advanced -> GameEvent.SceneAdvanced(event.destination)
                    is ExplorationEvent.Investigated -> GameEvent.SceneInvestigated(event.detail)
                    ExplorationEvent.RestRequested -> error("Exploration REST must be resolved by RulesEngine")
                }
            }
        )
}
