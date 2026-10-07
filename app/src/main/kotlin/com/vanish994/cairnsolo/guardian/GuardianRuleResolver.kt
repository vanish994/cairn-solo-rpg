package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.game.GameResult
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.rules.Attribute

data class GuardianRuleResolution(
    val state: GameState,
    val resultText: String,
    val gameResult: GameResult
)

class GuardianRuleResolver(
    private val actionResolver: GameActionResolver
) {
    fun resolve(state: GameState, request: GuardianRuleRequest): GuardianRuleResolution {
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
            else -> error("Unsupported Guardian rule request: ${request.type}")
        }

        val result = actionResolver.resolve(state, action)
        return GuardianRuleResolution(
            state = result.state,
            resultText = summarize(result.events),
            gameResult = result
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
}
