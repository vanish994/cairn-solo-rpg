package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.Background
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.CharacterTraits
import com.vanish994.cairnsolo.rules.DowntimeState
import com.vanish994.cairnsolo.rules.DungeonState
import com.vanish994.cairnsolo.rules.WildernessState
import com.vanish994.cairnsolo.rules.HirelingState
import com.vanish994.cairnsolo.rules.isSupportedWeaponDamageExpression
import java.util.UUID

data class CharacterIdentity(val id: String = UUID.randomUUID().toString(), val name: String) {
    init { require(name.isNotBlank()) }
}

data class CompanionState(val id: String, val hp: Int, val maxHp: Int, val armor: Int = 0, val str: Int = 0, val dex: Int = 0, val wil: Int = 0, val slots: Int = 0, val tags: Set<String> = emptySet())

data class CombatOpponentNarrative(
    val name: String,
    val appearance: String = "",
    val behavior: String = "",
    val intent: String = "",
    val context: String = ""
) {
    init {
        require(name.isNotBlank())
        require(name.length <= 160)
        require(appearance.length <= 1000)
        require(behavior.length <= 1000)
        require(intent.length <= 1000)
        require(context.length <= 1000)
    }
}

enum class CombatOpponentStatus { ACTIVE, DEFEATED, FLED }

enum class CombatMoraleTrigger { SINGLE_OPPONENT_ZERO_HP, FIRST_CASUALTY, HALF_GROUP }

enum class CombatEndReason {
    OPPONENTS_DEFEATED,
    OPPONENTS_FLED,
    OPPONENTS_DEFEATED_AND_FLED,
    PLAYER_DEFEATED
}

data class CombatOpponentState(
    val id: String,
    val narrative: CombatOpponentNarrative,
    val stats: CharacterState,
    val weapon: com.vanish994.cairnsolo.rules.WeaponProfile,
    val status: CombatOpponentStatus = CombatOpponentStatus.ACTIVE
) {
    init {
        require(id.isNotBlank() && id == id.trim() && id.length <= 80) { "Combat opponent id is invalid." }
        require(stats.maxHp > 0 && stats.hp in 0..stats.maxHp) { "Combat opponent HP is invalid." }
        require(weapon.id.isNotBlank() && weapon.id == weapon.id.trim() && weapon.id.length <= 80) {
            "Combat opponent weapon id is invalid."
        }
        require(weapon.damage.isNullOrBlank() || isSupportedWeaponDamageExpression(weapon.damage)) {
            "Dado de dano da arma do oponente incompatível com as regras de combate."
        }
    }
}

data class CombatState(
    val opponents: List<CombatOpponentState>,
    val moraleLeaderId: String? = null,
    val resolvedMoraleTriggers: Set<CombatMoraleTrigger> = emptySet(),
    val round: Int = 1,
    val playerCanAct: Boolean = true
) {
    init {
        require(opponents.size in 1..8) { "Combat must contain between one and eight opponents." }
        require(opponents.map { it.id }.distinct().size == opponents.size) { "Combat opponent ids must be unique." }
        require(opponents.any { it.status == CombatOpponentStatus.ACTIVE }) { "Combat must have at least one active opponent." }
        require(moraleLeaderId == null || opponents.any { it.id == moraleLeaderId }) {
            "The morale leader must belong to the encounter."
        }
    }

    /** Temporary single-opponent adapter for the existing UI, persistence codec, and tests. */
    constructor(
        opponentId: String,
        opponent: CharacterState,
        opponentWeapon: com.vanish994.cairnsolo.rules.WeaponProfile = com.vanish994.cairnsolo.rules.WeaponProfile("unarmed", "d4"),
        round: Int = 1,
        playerCanAct: Boolean = true,
        opponentNarrative: CombatOpponentNarrative = CombatOpponentNarrative(opponentId)
    ) : this(
        opponents = listOf(CombatOpponentState(opponentId, opponentNarrative, opponent, opponentWeapon)),
        round = round,
        playerCanAct = playerCanAct
    )

    val opponentId: String get() = opponents.first().id
    val opponent: CharacterState get() = opponents.first().stats
    val opponentWeapon: com.vanish994.cairnsolo.rules.WeaponProfile get() = opponents.first().weapon
    val opponentNarrative: CombatOpponentNarrative get() = opponents.first().narrative

    /** Temporary adapter for the existing single-opponent combat turn flow. */
    fun copy(
        opponent: CharacterState,
        round: Int = this.round,
        playerCanAct: Boolean = this.playerCanAct
    ): CombatState = copy(
        opponents = opponents.mapIndexed { index, current ->
            if (index == 0) current.copy(stats = opponent) else current
        },
        round = round,
        playerCanAct = playerCanAct
    )
}

