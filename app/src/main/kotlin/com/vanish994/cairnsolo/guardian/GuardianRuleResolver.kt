package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.game.GameResult
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CombatOpponentState
import com.vanish994.cairnsolo.rules.Attribute

data class GuardianRuleResolution(
    val state: GameState,
    val resultText: String,
    val gameResult: GameResult,
    val encounterContexts: List<GuardianNarrativeOpponentContext> = emptyList()
)

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
