package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.Attribute
import com.vanish994.cairnsolo.rules.CharacterState

/** Evidência ficcional registrada pelo domínio; o Guardião não pode inventá-la na proposta. */
data class GrowthEvidence(
    val id: String,
    val summary: String,
    val turn: Long,
    val relatedEntityIds: List<String> = emptyList(),
    val focusedPattern: Boolean = false,
    val seriousRisk: Boolean = false,
    val uniqueInteraction: Boolean = false
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{3,80}")))
        require(summary.isNotBlank() && summary.length <= 1000)
        require(turn >= 0)
        require(focusedPattern || seriousRisk || uniqueInteraction)
    }

    val triggerCount: Int get() = listOf(focusedPattern, seriousRisk, uniqueInteraction).count { it }
}

/** Contrato externo: não contém turno, pois o domínio atribui o turno autoritativo. */
data class GrowthEvidenceProposal(
    val id: String,
    val summary: String,
    val relatedEntityIds: List<String> = emptyList(),
    val focusedPattern: Boolean = false,
    val seriousRisk: Boolean = false,
    val uniqueInteraction: Boolean = false
)

/** Contrato externo: a mudança só é aplicada depois que o domínio valida as evidências. */
data class GrowthChangeProposal(
    val id: String,
    val evidenceIds: List<String>,
    val changeType: String,
    val attribute: String? = null,
    val amount: Int? = null,
    val candidate: Int? = null,
    val abilityId: String? = null,
    val abilityName: String? = null,
    val abilityDescription: String? = null,
    val abilityCost: String? = null,
    val rationale: String
) {
    fun toDomain(): GrowthProposal {
        val change = when (changeType.uppercase()) {
            "RAISE_MAX_ATTRIBUTE" -> GrowthChange.RaiseMaxAttribute(parseAttribute(), amount ?: 1)
            "KEEP_HIGHER_ATTRIBUTE" -> GrowthChange.KeepHigherAttribute(parseAttribute(), candidate ?: error("Growth candidate is required"))
            "GAIN_ABILITY" -> GrowthChange.GainAbility(
                abilityId ?: error("Ability id is required"),
                abilityName ?: error("Ability name is required"),
                abilityDescription ?: error("Ability description is required"),
                abilityCost
            )
            else -> error("Unknown Growth change type: $changeType")
        }
        return GrowthProposal(id, evidenceIds, change, rationale)
    }

    private fun parseAttribute(): Attribute = runCatching { Attribute.valueOf(attribute?.uppercase() ?: "") }
        .getOrElse { error("Growth attribute must be STR, DEX or WIL") }
}

sealed interface GrowthChange {
    data class RaiseMaxAttribute(val attribute: Attribute, val amount: Int = 1) : GrowthChange {
        init { require(amount > 0) }
    }
    data class KeepHigherAttribute(val attribute: Attribute, val candidate: Int) : GrowthChange {
        init { require(candidate in 3..18) }
    }
    data class GainAbility(val id: String, val name: String, val description: String, val cost: String? = null) : GrowthChange {
        init {
            require(id.matches(Regex("[a-z0-9-]{3,80}")))
            require(name.isNotBlank() && description.isNotBlank())
        }
    }
}

data class GrowthProposal(
    val id: String,
    val evidenceIds: List<String>,
    val change: GrowthChange,
    val rationale: String
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{3,80}")))
        require(evidenceIds.isNotEmpty())
        require(rationale.isNotBlank() && rationale.length <= 1000)
    }
}

data class GrowthAbility(
    val id: String,
    val name: String,
    val description: String,
    val cost: String? = null,
    val acquiredTurn: Long
)

data class GrowthState(
    val evidence: List<GrowthEvidence> = emptyList(),
    val appliedProposalIds: List<String> = emptyList(),
    val abilities: List<GrowthAbility> = emptyList()
)

data class GrowthResolution(
    val character: CharacterState,
    val growth: GrowthState,
    val history: CampaignHistoryEntry
)

