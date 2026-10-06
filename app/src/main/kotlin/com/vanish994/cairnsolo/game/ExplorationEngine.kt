package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.RandomSource

enum class ExplorationAction { CONTINUE, INVESTIGATE, REST }

sealed interface ExplorationEvent {
    data class Advanced(val destination: String) : ExplorationEvent
    data class Investigated(val detail: String) : ExplorationEvent
    data object RestRequested : ExplorationEvent
}

data class ExplorationResult(
    val state: GameState,
    val events: List<ExplorationEvent>
)

class ExplorationEngine(private val random: RandomSource) {
    fun resolve(state: GameState, action: ExplorationAction): ExplorationResult {
        return when (action) {
            ExplorationAction.CONTINUE -> {
                val destination = when (random.d6()) {
                    1, 2 -> "old_road"
                    3, 4 -> "woodland_edge"
                    5 -> "ruined_shrine"
                    else -> "watchtower"
                }
                val next = state.advanceScene(
                    id = destination,
                    type = SceneType.EXPLORATION,
                    title = destination.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    description = "Você chega a um novo ponto da jornada.",
                    exits = listOf("continuar", "investigar"),
                    narration = "Avançou para $destination."
                )
                ExplorationResult(next, listOf(ExplorationEvent.Advanced(destination)))
            }
            ExplorationAction.INVESTIGATE -> {
                val detail = when (random.d6()) {
                    1, 2 -> "Não encontra nada além de sinais antigos."
                    3, 4 -> "Encontra uma pista que merece atenção."
                    5 -> "Percebe um recurso útil no ambiente."
                    else -> "Descobre uma passagem ou detalhe oculto."
                }
                val next = state.advanceScene(
                    id = state.campaign.sceneId,
                    type = state.campaign.sceneType,
                    title = state.campaign.sceneTitle,
                    description = state.campaign.sceneDescription,
                    exits = state.campaign.exits,
                    narration = detail
                )
                ExplorationResult(next, listOf(ExplorationEvent.Investigated(detail)))
            }
            ExplorationAction.REST -> ExplorationResult(
                state,
                listOf(ExplorationEvent.RestRequested)
            )
        }
    }
}
