package com.vanish994.cairnsolo.game

enum class SceneType { EXPLORATION, SOCIAL, COMBAT, MANAGEMENT }

data class SceneState(
    val id: String,
    val type: SceneType,
    val title: String,
    val description: String,
    val exits: List<String> = emptyList(),
    val turn: Long = 0L
)

data class CampaignLogEntry(
    val turn: Long,
    val text: String
)

data class CampaignRuntime(
    val scene: SceneState,
    val log: List<CampaignLogEntry> = emptyList()
) {
    fun advance(nextScene: SceneState, narration: String): CampaignRuntime =
        copy(
            scene = nextScene.copy(turn = scene.turn + 1),
            log = log + CampaignLogEntry(scene.turn + 1, narration)
        )
}
