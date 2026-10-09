package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameState

internal data class CampaignOpeningOutcome(
    val state: GameState,
    val suggestedActions: List<String>,
    val error: String?
)

/** Applies narrative/canon only; an opening response can never resolve mechanics or Growth. */
internal fun resolveCampaignOpening(
    initialState: GameState,
    result: Result<GuardianResponse>,
    actionResolver: GameActionResolver
): CampaignOpeningOutcome = result.fold(
    onSuccess = { response ->
        val narrated = initialState.applyGuardianResponse(
            narration = response.narration,
            sceneTitle = response.sceneTitle,
            sceneDescription = response.sceneDescription,
            interactionId = response.interactionId
        )
        val next = runCatching {
            actionResolver.resolve(
                narrated,
                GameAction.ApplyCanonProposals(response.canonProposals)
            ).state
        }.getOrElse { narrated }
        CampaignOpeningOutcome(
            state = next,
            suggestedActions = response.suggestedActions,
            error = null
        )
    },
    onFailure = { error ->
        CampaignOpeningOutcome(
            state = initialState,
            suggestedActions = emptyList(),
            error = error.message ?: "Não foi possível falar com o Guardião."
        )
    }
)
