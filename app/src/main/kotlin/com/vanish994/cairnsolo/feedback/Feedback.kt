package com.vanish994.cairnsolo.feedback

import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.rules.Attribute
import com.vanish994.cairnsolo.rules.MarketplaceCatalog

enum class FeedbackType {
    INFO, SUCCESS, WARNING, FAILURE, DAMAGE, CRITICAL, INVENTORY
}

data class FeedbackEntry(
    val id: String,
    val message: String,
    val type: FeedbackType,
    val turn: Long
)

object FeedbackMapper {
    fun map(event: GameEvent, turn: Long): FeedbackEntry {
        require(turn >= 0)
        val mapped = when (event) {
            is GameEvent.CharacterCreated -> "Personagem criado." to FeedbackType.SUCCESS
            is GameEvent.SceneAdvanced -> "Você avançou para " + event.sceneId.toDisplayName() + "." to FeedbackType.INFO
            is GameEvent.SceneInvestigated -> event.detail to FeedbackType.INFO
            is GameEvent.RestCompleted -> restMessage(event) to FeedbackType.SUCCESS
            is GameEvent.DamageResolved -> damageMessage(event) to if (event.critical || event.scar != null) FeedbackType.CRITICAL else FeedbackType.DAMAGE
            is GameEvent.ItemAdded -> "Item adicionado: " + event.itemId.toDisplayName() + "." to FeedbackType.INVENTORY
            is GameEvent.ItemRemoved -> "Item removido: " + event.itemId.toDisplayName() + "." to FeedbackType.INVENTORY
            is GameEvent.FatigueAdded -> "Você ganhou " + event.amount + " de fadiga." to FeedbackType.WARNING
            is GameEvent.DeprivationChanged -> if (event.deprived) "Você está privado." to FeedbackType.WARNING else "A privação foi removida." to FeedbackType.SUCCESS
            is GameEvent.SaveResolved -> saveMessage(event.attribute, event.roll, event.success) to if (event.success) FeedbackType.SUCCESS else FeedbackType.FAILURE
            GameEvent.CriticalStabilized -> "A condição crítica foi estabilizada." to FeedbackType.SUCCESS
            is GameEvent.ScarRecovered -> "Cicatriz recuperada: " + event.scar.toDisplayName() + "." to FeedbackType.SUCCESS
            is GameEvent.CombatStarted -> "Combate iniciado contra ${event.opponentId}; rodada ${event.round}." to FeedbackType.INFO
            is GameEvent.CombatAttackResolved -> "Rodada ${event.round}: ataque e contra-ataque resolvidos." to FeedbackType.DAMAGE
            is GameEvent.CombatEnded -> if (event.victory) "Combate vencido contra ${event.opponentId}." to FeedbackType.SUCCESS else "Combate encerrado." to FeedbackType.WARNING
            is GameEvent.SpellResolved -> "Magia lançada: ${event.spellId}." to FeedbackType.INFO
            is GameEvent.PurchaseResolved -> "Compra concluída: ${event.itemId}. Ouro restante: ${event.goldRemaining}." to FeedbackType.INVENTORY
            is GameEvent.GoldCredited -> "Você recebeu ${event.amountGp} po. Saldo: ${event.newBalanceGp} po." to FeedbackType.SUCCESS
            is GameEvent.RewardItemAdded -> "Item recebido: ${catalogItemName(event.catalogItemId)}." to FeedbackType.INVENTORY
            is GameEvent.RewardItemPending -> {
                val needed = (event.slotsRequired - event.freeSlots).coerceAtLeast(0)
                "${catalogItemName(event.catalogItemId)} aguarda resgate; libere $needed ${if (needed == 1) "espaço" else "espaços"}." to FeedbackType.WARNING
            }
            is GameEvent.RewardItemClaimed -> "Item de recompensa resgatado: ${catalogItemName(event.itemInstanceId.substringAfterLast(':'))}." to FeedbackType.SUCCESS
            is GameEvent.RewardItemRejected -> {
                val reason = when (event.reason) {
                    "UNKNOWN_CATALOG_ITEM" -> "não existe no catálogo"
                    "NOT_AN_INVENTORY_ITEM" -> "não pode ser carregado no inventário"
                    else -> "não pôde ser concedido"
                }
                "Recompensa recusada: ${event.catalogItemId.toDisplayName()} $reason." to FeedbackType.WARNING
            }
            is GameEvent.DowntimeResolved -> "Downtime concluído: ${event.action.name.lowercase()}." to FeedbackType.SUCCESS
            is GameEvent.WildernessResolved -> "Ação de viagem resolvida: ${event.action.name.lowercase()}." to FeedbackType.INFO
            is GameEvent.DungeonResolved -> "Ação de dungeon resolvida: ${event.action.name.lowercase()}." to FeedbackType.INFO
            is GameEvent.RuleNotice -> event.summary to FeedbackType.INFO
            is GameEvent.ReactionResolved -> "Reação resolvida: ${event.disposition.name.lowercase()} (2d6=${event.roll})." to FeedbackType.INFO
            is GameEvent.MoraleResolved -> "Moral resolvida: ${event.outcome.name.lowercase()} (2d6=${event.roll}/${event.morale})." to if (event.outcome.name == "STAND") FeedbackType.SUCCESS else FeedbackType.WARNING
            is GameEvent.HirelingResolved -> "Hireling ${event.hirelingId}: ${event.outcome.lowercase()}." to if (event.outcome == "HIRED") FeedbackType.SUCCESS else FeedbackType.INFO
            is GameEvent.GrowthEvidenceRecorded -> "Experiência significativa registrada: ${event.evidenceId}." to FeedbackType.INFO
            is GameEvent.GrowthProposalPending -> "Uma proposta de Growth aguarda sua decisão." to FeedbackType.INFO
            is GameEvent.GrowthProposalDecided -> if (event.accepted) "Decisão de Growth registrada." to FeedbackType.INFO else "Proposta de Growth recusada." to FeedbackType.INFO
            is GameEvent.GrowthApplied -> "Growth aplicado: ${event.proposalId}." to FeedbackType.SUCCESS
            is GameEvent.CanonUpdated -> "Cânone atualizado com ${event.count} proposta(s)." to FeedbackType.INFO
            is GameEvent.FactionProgressChanged -> "Facção ${event.factionId}: progresso ${event.previous} → ${event.current}." to FeedbackType.INFO
        }
        return FeedbackEntry(id = idFor(event, turn), message = mapped.first, type = mapped.second, turn = turn)
    }

