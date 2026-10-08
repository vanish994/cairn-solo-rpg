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

data class CombatState(
    val opponentId: String,
    val opponent: com.vanish994.cairnsolo.rules.CharacterState,
    val opponentWeapon: com.vanish994.cairnsolo.rules.WeaponProfile = com.vanish994.cairnsolo.rules.WeaponProfile("unarmed", "d4"),
    val round: Int = 1,
    val playerCanAct: Boolean = true,
    val opponentNarrative: CombatOpponentNarrative = CombatOpponentNarrative(opponentId)
) {
    init {
        require(opponentWeapon.damage.isNullOrBlank() || isSupportedWeaponDamageExpression(opponentWeapon.damage)) {
            "Dado de dano da arma do oponente incompatível com as regras de combate."
        }
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

const val DEFAULT_GUARDIAN_PROLOGUE =
    "Você é o Guardião desta campanha de Cairn. Esta aventura nasce da seed desta campanha e deve ser inédita, coerente e aberta à agência do jogador. Antes de revelar o mundo, peça ao jogador uma breve ideia de quem é seu aventureiro — profissão, passado, propósito, crença ou apenas um arquétipo. Não peça uma ficha detalhada. Use essa resposta junto da seed para construir uma situação inicial concreta, com lugar, atmosfera, conflito, mistério ou oportunidade, sem escrever uma história fechada e sem obrigar o personagem a uma ação. A partir daí, conduza a campanha como um Guardião: mantenha continuidade, faça o mundo reagir às escolhas e apresente consequências naturais. Nunca invente testes, dano, HP, condições, itens ou outros resultados mecânicos; quando uma intenção exigir uma regra, solicite a resolução ao Rules Engine e narre somente os fatos autorizados por ele."

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
