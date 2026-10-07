package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.Background
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.CharacterTraits
import java.util.UUID

data class CharacterIdentity(val id: String = UUID.randomUUID().toString(), val name: String) {
    init { require(name.isNotBlank()) }
}

data class CompanionState(val id: String, val hp: Int, val maxHp: Int, val armor: Int = 0, val str: Int = 0, val dex: Int = 0, val wil: Int = 0, val slots: Int = 0, val tags: Set<String> = emptySet())

data class CombatState(
    val opponentId: String,
    val opponent: com.vanish994.cairnsolo.rules.CharacterState,
    val opponentWeapon: com.vanish994.cairnsolo.rules.WeaponProfile = com.vanish994.cairnsolo.rules.WeaponProfile("unarmed", "d4"),
    val round: Int = 1,
    val playerCanAct: Boolean = true
)

data class CharacterProfile(
    val age: Int? = null,
    val background: Background? = null,
    val traits: CharacterTraits? = null,
    val gold: Int = 0,
    val bondRoll: Int? = null,
    val secondBondRoll: Int? = null,
    val omenRoll: Int? = null,
    val backgroundRolls: com.vanish994.cairnsolo.rules.BackgroundRolls? = null,
    val backgroundFeatures: List<String> = emptyList(),
    val companions: List<CompanionState> = emptyList()
) {
    init {
        require(age == null || age >= 1)
        require(gold >= 0)
        require(bondRoll == null || bondRoll in 1..20)
        require(secondBondRoll == null || secondBondRoll in 1..20)
        require(omenRoll == null || omenRoll in 1..20)
    }
}

const val DEFAULT_GUARDIAN_PROLOGUE =
    "A chuva cai sobre as pedras antigas enquanto você atravessa a estrada abandonada. Há três dias, nenhum viajante retorna desta região. Os moradores da última aldeia evitaram falar sobre o assunto. Agora, entre as árvores, uma luz aparece. Então um sino toca — uma única vez. Você chegou ao lugar onde sua história começa."

data class CampaignState(
    val campaignId: String = UUID.randomUUID().toString(),
    val character: CharacterIdentity,
    val rules: CharacterState,
    val profile: CharacterProfile = CharacterProfile(),
    val sceneId: String = "prologue",
    val turn: Long = 0L,
    val sceneType: SceneType = SceneType.EXPLORATION,
    val sceneTitle: String = "Prologue",
    val sceneDescription: String = "A estrada está silenciosa. O sino acabou de tocar.",
    val exits: List<String> = listOf("investigar a luz", "seguir pela estrada", "procurar abrigo"),
    val log: List<String> = emptyList(),
    val guardianMessage: String = DEFAULT_GUARDIAN_PROLOGUE,
    val guardianHistory: List<String> = emptyList(),
    val guardianInteractionId: String? = null,
    val combat: CombatState? = null,
    val worldState: WorldState? = null,
    val worldCanon: WorldCanon = WorldCanon(),
    val history: List<CampaignHistoryEntry> = emptyList()
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
            guardianMessage = narration,
            guardianHistory = (campaign.guardianHistory + narration).takeLast(20),
            log = (campaign.log + narration).takeLast(50)
        ),
        updatedAtEpochMs = System.currentTimeMillis()
    )

    fun recordGuardianIntent(intent: String): GameState {
        val clean = intent.trim()
        require(clean.isNotEmpty())
        val reply = "O Guardião escuta sua decisão: \"$clean\". A intenção foi registrada; a próxima cena será determinada pela narrativa e pelas regras do mundo."
        return copy(
            campaign = campaign.copy(
                guardianMessage = reply,
                guardianHistory = (campaign.guardianHistory + "Você: $clean" + reply).takeLast(20),
                log = (campaign.log + "Você declarou: $clean").takeLast(50),
                turn = campaign.turn + 1
            ),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun applyGuardianResponse(
        narration: String,
        sceneTitle: String,
        sceneDescription: String,
        interactionId: String? = null
    ): GameState {
        val clean = narration.trim()
        require(clean.isNotEmpty())
        return copy(
            campaign = campaign.copy(
                sceneTitle = sceneTitle.ifBlank { campaign.sceneTitle },
                sceneDescription = sceneDescription.ifBlank { campaign.sceneDescription },
                guardianMessage = clean,
                guardianHistory = if (campaign.guardianHistory.lastOrNull() == clean) {
                    campaign.guardianHistory
                } else {
                    (campaign.guardianHistory + clean).takeLast(20)
                },
                log = if (campaign.log.lastOrNull() == clean) {
                    campaign.log
                } else {
                    (campaign.log + clean).takeLast(50)
                },
                turn = campaign.turn + 1,
                guardianInteractionId = interactionId ?: campaign.guardianInteractionId
            ),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }
}

fun newCharacter(name: String, str: Int, dex: Int, wil: Int): GameState =
    GameState(
        campaign = CampaignState(
            character = CharacterIdentity(name = name),
            rules = CharacterState(str, dex, wil, 6, 6, 0)
        )
    )
