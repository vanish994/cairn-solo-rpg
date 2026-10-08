package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.game.GameResult
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.game.CombatOpponentState
import com.vanish994.cairnsolo.rules.Attribute

data class GuardianRuleResolution(
    val state: GameState,
    val resultText: String,
    val gameResult: GameResult,
    val encounterNarratives: List<CombatOpponentNarrative> = emptyList()
) {
    /** Temporary singular adapter for MainActivity until Task 3 consumes the full list. */
    val encounterNarrative: CombatOpponentNarrative? get() = encounterNarratives.firstOrNull()
}

class GuardianRuleResolver(
    private val actionResolver: GameActionResolver
) {
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
            else -> return "Pedido de regra do Guardião não suportado: ${request.type}"
        }
        if (state.campaign.combat != null) {
            return when (type) {
                "DAMAGE" -> "Pedidos DAMAGE do Guardião não podem causar dano durante combate; use uma ação de ataque do motor de regras."
                "REST" -> "Pedidos REST não podem ser resolvidos durante combate ativo."
                "BEGIN_COMBAT" -> "Já existe um combate ativo."
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
            else -> error("Unsupported Guardian rule request: ${request.type}")
        }

        return resolve(state, action)
    }

    fun resolve(state: GameState, action: GameAction): GuardianRuleResolution {
        val result = actionResolver.resolve(state, action)
        val encounterNarratives = state.campaign.combat?.opponents?.map { it.narrative }
            ?: (action as? GameAction.BeginCombat)?.opponents?.map { it.narrative }
            ?: emptyList()
        return GuardianRuleResolution(
            state = result.state,
            resultText = summarize(result.events),
            gameResult = result,
            encounterNarratives = encounterNarratives
        )
    }

    private fun summarize(events: List<GameEvent>): String {
        if (events.isEmpty()) return "Nenhum efeito mecânico foi aplicado."

        return events.joinToString(" ") { event ->
            when (event) {
                is GameEvent.SaveResolved ->
                    "Teste de ${event.attribute.name}: d20=${event.roll}; " +
                        (if (event.success) "sucesso" else "falha") + "."
                is GameEvent.DamageResolved ->
                    "Dano: ${event.rawDamage} bruto, ${event.armorAbsorbed} absorvido pela armadura, ${event.hpDamage} aplicado ao HP."
                is GameEvent.CombatStarted ->
                    "Combate iniciado contra ${event.opponentId}, rodada ${event.round}; " +
                        (if (event.playerCanAct) "o jogador age primeiro." else "o oponente age primeiro.")
                is GameEvent.CombatAttackResolved -> listOfNotNull(
                    event.damageDealtByPlayer?.let { combatDamageFact("Dano causado pelo jogador ao oponente", it) },
                    event.damageDealtByOpponent?.let { combatDamageFact("Dano causado pelo oponente ao jogador", it) }
                ).joinToString(" ").ifBlank { "Ataque resolvido contra ${event.opponentId}." }
                is GameEvent.CombatEnded ->
                    if (event.victory) "O combate contra ${event.opponentId} terminou com vitória do jogador."
                    else "O combate contra ${event.opponentId} terminou sem vitória do jogador."
                is GameEvent.RestCompleted ->
                    "Descanso: recuperou ${event.hpRecovered} HP e ${event.fatigueRecovered} Fadiga."
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

    private fun combatDamageFact(label: String, damage: GameEvent.DamageResolved): String = buildString {
        append("$label: ${damage.rawDamage} bruto, ${damage.armorAbsorbed} absorvido pela armadura, ${damage.hpDamage} HP perdido.")
        if (damage.critical) append(" Resultado crítico.")
        if (damage.dead) append(" Alvo morto.")
        damage.scar?.let { append(" Cicatriz registrada: $it.") }
    }
}
