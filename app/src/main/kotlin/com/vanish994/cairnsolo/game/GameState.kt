package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.Background
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.CharacterTraits
import java.util.UUID

data class CharacterIdentity(val id: String = UUID.randomUUID().toString(), val name: String) {
    init { require(name.isNotBlank()) }
}

data class CharacterProfile(val age: Int? = null, val background: Background? = null, val traits: CharacterTraits? = null) { init { require(age == null || age >= 1) } }

data class CampaignState(
    val campaignId: String = UUID.randomUUID().toString(),
    val character: CharacterIdentity,
    val rules: CharacterState,
    val profile: CharacterProfile = CharacterProfile(),
    val sceneId: String = "prologue",
    val turn: Long = 0L,
    val sceneType: SceneType = SceneType.EXPLORATION,
    val sceneTitle: String = "Prologue",
    val sceneDescription: String = "A aventura começa.",
    val exits: List<String> = emptyList(),
    val log: List<String> = emptyList()
)

data class GameState(
    val campaign: CampaignState,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun withRules(newRules: CharacterState): GameState = copy(
        campaign = campaign.copy(rules = newRules, turn = campaign.turn + 1),
        updatedAtEpochMs = System.currentTimeMillis()
    )

    fun advanceScene(id: String, type: SceneType, title: String, description: String, exits: List<String> = emptyList(), narration: String = description): GameState = copy(
        campaign = campaign.copy(
            sceneId = id,
            sceneType = type,
            sceneTitle = title,
            sceneDescription = description,
            exits = exits,
            turn = campaign.turn + 1,
            log = (campaign.log + narration).takeLast(50)
        ),
        updatedAtEpochMs = System.currentTimeMillis()
    )
}

fun newCharacter(name: String, str: Int, dex: Int, wil: Int): GameState =
    GameState(
        campaign = CampaignState(
            character = CharacterIdentity(name = name),
            rules = CharacterState(str, dex, wil, 6, 6, 0)
        )
    )
