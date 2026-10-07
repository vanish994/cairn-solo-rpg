package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.AttackMode
import com.vanish994.cairnsolo.rules.Attribute
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.CombatRules
import com.vanish994.cairnsolo.rules.DowntimeAction
import com.vanish994.cairnsolo.rules.DungeonAction
import com.vanish994.cairnsolo.rules.Season
import com.vanish994.cairnsolo.rules.WildernessAction
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
    data class CastSpell(val itemId: String, val requiresWilSave: Boolean = false, val failure: com.vanish994.cairnsolo.rules.SpellFailureConsequence = com.vanish994.cairnsolo.rules.SpellFailureConsequence.NONE, val dropItemIdForFatigue: String? = null) : GameAction
    data class Purchase(val itemId: String) : GameAction
    data class PerformDowntime(val action: DowntimeAction) : GameAction
    data class AddDowntimeMilestone(val milestone: com.vanish994.cairnsolo.rules.Milestone) : GameAction
    data class StartTravel(val destination: String) : GameAction
    data class WildernessAct(val action: WildernessAction, val participants: Int = 1) : GameAction
    data class RollWeather(val season: Season) : GameAction
    data class DungeonAct(val action: DungeonAction) : GameAction
    data object LightTorch : GameAction
    data object LightLantern : GameAction
    data object ExtinguishLight : GameAction
    data object StartDungeonPanic : GameAction
    data object OvercomeDungeonPanic : GameAction
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
    data class SpellResolved(val spellId: String, val itemId: String) : GameEvent
    data class PurchaseResolved(val itemId: String, val goldRemaining: Int) : GameEvent
    data class DowntimeResolved(val action: com.vanish994.cairnsolo.rules.DowntimeActionType) : GameEvent
    data class WildernessResolved(val action: WildernessAction) : GameEvent
    data class DungeonResolved(val action: com.vanish994.cairnsolo.rules.DungeonActionKind) : GameEvent
    data class RuleNotice(val summary: String) : GameEvent
}