    fun mapAll(events: List<GameEvent>, turn: Long): List<FeedbackEntry> =
        events.mapIndexed { index, event -> map(event, turn).copy(id = map(event, turn).id + ":" + index) }

    private fun restMessage(event: GameEvent.RestCompleted): String {
        val parts = buildList {
            if (event.hpRecovered > 0) add("+" + event.hpRecovered + " HP")
            if (event.fatigueRecovered > 0) add("-" + event.fatigueRecovered + " fadiga")
        }
        return if (parts.isEmpty()) "Descanso concluído, sem recuperação." else "Descanso concluído: " + parts.joinToString(", ") + "."
    }

    private fun damageMessage(event: GameEvent.DamageResolved): String {
        val damage = "Você sofreu " + event.hpDamage + " de dano"
        val armor = if (event.armorAbsorbed > 0) " (" + event.armorAbsorbed + " absorvido pela armadura)" else ""
        val suffix = when {
            event.dead -> " Você não pode continuar."
            event.critical && event.scar != null -> " Dano crítico: " + event.scar.toDisplayName() + "."
            event.critical -> " Dano crítico."
            event.scar != null -> " Cicatriz: " + event.scar.toDisplayName() + "."
            else -> ""
        }
        return damage + armor + "." + suffix
    }

    private fun saveMessage(attribute: Attribute, roll: Int, success: Boolean): String =
        "Teste de " + attribute.displayName() + ": " + roll + " — " + (if (success) "sucesso" else "falha") + "."

    private fun catalogItemName(catalogItemId: String): String =
        MarketplaceCatalog.find(catalogItemId)?.name ?: catalogItemId.toDisplayName()

    private fun idFor(event: GameEvent, turn: Long): String =
        turn.toString() + ":" + event::class.simpleName + ":" + event.hashCode()

    private fun Attribute.displayName(): String = when (this) {
        Attribute.STR -> "FOR"
        Attribute.DEX -> "DES"
        Attribute.WIL -> "VON"
    }

    private fun String.toDisplayName(): String =
        lowercase().replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }
}