/** Intenção do jogador e perfil proposto, persistidos juntos enquanto aguardam aprovação. */
data class PendingCombatApproval(
    val actionId: String,
    val targetOpponentId: String,
    val targetName: String,
    val weaponId: String?,
    val opponents: List<CombatOpponentState>,
    val moraleLeaderId: String? = null
) {
    init {
        require(actionId.isNotBlank() && actionId.length <= 120)
        require(targetOpponentId.isNotBlank() && targetOpponentId == targetOpponentId.trim())
        require(targetName.isNotBlank() && targetName.length <= 160)
        require(opponents.size in 1..8 && opponents.map { it.id }.distinct().size == opponents.size)
        require(opponents.any { it.id == targetOpponentId && it.status == CombatOpponentStatus.ACTIVE })
        require(moraleLeaderId == null || opponents.any { it.id == moraleLeaderId })
        require(weaponId == null || (weaponId.isNotBlank() && weaponId == weaponId.trim()))
    }
}

/** NPCs relevantes são criados sob demanda; perfil nulo significa que ainda não foram preparados para combate. */
data class CampaignNpcState(
    val id: String,
    val name: String,
    val role: String? = null,
    val description: String? = null,
    val locationId: String? = null,
    val combatProfile: CombatOpponentState? = null
) {
    init {
        require(id.isNotBlank() && id == id.trim() && id.length <= 80)
        require(name.isNotBlank() && name.length <= 160)
        require(role == null || role.length <= 160)
        require(description == null || description.length <= 1000)
        require(locationId == null || (locationId.isNotBlank() && locationId.length <= 80))
        require(combatProfile == null || combatProfile.id == id)
    }
}

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

private val REWARD_ID_REGEX = Regex("^[a-z0-9-]{3,80}$")

fun isValidRewardId(value: String): Boolean = REWARD_ID_REGEX.matches(value)

data class PendingRewardItem(
    val id: String,
    val rewardId: String,
    val catalogItemId: String,
    val itemInstanceId: String
) {
    init {
        require(id.isNotBlank() && id.length <= 200)
        require(isValidRewardId(rewardId))
        require(isValidRewardId(catalogItemId))
        require(itemInstanceId.isNotBlank() && itemInstanceId.length <= 200)
    }
}

const val DEFAULT_GUARDIAN_PROLOGUE =
    "Esta campanha ainda não recebeu sua primeira narração. Use a identidade do aventureiro, a seed e o contexto do mundo desta campanha para apresentar uma situação inédita, concreta e aberta às escolhas do jogador. Não peça novamente informações já fornecidas. A IA conduz a narrativa; o Rules Engine controla toda consequência mecânica."

data class CampaignState(
    val campaignId: String = UUID.randomUUID().toString(),
    val campaignSeed: String = UUID.randomUUID().toString(),
    val character: CharacterIdentity,
    val rules: CharacterState,
    val profile: CharacterProfile = CharacterProfile(),
    val sceneId: String = "prologue",
    val turn: Long = 0L,
    val sceneType: SceneType = SceneType.EXPLORATION,
    val sceneTitle: String = "Quem é você?",
    val sceneDescription: String = "Defina o papel do seu aventureiro na história. O mundo reagirá àquilo que você trouxer para ele.",
    val exits: List<String> = emptyList(),
    val log: List<String> = emptyList(),
    val guardianMessage: String = DEFAULT_GUARDIAN_PROLOGUE,
    val guardianHistory: List<String> = emptyList(),
    val guardianInteractionId: String? = null,
    val combat: CombatState? = null,
    val dungeon: DungeonState? = null,
    val wilderness: WildernessState? = null,
    val downtime: DowntimeState = DowntimeState(),
    val hirelings: List<HirelingState> = emptyList(),
    val growth: GrowthState = GrowthState(),
    val worldState: WorldState? = null,
    val worldCanon: WorldCanon = WorldCanon(),
    val history: List<CampaignHistoryEntry> = emptyList(),
    val appliedRewardIds: Set<String> = emptySet(),
    val pendingRewardItems: List<PendingRewardItem> = emptyList(),
    val pendingCombatApproval: PendingCombatApproval? = null,
    val resolvedCombatActionIds: Set<String> = emptySet(),
    val knownNpcs: List<CampaignNpcState> = emptyList()
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
            guardianHistory = (campaign.guardianHistory + narration).takeLast(200),
            log = (campaign.log + narration).takeLast(50)
        ),
        updatedAtEpochMs = System.currentTimeMillis()
    )

    fun recordGuardianIntent(intent: String): GameState {
        val clean = intent.trim()
        require(clean.isNotEmpty())
        return copy(
            campaign = campaign.copy(
                guardianMessage = "Você: $clean",
                guardianHistory = (campaign.guardianHistory + "Você: $clean").takeLast(200),
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
                    (campaign.guardianHistory + clean).takeLast(200)
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
