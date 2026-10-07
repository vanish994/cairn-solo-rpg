package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.AttackMode
import com.vanish994.cairnsolo.rules.Attribute
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.CombatRules
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.RandomSource
import com.vanish994.cairnsolo.rules.RuleEvent
import com.vanish994.cairnsolo.rules.RolledCharacter
import com.vanish994.cairnsolo.rules.RulesEngine
import com.vanish994.cairnsolo.rules.WeaponProfile

sealed interface GameAction {
    data class CreateCharacter(val name: String, val rolled: RolledCharacter) : GameAction
    data object ExploreContinue : GameAction
    data object ExploreInvestigate : GameAction
    data object ExploreRest : GameAction
    data class GuardianIntent(val text: String) : GameAction
    data object Rest : GameAction
    data class ApplyDamage(val amount: Int) : GameAction
    data class AddItem(val item: InventoryItem) : GameAction
    data class RemoveItem(val itemId: String) : GameAction
    data class AddFatigue(val amount: Int = 1) : GameAction
    data class MarkDeprived(val deprived: Boolean) : GameAction
    data class Save(val attribute: Attribute) : GameAction
    data object StabilizeCritical : GameAction
    data object RecoverScar : GameAction
    data class BeginCombat(val opponentId: String, val opponent: CharacterState, val opponentWeapon: WeaponProfile = WeaponProfile("unarmed", "d4")) : GameAction
    data class CombatAttack(val weapon: WeaponProfile? = null, val mode: AttackMode = AttackMode.NORMAL) : GameAction
    data object EndCombat : GameAction
}

data class GameResult(val state: GameState, val events: List<GameEvent>)

sealed interface GameEvent {
    data class CharacterCreated(val characterId: String) : GameEvent
    data class SceneAdvanced(val sceneId: String) : GameEvent
    data class SceneInvestigated(val detail: String) : GameEvent
    data class RestCompleted(val hpRecovered: Int, val fatigueRecovered: Int) : GameEvent
    data class DamageResolved(val rawDamage: Int, val armorAbsorbed: Int, val hpDamage: Int, val critical: Boolean, val dead: Boolean, val scar: String?) : GameEvent
    data class ItemAdded(val itemId: String) : GameEvent
    data class ItemRemoved(val itemId: String) : GameEvent
    data class FatigueAdded(val amount: Int) : GameEvent
    data class DeprivationChanged(val deprived: Boolean) : GameEvent
    data class SaveResolved(val attribute: Attribute, val roll: Int, val success: Boolean) : GameEvent
    data object CriticalStabilized : GameEvent
    data class ScarRecovered(val scar: String) : GameEvent
    data class CombatStarted(val opponentId: String, val round: Int, val playerCanAct: Boolean) : GameEvent
    data class CombatAttackResolved(val opponentId: String, val round: Int, val playerDamage: DamageResolved, val enemyDamage: DamageResolved?, val playerCanAct: Boolean) : GameEvent
    data class CombatEnded(val opponentId: String, val victory: Boolean) : GameEvent
}