class GameActionResolver(
    private val exploration: ExplorationEngine,
    private val rules: RulesEngine,
    private val combat: CombatRules = CombatRules(rules.random),
    private val magic: com.vanish994.cairnsolo.rules.MagicRules = com.vanish994.cairnsolo.rules.MagicRules(rules),
    private val marketplace: com.vanish994.cairnsolo.rules.MarketplaceRules = com.vanish994.cairnsolo.rules.MarketplaceRules(),
    private val downtime: com.vanish994.cairnsolo.rules.DowntimeRules = com.vanish994.cairnsolo.rules.DowntimeRules(rules),
    private val dungeon: com.vanish994.cairnsolo.rules.DungeonRules = com.vanish994.cairnsolo.rules.DungeonRules(rules.random, rules),
    private val wilderness: com.vanish994.cairnsolo.rules.WildernessRules = com.vanish994.cairnsolo.rules.WildernessRules(rules.random, rules)
) {
    fun resolve(state: GameState, action: GameAction): GameResult = when (action) {
        is GameAction.CreateCharacter -> {
            val created = com.vanish994.cairnsolo.rules.createCharacter(action.name, action.rolled)
            val world = WorldGenerator(rules.random).generate(
                WorldSeed(action.name.trim(), action.rolled.background?.name ?: "uma fronteira desconhecida")
            )
            val withWorld = created.copy(campaign = created.campaign.copy(
                worldState = world,
                wilderness = com.vanish994.cairnsolo.rules.WildernessState(world.currentLocationId),
                dungeon = world.dungeons.firstOrNull()?.let { com.vanish994.cairnsolo.rules.DungeonState(it.id) }
            ))
            GameResult(withWorld, listOf(GameEvent.CharacterCreated(withWorld.campaign.character.id)))
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
        is GameAction.CastSpell -> castSpell(state, action)
        is GameAction.Purchase -> purchase(state, action)
        is GameAction.PerformDowntime -> performDowntime(state, action)
        is GameAction.AddDowntimeMilestone -> GameResult(state.copy(campaign = state.campaign.copy(downtime = downtime.addMilestone(state.campaign.downtime, action.milestone))), listOf(GameEvent.RuleNotice("Marco de downtime adicionado: ${action.milestone.id}")))
        is GameAction.StartTravel -> GameResult(state.copy(campaign = state.campaign.copy(wilderness = wilderness.startTravel(state.campaign.wilderness ?: error("Wilderness state not initialized"), action.destination), turn = state.campaign.turn + 1)), listOf(GameEvent.RuleNotice("Viagem iniciada para ${action.destination}")))
        is GameAction.WildernessAct -> wildernessAct(state, action)
        is GameAction.RollWeather -> rollWeather(state, action)
        is GameAction.DungeonAct -> dungeonAct(state, action)
        GameAction.LightTorch -> dungeonLight(state) { dungeon.lightTorch(it) }
        GameAction.LightLantern -> dungeonLight(state) { dungeon.lightLantern(it) }
        GameAction.ExtinguishLight -> dungeonLight(state) { dungeon.extinguish(it) }
        GameAction.StartDungeonPanic -> dungeonTurn(state, dungeon.startPanic(state.campaign.dungeon ?: error("Dungeon state not initialized"), state.campaign.rules))
        GameAction.OvercomeDungeonPanic -> dungeonTurn(state, dungeon.overcomePanic(state.campaign.dungeon ?: error("Dungeon state not initialized"), state.campaign.rules))
    }

    private fun castSpell(state: GameState, action: GameAction.CastSpell): GameResult {
        val result = magic.cast(state.campaign.rules, action.itemId, action.requiresWilSave, action.failure, action.dropItemIdForFatigue)
        return GameResult(state.withRules(result.state), listOf(GameEvent.SpellResolved(result.spell.id, action.itemId)))
    }

    private fun purchase(state: GameState, action: GameAction.Purchase): GameResult {
        val result = marketplace.purchase(state.campaign.rules, state.campaign.profile.gold, action.itemId)
        val profile = state.campaign.profile.copy(gold = result.goldRemaining)
        return GameResult(state.withRules(result.state).copy(campaign = state.withRules(result.state).campaign.copy(profile = profile)), listOf(GameEvent.PurchaseResolved(action.itemId, result.goldRemaining)))
    }

    private fun performDowntime(state: GameState, action: GameAction.PerformDowntime): GameResult {
        val current = state.campaign.downtime.copy(gold = state.campaign.profile.gold)
        val result = downtime.perform(current, state.campaign.rules, action.action)
        val profile = state.campaign.profile.copy(gold = result.state.gold)
        val next = state.withRules(result.character).copy(campaign = state.withRules(result.character).campaign.copy(downtime = result.state, profile = profile))
        return GameResult(next, listOf(GameEvent.DowntimeResolved(action.action.type)))
    }

    private fun wildernessAct(state: GameState, action: GameAction.WildernessAct): GameResult {
        val current = state.campaign.wilderness ?: error("Wilderness state not initialized")
        val result = wilderness.act(current, state.campaign.rules, action.action, action.participants)
        val next = state.withRules(result.character).copy(campaign = state.withRules(result.character).campaign.copy(wilderness = result.state))
        return GameResult(next, listOf(GameEvent.WildernessResolved(action.action)))
    }

    private fun rollWeather(state: GameState, action: GameAction.RollWeather): GameResult {
        val current = state.campaign.wilderness ?: error("Wilderness state not initialized")
        val (next, event) = wilderness.rollWeather(action.season, current)
        return GameResult(state.copy(campaign = state.campaign.copy(wilderness = next, turn = state.campaign.turn + 1)), listOf(GameEvent.RuleNotice(event.toString())))
    }

    private fun dungeonAct(state: GameState, action: GameAction.DungeonAct): GameResult {
        val current = state.campaign.dungeon ?: error("Dungeon state not initialized")
        return dungeonTurn(state, dungeon.resolveTurn(current, state.campaign.rules, action))
    }

    private fun dungeonTurn(state: GameState, result: com.vanish994.cairnsolo.rules.DungeonTurnResult): GameResult {
        val next = state.withRules(result.character).copy(campaign = state.withRules(result.character).campaign.copy(dungeon = result.state))
        return GameResult(next, listOf(GameEvent.DungeonResolved(com.vanish994.cairnsolo.rules.DungeonActionKind.OTHER)))
    }

    private fun dungeonLight(state: GameState, operation: (com.vanish994.cairnsolo.rules.DungeonState) -> com.vanish994.cairnsolo.rules.DungeonLightResult): GameResult {
        val current = state.campaign.dungeon ?: error("Dungeon state not initialized")
        val result = operation(current)
        return GameResult(state.copy(campaign = state.campaign.copy(dungeon = result.state, turn = state.campaign.turn + 1)), result.events.map { GameEvent.RuleNotice(it.toString()) })
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
