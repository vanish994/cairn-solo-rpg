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
                    title = destinationTitle(destination),
                    description = "Você chega a um novo ponto da jornada.",
                    exits = listOf("continuar", "investigar"),
                    narration = destinationNarration(destination)
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


private fun destinationTitle(destination: String): String =
    when (destination) {
        "old_road" -> "Estrada Antiga"
        "woodland_edge" -> "Limite da Floresta"
        "ruined_shrine" -> "Santuário em Ruínas"
        "watchtower" -> "Torre de Vigia"
        else -> destination.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

private fun destinationNarration(destination: String): String =
    when (destination) {
        "old_road" -> "A estrada antiga se estende à sua frente, silenciosa e quase esquecida."
        "woodland_edge" -> "Você alcança o limite da floresta. As árvores fecham-se adiante."
        "ruined_shrine" -> "Entre as árvores, surgem as pedras de um santuário em ruínas."
        "watchtower" -> "Você alcança a Torre de Vigia. A construção domina a paisagem à frente."
        else -> "Você avança para um novo ponto da jornada."
    }
