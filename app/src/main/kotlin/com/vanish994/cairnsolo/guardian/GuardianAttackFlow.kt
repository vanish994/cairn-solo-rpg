package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.PendingCombatApproval

sealed interface GuardianAttackFlowResult {
    val state: GameState

    data class Rejected(
        override val state: GameState,
        val message: String
    ) : GuardianAttackFlowResult

    data class AwaitingApproval(
        override val state: GameState,
        val approval: PendingCombatApproval,
        val intent: GuardianActionIntent
    ) : GuardianAttackFlowResult

    data class Resolved(
        val resolution: GuardianRuleResolution
    ) : GuardianAttackFlowResult {
        override val state: GameState get() = resolution.state
    }
}

/**
 * Coordinates a player-declared attack without allowing pre-resolution Guardian prose to
 * become campaign narration. The caller may persist narrative canon first; this flow only
 * validates the mechanical hand-off and either resolves it or stores the exact pending action.
 */
class GuardianAttackFlow(
    private val ruleResolver: GuardianRuleResolver
) {
    companion object {
        private val directAttack = Regex(
            "^(?:eu\\s+)?(?:ataco|golpeio|bato|esfaqueio|disparo|arremesso|apunhalo|firo)\\b",
            RegexOption.IGNORE_CASE
        )
        private val intendedAttack = Regex(
            "^(?:eu\\s+)?(?:quero|vou|tento|tentarei|pretendo|decido|decidi)\\s+(?:atacar|golpear|bater|esfaquear|disparar|arremessar|apunhalar|ferir)\\b",
            RegexOption.IGNORE_CASE
        )

        /** Conservative fail-closed fallback for clear positive Portuguese attack declarations. */
        fun isExplicitAttackDeclaration(text: String): Boolean =
            directAttack.containsMatchIn(text.trim()) || intendedAttack.containsMatchIn(text.trim())
    }

    fun prepare(
        state: GameState,
        intent: GuardianActionIntent,
        request: GuardianRuleRequest?,
        actionId: String
    ): GuardianAttackFlowResult {
        val validationError = ruleResolver.validateAttackIntent(state, intent, request)
        if (validationError != null) return GuardianAttackFlowResult.Rejected(state, validationError)

        val activeCombat = state.campaign.combat != null
        val knownProfile = state.campaign.knownNpcs.any {
            it.id == intent.targetId && it.combatProfile != null
        }
        if (activeCombat || knownProfile) {
            return runCatching<GuardianAttackFlowResult> {
                GuardianAttackFlowResult.Resolved(ruleResolver.resolveAttackIntent(state, intent, request))
            }.getOrElse { error ->
                GuardianAttackFlowResult.Rejected(
                    state,
                    error.message ?: "Não foi possível resolver o ataque; nenhuma consequência narrativa foi aplicada."
                )
            }
        }

        val approval = ruleResolver.pendingCombatApproval(state, intent, request, actionId)
            ?: return GuardianAttackFlowResult.Rejected(
                state,
                "Não foi possível preparar um perfil mecânico completo para este alvo; nenhuma rolagem ou efeito foi aplicado."
            )
        val awaiting = state.copy(
            campaign = state.campaign.copy(pendingCombatApproval = approval)
        )
        return GuardianAttackFlowResult.AwaitingApproval(awaiting, approval, intent)
    }

    /** Recusal is mechanical no-op: preserve campaign/NPC facts and discard only the proposal. */
    fun reject(state: GameState): GameState {
        if (state.campaign.pendingCombatApproval == null) return state
        return state.copy(campaign = state.campaign.copy(pendingCombatApproval = null))
    }
}
