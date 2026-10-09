package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.game.GameResult
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CombatOpponentState
import com.vanish994.cairnsolo.game.CanonStatus
import com.vanish994.cairnsolo.game.isValidRewardId
import com.vanish994.cairnsolo.rules.Attribute
import com.vanish994.cairnsolo.rules.WeaponProfile
import com.vanish994.cairnsolo.rules.isSupportedWeaponDamageExpression

data class GuardianRuleResolution(
    val state: GameState,
    val resultText: String,
    val gameResult: GameResult,
    val encounterContexts: List<GuardianNarrativeOpponentContext> = emptyList()
)

class GuardianRuleResolver(
    private val actionResolver: GameActionResolver
) {
    fun validateAttackIntent(
        state: GameState,
        intent: GuardianActionIntent,
        request: GuardianRuleRequest?
    ): String? {
        val knownNpcIds = state.campaign.worldCanon.npcs
            .filter { it.status != CanonStatus.RUMOR }
            .map { it.id }
            .toSet()
        val activeCombat = state.campaign.combat
        if (activeCombat != null) {
            if (request != null) return "Um ataque em combate não pode vir acompanhado de outro pedido de regra."
            if (!activeCombat.playerCanAct) return "O personagem ainda não pode agir nesta rodada."
            if (activeCombat.opponents.none { it.id == intent.targetId && it.status == com.vanish994.cairnsolo.game.CombatOpponentStatus.ACTIVE }) {
                return "O alvo indicado não é um oponente ativo deste combate; nenhum ataque foi resolvido."
            }
        } else {
            if (intent.targetId !in knownNpcIds) return "O alvo não corresponde a um NPC conhecido; esclareça quem você pretende atacar."
            if (request == null || !request.type.equals("BEGIN_COMBAT", ignoreCase = true)) {
                return "O Guardião precisa propor o perfil do NPC para sua confirmação antes do primeiro ataque."
            }
            validationError(state, request)?.let { return it }
            val proposal = request.encounter ?: return "A proposta de combate está incompleta."
            if (proposal.opponents.none { it.opponentId == intent.targetId }) {
                return "A proposta de combate não contém o alvo indicado; nenhum combate foi iniciado."
            }
            if (proposal.opponents.any { it.opponentId !in knownNpcIds }) {
                return "A proposta inclui um oponente que ainda não existe no cânone conhecido; nenhum combate foi iniciado."
            }
        }
        intent.weaponId?.let { id ->
            if (id == "unarmed") return null
            val item = state.campaign.rules.inventory.firstOrNull { it.id == id }
                ?: return "A arma proposta não está no inventário; nenhum ataque foi resolvido."
            if (!isSupportedWeaponDamageExpression(item.damage)) {
                return "O item proposto não possui um dano de arma válido; nenhum ataque foi resolvido."
            }
        }
        return null
    }

    fun resolveAttackIntent(
        state: GameState,
        intent: GuardianActionIntent,
        request: GuardianRuleRequest? = null
    ): GuardianRuleResolution {
        val error = validateAttackIntent(state, intent, request)
        require(error == null) { error ?: "Invalid attack intent" }
        val weapon = attackWeapon(state, intent.weaponId)
        if (state.campaign.combat != null) {
            return resolve(state, GameAction.CombatAttack(intent.targetId, weapon))
        }

        val encounterRequest = requireNotNull(request)
        val started = resolve(state, encounterRequest)
        val activeCombat = started.state.campaign.combat
        if (activeCombat == null || !activeCombat.playerCanAct ||
            activeCombat.opponents.none { it.id == intent.targetId && it.status == com.vanish994.cairnsolo.game.CombatOpponentStatus.ACTIVE }
        ) return started

        val attack = resolve(started.state, GameAction.CombatAttack(intent.targetId, weapon))
        val combined = GameResult(attack.state, started.gameResult.events + attack.gameResult.events)
        return GuardianRuleResolution(
            state = attack.state,
            resultText = listOf(started.resultText, attack.resultText).filter { it.isNotBlank() }.joinToString("\n"),
            gameResult = combined,
            encounterContexts = attack.encounterContexts.ifEmpty { started.encounterContexts }
        )
    }

    private fun attackWeapon(state: GameState, weaponId: String?): WeaponProfile? {
        if (weaponId == null) return null
        if (weaponId == "unarmed") return WeaponProfile("unarmed", "d4")
        val item = requireNotNull(state.campaign.rules.inventory.firstOrNull { it.id == weaponId })
        return WeaponProfile(
            id = item.id,
            damage = item.damage,
            blast = item.tags.any { it.equals("BLAST", ignoreCase = true) },
            ranged = item.tags.any { it.equals("RANGED", ignoreCase = true) }
        )
    }

    fun validationError(state: GameState, request: GuardianRuleRequest): String? {
        val type = request.type.uppercase()
        when (type) {
            "SAVE" -> if (request.attribute?.uppercase() !in setOf("STR", "DEX", "WIL")) {
                return "SAVE precisa indicar o atributo STR, DEX ou WIL."
            }
            "DAMAGE", "FATIGUE" -> if (request.amount == null || request.amount < 1) {
                return "$type precisa indicar uma quantidade positiva."
            }
            "REST", "STABILIZE_CRITICAL", "RECOVER_SCAR" -> Unit
            "BEGIN_COMBAT" -> if (request.encounter == null) {
                return "BEGIN_COMBAT precisa trazer uma proposta completa de encontro."
            }
            "REWARD" -> {
                val reward = request.reward ?: return "REWARD precisa trazer uma proposta completa de recompensa."
                if (!isValidRewardId(reward.id)) return "REWARD precisa usar um ID válido."
                if (reward.amountGp < 0) return "REWARD não pode ter quantidade de GP negativa."
                if (reward.itemCatalogIds.size > 5) return "REWARD pode conter no máximo cinco itens."
                if (reward.itemCatalogIds.any { !isValidRewardId(it) }) return "REWARD contém um ID de catálogo inválido."
                if (reward.status == RewardStatus.PAID && reward.amountGp == 0 && reward.itemCatalogIds.isEmpty()) {
                    return "REWARD PAID precisa entregar GP ou ao menos um item."
                }
            }
            else -> return "Pedido de regra do Guardião não suportado: ${request.type}"
        }
        if (state.campaign.combat != null) {
            return when (type) {
                "DAMAGE" -> "Pedidos DAMAGE do Guardião não podem causar dano durante combate; use uma ação de ataque do motor de regras."
                "REST" -> "Pedidos REST não podem ser resolvidos durante combate ativo."
                "BEGIN_COMBAT" -> "Já existe um combate ativo."
                "REWARD" -> if (request.reward?.status == RewardStatus.PAID) "Pagamentos REWARD não podem ser aplicados durante combate ativo." else null
                else -> null
            }
        }
        return null
    }

    fun resolve(state: GameState, request: GuardianRuleRequest): GuardianRuleResolution {
        val validationError = validationError(state, request)
        require(validationError == null) { validationError ?: "Invalid Guardian rule request" }
        val action = when (request.type.uppercase()) {
            "SAVE" -> GameAction.Save(
                attribute = when (request.attribute?.uppercase()) {
                    "STR" -> Attribute.STR
                    "DEX" -> Attribute.DEX
                    "WIL" -> Attribute.WIL
                    else -> error("SAVE requires attribute STR, DEX or WIL")
                }
            )
            "DAMAGE" -> GameAction.ApplyDamage(
                amount = request.amount?.takeIf { it >= 1 }
                    ?: error("DAMAGE requires a positive amount")
            )
            "FATIGUE" -> GameAction.AddFatigue(
                amount = request.amount?.takeIf { it >= 1 }
                    ?: error("FATIGUE requires a positive amount")
            )
            "REST" -> GameAction.Rest
            "STABILIZE_CRITICAL" -> GameAction.StabilizeCritical
            "RECOVER_SCAR" -> GameAction.RecoverScar
            "BEGIN_COMBAT" -> {
                val encounter = request.encounter ?: error("BEGIN_COMBAT requires a complete encounter proposal")
                GameAction.BeginCombat(
                    opponents = encounter.opponents.map { proposal ->
                        CombatOpponentState(
                            id = proposal.opponentId,
                            narrative = proposal.narrative,
                            stats = proposal.stats,
                            weapon = proposal.weapon
                        )
                    },
                    moraleLeaderId = encounter.moraleLeaderId
                )
            }
            "REWARD" -> {
                val reward = request.reward ?: error("REWARD requires a complete reward proposal")
                if (reward.status == RewardStatus.OFFERED) {
                    return GuardianRuleResolution(
                        state = state,
                        resultText = "Recompensa apenas oferecida; nenhum efeito mecânico foi aplicado.",
                        gameResult = GameResult(state, emptyList())
                    )
                }
                GameAction.GrantReward(reward.id, reward.amountGp, reward.itemCatalogIds)
            }
            else -> error("Unsupported Guardian rule request: ${request.type}")
        }

        return resolve(state, action)
    }

    fun resolve(state: GameState, action: GameAction): GuardianRuleResolution {
        val encounterContexts = state.campaign.combat?.opponents
            ?.map { GuardianNarrativeOpponentContext(it.id, it.narrative) }
            ?: (action as? GameAction.BeginCombat)?.opponents
                ?.map { GuardianNarrativeOpponentContext(it.id, it.narrative) }
            ?: emptyList()
        val result = actionResolver.resolve(state, action)
        return GuardianRuleResolution(
            state = result.state,
            resultText = summarize(result.events),
            gameResult = result,
            encounterContexts = encounterContexts
        )
    }

    private fun summarize(events: List<GameEvent>): String {
        if (events.isEmpty()) return "Nenhum efeito mecânico foi aplicado."

        return events.joinToString(" ") { event ->
            when (event) {
                is GameEvent.SaveResolved ->
                    "Teste de ${event.attribute.name}: d20=${event.roll}; " +
                        (if (event.success) "sucesso" else "falha") + "."
                is GameEvent.DiceRollResolved ->
                    "Rolagem de dano (${if (event.purpose == com.vanish994.cairnsolo.game.RollPurpose.PLAYER_ATTACK_DAMAGE) "jogador" else event.actorLabel}): " +
                        event.dice.joinToString(", ") { "d${it.sides}=${it.result}" } + "."
                is GameEvent.DamageResolved ->
                    combatDamageFact("Dano", event, reportCharacterDeath = true)
                is GameEvent.CombatStarted ->
                    "Combate iniciado contra ${event.opponentIds.joinToString(", ")}, rodada ${event.round}; " +
                        (if (event.playerCanAct) "o jogador age primeiro." else "os oponentes agem primeiro.")
                is GameEvent.CombatAttackResolved -> buildList {
                    event.targetOpponentId?.let { add("Alvo do ataque do jogador: $it.") }
                    event.enemyAttackRolls.forEach { roll ->
                        add("Oponente ${roll.opponentId} rolou ${roll.damageRolled} de dano.")
                    }
                    event.damageDealtByPlayer?.let {
                        add(combatDamageFact("Dano causado pelo jogador ao oponente", it, reportCharacterDeath = false))
                    }
                    event.damageDealtByEnemies?.let { damage ->
                        val label = if (event.enemyAttackRolls.size <= 1) {
                            "Dano causado pelo oponente ao jogador"
                        } else {
                            "Dano causado pelo grupo inimigo ao jogador"
                        }
                        add(combatDamageFact(label, damage, reportCharacterDeath = true))
                    }
                    if (event.defeatedOpponentIds.isNotEmpty()) {
                        add("Oponentes derrotados: ${event.defeatedOpponentIds.joinToString(", ")}.")
                    }
                    if (event.fledOpponentIds.isNotEmpty()) {
                        add("Oponentes que fugiram: ${event.fledOpponentIds.joinToString(", ")}.")
                    }
                    event.moraleOutcomes.forEach { outcome ->
                        add("Moral de ${outcome.opponentId}: gatilho ${outcome.trigger}, rolagem ${outcome.roll}, " +
                            "atributo ${outcome.attributeValue}, ${if (outcome.fled) "fugiu" else "não fugiu"}.")
                    }
                }.joinToString(" ").ifBlank { "Ataque resolvido." }
                is GameEvent.CombatEnded ->
                    "Combate encerrado para ${event.opponentIds.joinToString(", ")}; motivo: ${event.reason}."
                is GameEvent.RestCompleted ->
                    "Descanso: recuperou ${event.hpRecovered} HP e ${event.fatigueRecovered} Fadiga."
                is GameEvent.GoldCredited ->
                    "Pagamento confirmado: +${event.amountGp} GP; saldo atual: ${event.newBalanceGp} GP."
                is GameEvent.RewardItemAdded ->
                    "Item de catálogo entregue: ${event.catalogItemId}."
                is GameEvent.RewardItemPending ->
                    "Item ${event.catalogItemId} está pendente; libere ${(event.slotsRequired - event.freeSlots).coerceAtLeast(0)} slot(s)."
                is GameEvent.RewardItemClaimed ->
                    "Item de recompensa resgatado: ${event.itemInstanceId}."
                is GameEvent.RewardItemRejected ->
                    "Item de recompensa não aplicado (${event.reason}): ${event.catalogItemId}."
                is GameEvent.FatigueAdded ->
                    "Fadiga aumentada em ${event.amount}."
                GameEvent.CriticalStabilized ->
                    "Estado crítico estabilizado."
                is GameEvent.ScarRecovered ->
                    "Cicatriz recuperada: ${event.scar}."
                else -> event.toString()
            }
        }
    }

    private fun combatDamageFact(
        label: String,
        damage: GameEvent.DamageResolved,
        reportCharacterDeath: Boolean
    ): String = buildString {
        append("$label: ${damage.rawDamage} bruto, ${damage.armorAbsorbed} absorvido pela armadura, ${damage.hpDamage} HP perdido.")
        if (damage.critical) append(" Resultado crítico.")
        if (reportCharacterDeath && damage.dead) append(" Personagem morto.")
        damage.scar?.let { append(" Cicatriz registrada: $it.") }
    }
}
