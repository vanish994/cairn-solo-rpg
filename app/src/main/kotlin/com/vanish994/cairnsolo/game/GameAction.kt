package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.Attribute
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.RuleEvent
import com.vanish994.cairnsolo.rules.RolledCharacter
import com.vanish994.cairnsolo.rules.RulesEngine

sealed interface GameAction {
    data class CreateCharacter(val name: String, val rolled: RolledCharacter) : GameAction
    data object ExploreContinue : GameAction
    data object ExploreInvestigate : GameAction
    data object ExploreRest : GameAction
    data object Rest : GameAction
    data class ApplyDamage(val amount: Int) : GameAction
    data class AddItem(val item: InventoryItem) : GameAction
    data class RemoveItem(val itemId: String) : GameAction
    data class AddFatigue(val amount: Int = 1) : GameAction
    data class MarkDeprived(val deprived: Boolean) : GameAction
    data class Save(val attribute: Attribute) : GameAction
    data object StabilizeCritical : GameAction
}

data class GameResult(
    val state: GameState,
    val events: List<GameEvent>
)

sealed interface GameEvent {
    data class CharacterCreated(val characterId: String) : GameEvent
    data class SceneAdvanced(val sceneId: String) : GameEvent
    data class SceneInvestigated(val detail: String) : GameEvent
    data class RestCompleted(val hpRecovered: Int, val fatigueRecovered: Int) : GameEvent
    data class DamageResolved(
        val rawDamage: Int,
        val armorAbsorbed: Int,
        val hpDamage: Int,
        val critical: Boolean,
        val dead: Boolean,
        val scar: String?
    ) : GameEvent
    data class ItemAdded(val itemId: String) : GameEvent
    data class ItemRemoved(val itemId: String) : GameEvent
    data class FatigueAdded(val amount: Int) : GameEvent
    data class DeprivationChanged(val deprived: Boolean) : GameEvent
    data class SaveResolved(val attribute: Attribute, val roll: Int, val success: Boolean) : GameEvent
    data object CriticalStabilized : GameEvent
}

class GameActionResolver(
    private val exploration: ExplorationEngine,
    private val rules: RulesEngine
) {
    fun resolve(state: GameState, action: GameAction): GameResult = when (action) {
        is GameAction.CreateCharacter -> {
            val created = com.vanish994.cairnsolo.rules.createCharacter(action.name, action.rolled)
            GameResult(created, listOf(GameEvent.CharacterCreated(created.campaign.character.id)))
        }
        GameAction.ExploreContinue -> exploration.resolve(state, ExplorationAction.CONTINUE).toGameResult()
        GameAction.ExploreInvestigate -> exploration.resolve(state, ExplorationAction.INVESTIGATE).toGameResult()
        GameAction.ExploreRest, GameAction.Rest -> resolveRest(state)

        is GameAction.ApplyDamage -> {
            val result = rules.applyDamage(state.campaign.rules, action.amount)
            GameResult(state.withRules(result.newState), result.events.toGameEvents(result.newState))
        }

        is GameAction.AddItem -> {
            val result = rules.addItem(state.campaign.rules, action.item)
            GameResult(state.withRules(result.newState), listOf(GameEvent.ItemAdded(action.item.id)))
        }

        is GameAction.RemoveItem -> {
            val result = rules.removeItem(state.campaign.rules, action.itemId)
            GameResult(state.withRules(result.newState), listOf(GameEvent.ItemRemoved(action.itemId)))
        }

        is GameAction.AddFatigue -> {
            val result = rules.addFatigue(state.campaign.rules, action.amount)
            GameResult(state.withRules(result.newState), result.events.toGameEvents(result.newState))
        }

        is GameAction.MarkDeprived -> {
            val result = rules.markDeprived(state.campaign.rules, action.deprived)
            GameResult(
                state.withRules(result.newState),
                listOf(GameEvent.DeprivationChanged(action.deprived))
            )
        }

        is GameAction.Save -> {
            val result = rules.save(state.campaign.rules, action.attribute)
            GameResult(state.withRules(result.newState), result.events.toGameEvents(result.newState))
        }

        GameAction.StabilizeCritical -> {
            val result = rules.stabilizeCritical(state.campaign.rules)
            GameResult(
                state.withRules(result.newState),
                if (result.newState == state.campaign.rules) emptyList() else listOf(GameEvent.CriticalStabilized)
            )
        }
    }

    private fun resolveRest(state: GameState): GameResult {
        val result = rules.safeRest(state.campaign.rules)
        val hpRecovered = result.events.filterIsInstance<RuleEvent.HpRecovered>().sumOf { it.amount }
        val fatigueRecovered = result.events.filterIsInstance<RuleEvent.FatigueRecovered>().sumOf { it.amount }
        return GameResult(
            state.withRules(result.newState),
            listOf(GameEvent.RestCompleted(hpRecovered, fatigueRecovered))
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

    private fun List<RuleEvent>.toGameEvents(state: com.vanish994.cairnsolo.rules.CharacterState): List<GameEvent> =
        mapNotNull { event ->
            when (event) {
                is RuleEvent.DamageApplied -> GameEvent.DamageResolved(
                    event.rawDamage, event.armorAbsorbed, event.hpDamage,
                    state.critical, state.dead, state.scar?.name
                )
                is RuleEvent.FatigueAdded -> GameEvent.FatigueAdded(event.amount)
                is RuleEvent.SaveResolved -> GameEvent.SaveResolved(event.attribute, event.roll, event.success)
                else -> null
            }
        }
}
