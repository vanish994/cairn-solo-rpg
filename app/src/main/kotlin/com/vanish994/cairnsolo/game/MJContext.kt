package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState

data class MJContext(
    val campaignId: String,
    val characterId: String,
    val characterName: String,
    val profile: CharacterProfile,
    val sceneId: String,
    val sceneType: SceneType,
    val sceneTitle: String,
    val sceneDescription: String,
    val exits: List<String>,
    val turn: Long,
    val rules: CharacterState,
    val recentLog: List<String>
) {
    companion object {
        fun from(state: GameState, recentEntries: Int = 10): MJContext {
            require(recentEntries >= 0)
            val c = state.campaign
            return MJContext(
                campaignId = c.campaignId,
                characterId = c.character.id,
                characterName = c.character.name,
                profile = c.profile,
                sceneId = c.sceneId,
                sceneType = c.sceneType,
                sceneTitle = c.sceneTitle,
                sceneDescription = c.sceneDescription,
                exits = c.exits,
                turn = c.turn,
                rules = c.rules,
                recentLog = c.log.takeLast(recentEntries)
            )
        }
    }
}
