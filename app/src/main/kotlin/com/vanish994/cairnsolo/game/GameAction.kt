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
import com.vanish994.cairnsolo.rules.MoraleOutcome

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
    data object RollReaction : GameAction
    data class CheckMorale(val morale: Int, val failureOutcome: MoraleOutcome = MoraleOutcome.RETREAT) : GameAction
    data class HireHireling(val entryId: String, val hirelingId: String, val name: String, val loyalty: Int = 7, val morale: Int = 7) : GameAction
    data object PayHirelings : GameAction
    data class CheckHirelingMorale(val hirelingId: String, val failureOutcome: MoraleOutcome = MoraleOutcome.RETREAT) : GameAction
    data class RecordGrowthEvidence(val evidence: GrowthEvidence) : GameAction
    data class RecordGrowthEvidenceProposal(val proposal: GrowthEvidenceProposal) : GameAction
    data class ApplyGrowthChangeProposal(val proposal: GrowthChangeProposal) : GameAction
    data class ApplyGrowth(val proposal: GrowthProposal) : GameAction
    data class ApplyCanonProposals(val proposals: List<CanonProposal>) : GameAction
    data class AdvanceFaction(val factionId: String, val amount: Int = 1, val reason: String) : GameAction
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
    data class ReactionResolved(val roll: Int, val disposition: com.vanish994.cairnsolo.rules.ReactionDisposition) : GameEvent
    data class MoraleResolved(val roll: Int, val morale: Int, val outcome: MoraleOutcome) : GameEvent
    data class HirelingResolved(val hirelingId: String, val outcome: String, val goldRemaining: Int? = null) : GameEvent
    data class GrowthEvidenceRecorded(val evidenceId: String) : GameEvent
    data class GrowthApplied(val proposalId: String) : GameEvent
    data class CanonUpdated(val count: Int) : GameEvent
    data class FactionProgressChanged(val factionId: String, val previous: Int, val current: Int, val goal: String) : GameEvent
}