class GameActionResolver(
    private val exploration: ExplorationEngine,
    private val rules: RulesEngine,
    private val combat: CombatRules = CombatRules(rules.random)
) {
    fun resolve(state: GameState, action: GameAction): GameResult = when (action) {
        is GameAction.CreateCharacter -> {
            val created = com.vanish994.cairnsolo.rules.createCharacter(action.name, action.rolled)
            GameResult(created, listOf(GameEvent.CharacterCreated(created.campaign.character.id)))
        }
        GameAction.ExploreContinue -> exploration.resolve(state, ExplorationAction.CONTINUE).toGameResult()
        GameAction.ExploreInvestigate -> exploration.resolve(state, ExplorationAction.INVESTIGATE).toGameResult()
        GameAction.ExploreRest, GameAction.Rest -> resolveRest(state)
        is GameAction.GuardianIntent -> GameResult(state.recordGuardianIntent(action.text), emptyList())
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
            GameResult(state.withRules(result.newState), listOf(GameEvent.DeprivationChanged(action.deprived)))
        }
        is GameAction.Save -> {
            val result = rules.save(state.campaign.rules, action.attribute)
            GameResult(state.withRules(result.newState), result.events.toGameEvents(result.newState))
        }
        GameAction.RecoverScar -> {
            val previousScar = state.campaign.rules.scar?.name ?: "unknown"
            val result = rules.recoverScar(state.campaign.rules)
            GameResult(state.withRules(result.newState), if (result.newState == state.campaign.rules) emptyList() else listOf(GameEvent.ScarRecovered(previousScar)))
        }
        GameAction.StabilizeCritical -> {
            val result = rules.stabilizeCritical(state.campaign.rules)
            GameResult(state.withRules(result.newState), if (result.newState == state.campaign.rules) emptyList() else listOf(GameEvent.CriticalStabilized))
        }
        is GameAction.BeginCombat -> beginCombat(state, action)
        is GameAction.CombatAttack -> combatAttack(state, action)
        GameAction.EndCombat -> endCombat(state)
    }

    private fun resolveRest(state: GameState): GameResult {
        val result = rules.safeRest(state.campaign.rules)
        val hpRecovered = result.events.filterIsInstance<RuleEvent.HpRecovered>().sumOf { it.amount }
        val fatigueRecovered = result.events.filterIsInstance<RuleEvent.FatigueRecovered>().sumOf { it.amount }
        return GameResult(state.withRules(result.newState), listOf(GameEvent.RestCompleted(hpRecovered, fatigueRecovered)))
    }

    private fun beginCombat(state: GameState, action: GameAction.BeginCombat): GameResult {
        require(state.campaign.combat == null) { "Combat already active" }
        val save = rules.save(state.campaign.rules, Attribute.DEX)
        if (save.success) {
            val combatState = CombatState(action.opponentId, action.opponent, action.opponentWeapon, 1, true)
            val next = state.copy(campaign = state.campaign.copy(combat = combatState, turn = state.campaign.turn + 1))
            return GameResult(next, listOf(GameEvent.CombatStarted(action.opponentId, 1, true), GameEvent.SaveResolved(Attribute.DEX, save.roll, true)))
        }
        val enemyAttack = combat.attack(action.opponent, state.campaign.rules, action.opponentWeapon)
        val damage = enemyAttack.events.toDamageEvent(enemyAttack.target)
        val dead = enemyAttack.target.dead
        val next = state.copy(campaign = state.campaign.copy(
            rules = enemyAttack.target,
            combat = if (dead) null else CombatState(action.opponentId, action.opponent, action.opponentWeapon, 2, true),
            turn = state.campaign.turn + 1
        ))
        val events = mutableListOf<GameEvent>(GameEvent.CombatStarted(action.opponentId, 1, false), GameEvent.SaveResolved(Attribute.DEX, save.roll, false), GameEvent.CombatAttackResolved(action.opponentId, 1, damage, null, !dead))
        if (dead) events += GameEvent.CombatEnded(action.opponentId, false)
        return GameResult(next, events)
    }

    private fun combatAttack(state: GameState, action: GameAction.CombatAttack): GameResult {
        val current = state.campaign.combat ?: error("No active combat")
        require(current.playerCanAct) { "Player cannot act this round" }
        val playerAttack = combat.attack(state.campaign.rules, current.opponent, action.weapon, action.mode)
        val playerDamage = playerAttack.events.toDamageEvent(playerAttack.target)
        if (playerAttack.target.dead || playerAttack.target.hp == 0) {
            val next = state.copy(campaign = state.campaign.copy(combat = null, turn = state.campaign.turn + 1))
            return GameResult(next, listOf(GameEvent.CombatAttackResolved(current.opponentId, current.round, playerDamage, null, false), GameEvent.CombatEnded(current.opponentId, true)))
        }
        val enemyAttack = combat.attack(playerAttack.attacker, state.campaign.rules, current.opponentWeapon)
        val enemyDamage = enemyAttack.events.toDamageEvent(enemyAttack.target)
        val playerDead = enemyAttack.target.dead
        val next = state.copy(
            campaign = state.campaign.copy(
                rules = enemyAttack.target,
                combat = if (playerDead) null else current.copy(opponent = playerAttack.target, round = current.round + 1, playerCanAct = true),
                turn = state.campaign.turn + 1
            ),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        val events = mutableListOf<GameEvent>(GameEvent.CombatAttackResolved(current.opponentId, current.round, playerDamage, enemyDamage, !playerDead))
        if (playerDead) events += GameEvent.CombatEnded(current.opponentId, false)
        return GameResult(next, events)
    }

    private fun endCombat(state: GameState): GameResult {
        val current = state.campaign.combat ?: return GameResult(state, emptyList())
        return GameResult(state.copy(campaign = state.campaign.copy(combat = null, turn = state.campaign.turn + 1)), listOf(GameEvent.CombatEnded(current.opponentId, false)))
    }

    private fun ExplorationResult.toGameResult(): GameResult = GameResult(state, events.map { event ->
        when (event) {
            is ExplorationEvent.Advanced -> GameEvent.SceneAdvanced(event.destination)
            is ExplorationEvent.Investigated -> GameEvent.SceneInvestigated(event.detail)
            ExplorationEvent.RestRequested -> error("Exploration REST must be resolved by RulesEngine")
        }
    })

    private fun List<RuleEvent>.toGameEvents(state: CharacterState): List<GameEvent> = mapNotNull { event ->
        when (event) {
            is RuleEvent.DamageApplied -> GameEvent.DamageResolved(event.rawDamage, event.armorAbsorbed, event.hpDamage, state.critical, state.dead, state.scar?.name)
            is RuleEvent.FatigueAdded -> GameEvent.FatigueAdded(event.amount)
            is RuleEvent.SaveResolved -> GameEvent.SaveResolved(event.attribute, event.roll, event.success)
            else -> null
        }
    }

    private fun List<RuleEvent>.toDamageEvent(state: CharacterState): GameEvent.DamageResolved {
        val damage = filterIsInstance<RuleEvent.DamageApplied>().single()
        return GameEvent.DamageResolved(damage.rawDamage, damage.armorAbsorbed, damage.hpDamage, state.critical, state.dead, state.scar?.name)
    }
}