class GrowthResolver {
    fun recordProposal(state: GameState, proposal: GrowthEvidenceProposal): GameState = recordEvidence(
        state,
        GrowthEvidence(
            id = proposal.id,
            summary = proposal.summary,
            turn = state.campaign.turn,
            relatedEntityIds = proposal.relatedEntityIds,
            focusedPattern = proposal.focusedPattern,
            seriousRisk = proposal.seriousRisk,
            uniqueInteraction = proposal.uniqueInteraction
        )
    )

    fun recordEvidence(state: GameState, evidence: GrowthEvidence): GameState {
        require(state.campaign.growth.evidence.none { it.id == evidence.id }) { "Growth evidence id already exists" }
        val nextGrowth = state.campaign.growth.copy(evidence = (state.campaign.growth.evidence + evidence).takeLast(500))
        val history = CampaignHistoryEntry(
            id = "growth-evidence-${evidence.id}", turn = state.campaign.turn,
            type = HistoryEventType.GROWTH, summary = "Evidência de Growth registrada: ${evidence.id}.",
            source = HistorySource.RULES_ENGINE, relatedEntityIds = evidence.relatedEntityIds
        )
        return state.copy(campaign = state.campaign.copy(growth = nextGrowth, history = (state.campaign.history + history).takeLast(500), turn = state.campaign.turn + 1))
    }

    fun applyProposal(state: GameState, proposal: GrowthChangeProposal): GrowthResolution = apply(state, proposal.toDomain())

    fun apply(state: GameState, proposal: GrowthProposal): GrowthResolution {
        require(proposal.id !in state.campaign.growth.appliedProposalIds) { "Growth proposal already applied" }
        val evidence = proposal.evidenceIds.map { id -> state.campaign.growth.evidence.firstOrNull { it.id == id } ?: error("Unknown Growth evidence: $id") }
        require(evidence.size >= 1 && evidence.sumOf { it.triggerCount } >= 2) { "Growth requires evidence of at least two trigger conditions" }
        val character = when (val change = proposal.change) {
            is GrowthChange.RaiseMaxAttribute -> raiseMaxAttribute(state.campaign.rules, change.attribute, change.amount)
            is GrowthChange.KeepHigherAttribute -> keepHigher(state.campaign.rules, change.attribute, change.candidate)
            is GrowthChange.GainAbility -> state.campaign.rules
        }
        val abilities = when (val change = proposal.change) {
            is GrowthChange.GainAbility -> state.campaign.growth.abilities + GrowthAbility(change.id, change.name, change.description, change.cost, state.campaign.turn)
            else -> state.campaign.growth.abilities
        }
        require(abilities.map { it.id }.distinct().size == abilities.size) { "Growth ability id already exists" }
        val nextGrowth = state.campaign.growth.copy(appliedProposalIds = (state.campaign.growth.appliedProposalIds + proposal.id).takeLast(500), abilities = abilities.takeLast(200))
        val history = CampaignHistoryEntry(
            id = "growth-${proposal.id}", turn = state.campaign.turn,
            type = HistoryEventType.GROWTH, summary = "Growth aplicado: ${proposal.id}.",
            source = HistorySource.RULES_ENGINE, relatedEntityIds = proposal.evidenceIds
        )
        return GrowthResolution(character, nextGrowth, history)
    }

    private fun raiseMaxAttribute(state: CharacterState, attribute: Attribute, amount: Int): CharacterState = when (attribute) {
        Attribute.STR -> state.copy(str = state.str + amount, maxStr = state.maxStr + amount)
        Attribute.DEX -> state.copy(dex = state.dex + amount, maxDex = state.maxDex + amount)
        Attribute.WIL -> state.copy(wil = state.wil + amount, maxWil = state.maxWil + amount)
    }

    private fun keepHigher(state: CharacterState, attribute: Attribute, candidate: Int): CharacterState = when (attribute) {
        Attribute.STR -> if (candidate > state.str) state.copy(str = candidate, maxStr = maxOf(state.maxStr, candidate)) else state
        Attribute.DEX -> if (candidate > state.dex) state.copy(dex = candidate, maxDex = maxOf(state.maxDex, candidate)) else state
        Attribute.WIL -> if (candidate > state.wil) state.copy(wil = candidate, maxWil = maxOf(state.maxWil, candidate)) else state
    }
}
