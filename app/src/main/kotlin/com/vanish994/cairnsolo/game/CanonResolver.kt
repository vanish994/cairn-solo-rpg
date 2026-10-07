package com.vanish994.cairnsolo.game

class CanonResolver {
    fun apply(state: GameState, proposals: List<CanonProposal>): GameState {
        if (proposals.isEmpty()) return state
        var canon = state.campaign.worldCanon
        val accepted = mutableListOf<CampaignHistoryEntry>()
        proposals.take(5).forEach { proposal ->
            require(proposal.id.matches(Regex("[a-z0-9-]{3,80}"))) { "ID de cânone inválido: ${proposal.id}" }
            when (proposal) {
                is CanonProposal.UpsertNpc -> {
                    require(proposal.name.isNotBlank() && proposal.name.length <= 160)
                    val existing = canon.npcs.firstOrNull { it.id == proposal.id }
                    val next = CanonNpc(proposal.id, proposal.name, proposal.role, proposal.description, proposal.status, existing?.firstSeenTurn ?: state.campaign.turn)
                    canon = canon.copy(npcs = (canon.npcs.filterNot { it.id == proposal.id } + next).takeLast(200))
                }
                is CanonProposal.DiscoverLocation -> {
                    require(proposal.name.isNotBlank() && proposal.description.length <= 1000)
                    val existing = canon.locations.firstOrNull { it.id == proposal.id }
                    val next = CanonLocation(proposal.id, proposal.name, proposal.description, proposal.status, existing?.firstSeenTurn ?: state.campaign.turn)
                    canon = canon.copy(locations = (canon.locations.filterNot { it.id == proposal.id } + next).takeLast(200))
                }
                is CanonProposal.AddImportantItem -> {
                    require(proposal.name.isNotBlank() && proposal.name.length <= 160)
                    val existing = canon.importantItems.firstOrNull { it.id == proposal.id }
                    val next = CanonItem(proposal.id, proposal.name, proposal.description, proposal.status, existing?.firstSeenTurn ?: state.campaign.turn)
                    canon = canon.copy(importantItems = (canon.importantItems.filterNot { it.id == proposal.id } + next).takeLast(200))
                }
                is CanonProposal.CreateQuest -> {
                    require(proposal.name.isNotBlank() && proposal.description.length <= 1000)
                    val existing = canon.quests.firstOrNull { it.id == proposal.id }
                    val next = CanonQuest(proposal.id, proposal.name, proposal.description, "ACTIVE", existing?.firstSeenTurn ?: state.campaign.turn)
                    canon = canon.copy(quests = (canon.quests.filterNot { it.id == proposal.id } + next).takeLast(100))
                }
                is CanonProposal.AddDiscovery -> {
                    require(proposal.text.isNotBlank() && proposal.text.length <= 1000)
                    if (canon.discoveries.none { it.id == proposal.id }) {
                        canon = canon.copy(discoveries = (canon.discoveries + CanonDiscovery(proposal.id, proposal.text, proposal.status, proposal.source, state.campaign.turn)).takeLast(300))
                    }
                }
            }
            accepted += CampaignHistoryEntry(
                id = "canon-${state.campaign.turn}-${proposal.id}",
                turn = state.campaign.turn,
                type = HistoryEventType.CANON_UPDATED,
                summary = "Cânone atualizado: ${proposal.id}.",
                source = when (proposal.source) {
                    CanonSource.PLAYER -> HistorySource.PLAYER
                    CanonSource.GUARDIAN -> HistorySource.GUARDIAN
                    CanonSource.RULES_ENGINE -> HistorySource.RULES_ENGINE
                    CanonSource.NPC, CanonSource.SYSTEM -> HistorySource.SYSTEM
                },
                relatedEntityIds = listOf(proposal.id) + proposal.relatedEntityIds
            )
        }
        return state.copy(
            campaign = state.campaign.copy(
                worldCanon = canon,
                history = (state.campaign.history + accepted).takeLast(500)
            ),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }
}