class GameActionResolver(
    private val exploration: ExplorationEngine,
    private val rules: RulesEngine,
    private val combat: CombatRules = CombatRules(rules.random),
    private val magic: com.vanish994.cairnsolo.rules.MagicRules = com.vanish994.cairnsolo.rules.MagicRules(rules),
    private val marketplace: com.vanish994.cairnsolo.rules.MarketplaceRules = com.vanish994.cairnsolo.rules.MarketplaceRules(),
    private val downtime: com.vanish994.cairnsolo.rules.DowntimeRules = com.vanish994.cairnsolo.rules.DowntimeRules(rules),
    private val dungeon: com.vanish994.cairnsolo.rules.DungeonRules = com.vanish994.cairnsolo.rules.DungeonRules(rules.random, rules),
    private val wilderness: com.vanish994.cairnsolo.rules.WildernessRules = com.vanish994.cairnsolo.rules.WildernessRules(rules.random, rules),
    private val reactions: com.vanish994.cairnsolo.rules.ReactionRules = com.vanish994.cairnsolo.rules.ReactionRules(rules.random),
    private val morale: com.vanish994.cairnsolo.rules.MoraleRules = com.vanish994.cairnsolo.rules.MoraleRules(rules.random),
    private val hirelingRules: com.vanish994.cairnsolo.rules.HirelingRules = com.vanish994.cairnsolo.rules.HirelingRules(morale),
    private val growth: GrowthResolver = GrowthResolver()
) {
    /** Entrada de criação para a UI; mesmo a primeira transição passa pelo domínio. */
    fun resolve(action: GameAction): GameResult = resolve(
        GameState(
            campaign = CampaignState(
                character = CharacterIdentity(name = "creation-bootstrap"),
                rules = CharacterState(str = 1, dex = 1, wil = 1, hp = 1, maxHp = 1, armor = 0)
            )
        ),
        action
    )

    fun resolve(state: GameState, action: GameAction): GameResult = when (action) {
        is GameAction.CreateCharacter -> {
            val created = com.vanish994.cairnsolo.rules.createCharacter(action.name, action.rolled)
            val world = WorldGenerator(rules.random).generate(
                WorldSeed(action.name.trim(), action.rolled.background?.name ?: "uma fronteira desconhecida")
            )
            val withWorld = created.copy(campaign = created.campaign.copy(
                worldState = world,
                wilderness = com.vanish994.cairnsolo.rules.WildernessState(world.currentLocationId),
                dungeon = world.dungeons.firstOrNull()?.let { com.vanish994.cairnsolo.rules.DungeonState(it.id) },
                sceneId = world.currentLocationId,
                sceneTitle = world.settlements.first().name,
                sceneDescription = "${world.settlements.first().description} ${world.region.description}",
                exits = listOf("conversar com os moradores", "seguir os rumores", "explorar a região"),
                guardianMessage = openingNarration(world)
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
            require(state.campaign.rules.inventory.none { it.id == action.item.id }) { "Item id already exists in inventory" }
            val result = rules.addItem(state.campaign.rules, action.item)
            GameResult(state.withHistory("item-add-${state.campaign.turn}-${action.item.id}", HistoryEventType.ITEM_CHANGED, "Item adicionado: ${action.item.id}", listOf(action.item.id)).withRules(result.newState), listOf(GameEvent.ItemAdded(action.item.id)))
        }
        is GameAction.RemoveItem -> {
            val result = rules.removeItem(state.campaign.rules, action.itemId)
            GameResult(state.withHistory("item-remove-${state.campaign.turn}-${action.itemId}", HistoryEventType.ITEM_CHANGED, "Item removido: ${action.itemId}", listOf(action.itemId)).withRules(result.newState), listOf(GameEvent.ItemRemoved(action.itemId)))
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
        is GameAction.DungeonAct -> dungeonAct(state, action.action)
        GameAction.LightTorch -> dungeonLight(state) { dungeon.lightTorch(it) }
        GameAction.LightLantern -> dungeonLight(state) { dungeon.lightLantern(it) }
        GameAction.ExtinguishLight -> dungeonLight(state) { dungeon.extinguish(it) }
        GameAction.StartDungeonPanic -> dungeonTurn(state, dungeon.startPanic(state.campaign.dungeon ?: error("Dungeon state not initialized"), state.campaign.rules))
        GameAction.OvercomeDungeonPanic -> dungeonTurn(state, dungeon.overcomePanic(state.campaign.dungeon ?: error("Dungeon state not initialized"), state.campaign.rules))
        GameAction.RollReaction -> {
            val result = reactions.roll()
            GameResult(state.copy(campaign = state.campaign.copy(turn = state.campaign.turn + 1)), listOf(GameEvent.ReactionResolved(result.roll, result.disposition)))
        }
        is GameAction.CheckMorale -> {
            val result = morale.check(action.morale, action.failureOutcome)
            GameResult(state.copy(campaign = state.campaign.copy(turn = state.campaign.turn + 1)), listOf(GameEvent.MoraleResolved(result.roll, result.morale, result.outcome)))
        }
        is GameAction.HireHireling -> hireHireling(state, action)
        GameAction.PayHirelings -> payHirelings(state)
        is GameAction.CheckHirelingMorale -> checkHirelingMorale(state, action)
        is GameAction.RecordGrowthEvidence -> {
            val next = growth.recordEvidence(state, action.evidence)
            GameResult(next, listOf(GameEvent.GrowthEvidenceRecorded(action.evidence.id)))
        }
        is GameAction.RecordGrowthEvidenceProposal -> {
            val next = growth.recordProposal(state, action.proposal)
            GameResult(next, listOf(GameEvent.GrowthEvidenceRecorded(action.proposal.id)))
        }
        is GameAction.ApplyGrowth -> applyGrowth(state, action)
        is GameAction.ApplyGrowthChangeProposal -> applyGrowthChangeProposal(state, action)
        is GameAction.ApplyCanonProposals -> {
            val next = CanonResolver().apply(state, action.proposals)
            GameResult(next, if (action.proposals.isEmpty()) emptyList() else listOf(GameEvent.CanonUpdated(action.proposals.size)))
        }
        is GameAction.AdvanceFaction -> advanceFaction(state, action)
    }

    private fun openingNarration(world: WorldState): String {
        val settlement = world.settlements.first()
        val faction = world.factions.first()
        val dungeon = world.dungeons.firstOrNull()
        val rumor = world.rumors.firstOrNull()?.text ?: "Rumores contraditórios circulam entre os viajantes."
        val dungeonClue = dungeon?.let { " Ao longe, a entrada de ${it.name} aguarda quem se atrever a procurá-la." }
            ?: " A região oferece mais de um caminho, mas nenhum parece seguro."
        return "Você chega a ${settlement.name}, na região ${world.region.name}. " +
            "${settlement.description} ${world.region.description} " +
            "${faction.name} movimenta seus agentes para cumprir o plano de ${faction.agenda.lowercase()}. " +
            "$rumor$dungeonClue"
    }

    private fun advanceFaction(state: GameState, action: GameAction.AdvanceFaction): GameResult {
        require(action.amount > 0) { "Faction progress must be positive" }
        require(action.reason.isNotBlank() && action.reason.length <= 500) { "Faction progress requires a reason" }
        val world = state.campaign.worldState ?: error("World state not initialized")
        val faction = world.factions.firstOrNull { it.id == action.factionId } ?: error("Unknown faction: ${action.factionId}")
        val previous = faction.goalProgress
        val current = (previous + action.amount).coerceAtMost(faction.goals.size)
        val nextFaction = faction.copy(goalProgress = current)
        val nextWorld = world.copy(factions = world.factions.map { if (it.id == faction.id) nextFaction else it })
        val history = CampaignHistoryEntry(
            id = "faction-${state.campaign.turn}-${faction.id}",
            turn = state.campaign.turn,
            type = HistoryEventType.FACTION_UPDATED,
            summary = "${faction.name}: progresso ${previous}→${current}. ${action.reason}",
            source = HistorySource.RULES_ENGINE,
            relatedEntityIds = listOf(faction.id)
        )
        val next = state.copy(campaign = state.campaign.copy(
            worldState = nextWorld,
            history = (state.campaign.history + history).takeLast(500),
            turn = state.campaign.turn + 1
        ))
        return GameResult(next, listOf(GameEvent.FactionProgressChanged(faction.id, previous, current, faction.goals[current.coerceAtMost(faction.goals.lastIndex)])))
    }

    private fun applyGrowth(state: GameState, action: GameAction.ApplyGrowth): GameResult {
        val result = growth.apply(state, action.proposal)
        val next = state.copy(
            campaign = state.campaign.copy(
                rules = result.character,
                growth = result.growth,
                history = (state.campaign.history + result.history).takeLast(500),
                turn = state.campaign.turn + 1
            ),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        return GameResult(next, listOf(GameEvent.GrowthApplied(action.proposal.id)))
    }

    private fun applyGrowthChangeProposal(state: GameState, action: GameAction.ApplyGrowthChangeProposal): GameResult {
        val result = growth.applyProposal(state, action.proposal)
        val next = state.copy(
            campaign = state.campaign.copy(
                rules = result.character,
                growth = result.growth,
                history = (state.campaign.history + result.history).takeLast(500),
                turn = state.campaign.turn + 1
            ),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        return GameResult(next, listOf(GameEvent.GrowthApplied(action.proposal.id)))
    }

    private fun hireHireling(state: GameState, action: GameAction.HireHireling): GameResult {
        val entry = com.vanish994.cairnsolo.rules.MarketplaceCatalog.find(action.entryId) ?: error("Unknown Marketplace entry: ${action.entryId}")
        require(state.campaign.profile.gold >= entry.priceGp) { "Insufficient gold" }
        require(state.campaign.hirelings.none { it.id == action.hirelingId }) { "Hireling id already exists" }
        val hireling = hirelingRules.hire(entry, action.hirelingId, action.name, action.loyalty, action.morale)
        val gold = state.campaign.profile.gold - entry.priceGp
        val next = state.copy(campaign = state.campaign.copy(profile = state.campaign.profile.copy(gold = gold), hirelings = state.campaign.hirelings + hireling, turn = state.campaign.turn + 1))
        return GameResult(next, listOf(GameEvent.HirelingResolved(hireling.id, "HIRED", gold)))
    }

    private fun payHirelings(state: GameState): GameResult {
        val result = hirelingRules.payWages(state.campaign.hirelings, state.campaign.profile.gold)
        val next = state.copy(campaign = state.campaign.copy(profile = state.campaign.profile.copy(gold = result.goldRemaining), turn = state.campaign.turn + 1))
        return GameResult(next, result.events.map { GameEvent.HirelingResolved(it.hirelingId, "WAGES_PAID", result.goldRemaining) })
    }

    private fun checkHirelingMorale(state: GameState, action: GameAction.CheckHirelingMorale): GameResult {
        val hireling = state.campaign.hirelings.firstOrNull { it.id == action.hirelingId } ?: error("Unknown hireling: ${action.hirelingId}")
        val result = hirelingRules.checkMorale(hireling, action.failureOutcome)
        val nextHirelings = state.campaign.hirelings.map { if (it.id == hireling.id) result.hireling else it }
        val next = state.copy(campaign = state.campaign.copy(hirelings = nextHirelings, turn = state.campaign.turn + 1))
        return GameResult(next, listOf(GameEvent.MoraleResolved(result.result.roll, result.result.morale, result.result.outcome), GameEvent.HirelingResolved(hireling.id, result.result.outcome.name)))
    }

    private fun castSpell(state: GameState, action: GameAction.CastSpell): GameResult {
        val result = magic.cast(state.campaign.rules, action.itemId, action.requiresWilSave, action.failure, action.dropItemIdForFatigue)
        return GameResult(state.withRules(result.state), listOf(GameEvent.SpellResolved(result.spell.id, action.itemId)))
    }

    private fun purchase(state: GameState, action: GameAction.Purchase): GameResult {
        val entry = com.vanish994.cairnsolo.rules.MarketplaceCatalog.find(action.itemId) ?: error("Unknown Marketplace entry: ${action.itemId}")
        if (entry.item != null) require(state.campaign.rules.inventory.none { it.id == action.itemId }) { "Item id already exists in inventory" }
        val result = marketplace.purchase(state.campaign.rules, state.campaign.profile.gold, action.itemId)
        val profile = state.campaign.profile.copy(gold = result.goldRemaining)
        val recorded = state.withHistory("purchase-${state.campaign.turn}-${action.itemId}", HistoryEventType.ITEM_CHANGED, "Compra realizada: ${action.itemId}.", listOf(action.itemId))
        val resolved = recorded.withRules(result.state)
        val next = resolved.copy(campaign = resolved.campaign.copy(profile = profile))
        return GameResult(next, listOf(GameEvent.PurchaseResolved(action.itemId, result.goldRemaining)))
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

    private fun dungeonAct(state: GameState, action: DungeonAction): GameResult {
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
            return GameResult(next.withHistory("combat-start-${state.campaign.turn}", HistoryEventType.COMBAT_UPDATED, "Combate iniciado contra ${action.opponentId}.", listOf(action.opponentId)), listOf(GameEvent.CombatStarted(action.opponentId, 1, true), GameEvent.SaveResolved(Attribute.DEX, save.roll, true)))
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
        return GameResult(next.withHistory("combat-start-${state.campaign.turn}", HistoryEventType.COMBAT_UPDATED, "Combate iniciado contra ${action.opponentId}; o inimigo agiu primeiro.", listOf(action.opponentId)), events)
    }

    private fun combatAttack(state: GameState, action: GameAction.CombatAttack): GameResult {
        val current = state.campaign.combat ?: error("No active combat")
        require(current.playerCanAct) { "Player cannot act this round" }
        val playerAttack = combat.attack(state.campaign.rules, current.opponent, action.weapon, action.mode)
        val playerDamage = playerAttack.events.toDamageEvent(playerAttack.target)
        if (playerAttack.target.dead || playerAttack.target.hp == 0) {
            val next = state.copy(campaign = state.campaign.copy(combat = null, turn = state.campaign.turn + 1))
            return GameResult(next.withHistory("combat-victory-${state.campaign.turn}", HistoryEventType.COMBAT_UPDATED, "Combate vencido contra ${current.opponentId}.", listOf(current.opponentId)), listOf(GameEvent.CombatAttackResolved(current.opponentId, current.round, playerDamage, null, false), GameEvent.CombatEnded(current.opponentId, true)))
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
        return GameResult(next.withHistory("combat-round-${state.campaign.turn}", HistoryEventType.COMBAT_UPDATED, "Rodada ${current.round} resolvida contra ${current.opponentId}.", listOf(current.opponentId)), events)
    }

    private fun endCombat(state: GameState): GameResult {
        val current = state.campaign.combat ?: return GameResult(state, emptyList())
        return GameResult(state.copy(campaign = state.campaign.copy(combat = null, turn = state.campaign.turn + 1)).withHistory("combat-end-${state.campaign.turn}", HistoryEventType.COMBAT_UPDATED, "Combate encerrado contra ${current.opponentId}.", listOf(current.opponentId)), listOf(GameEvent.CombatEnded(current.opponentId, false)))
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

    private fun GameState.withHistory(id: String, type: HistoryEventType, summary: String, related: List<String> = emptyList()): GameState = copy(
        campaign = campaign.copy(history = (campaign.history + CampaignHistoryEntry(id, campaign.turn, type, summary, HistorySource.RULES_ENGINE, related)).takeLast(500)),
        updatedAtEpochMs = System.currentTimeMillis()
    )
}
