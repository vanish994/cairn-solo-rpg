package com.vanish994.cairnsolo.game

enum class CanonStatus { CONFIRMED, RUMOR, DISCOVERED }
enum class CanonSource { PLAYER, GUARDIAN, NPC, RULES_ENGINE, SYSTEM }

data class CanonLocation(
    val id: String,
    val name: String,
    val description: String,
    val status: CanonStatus = CanonStatus.DISCOVERED,
    val firstSeenTurn: Long = 0L
)

data class CanonNpc(
    val id: String,
    val name: String,
    val role: String? = null,
    val description: String? = null,
    val status: CanonStatus = CanonStatus.CONFIRMED,
    val firstSeenTurn: Long = 0L
)

data class CanonItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: CanonStatus = CanonStatus.DISCOVERED,
    val firstSeenTurn: Long = 0L
)

data class CanonQuest(
    val id: String,
    val title: String,
    val description: String,
    val status: String = "ACTIVE",
    val firstSeenTurn: Long = 0L
)

data class CanonDiscovery(
    val id: String,
    val text: String,
    val status: CanonStatus = CanonStatus.CONFIRMED,
    val source: CanonSource = CanonSource.GUARDIAN,
    val turn: Long = 0L
)

data class WorldCanon(
    val locations: List<CanonLocation> = emptyList(),
    val npcs: List<CanonNpc> = emptyList(),
    val importantItems: List<CanonItem> = emptyList(),
    val quests: List<CanonQuest> = emptyList(),
    val discoveries: List<CanonDiscovery> = emptyList()
)

enum class HistoryEventType { PLAYER_ACTION, SCENE_CHANGED, ROLL_RESOLVED, DAMAGE_APPLIED, ITEM_CHANGED, REST_COMPLETED, GROWTH, CANON_UPDATED, NARRATION }
enum class HistorySource { PLAYER, RULES_ENGINE, GUARDIAN, SYSTEM }

data class CampaignHistoryEntry(
    val id: String,
    val turn: Long,
    val type: HistoryEventType,
    val summary: String,
    val source: HistorySource,
    val relatedEntityIds: List<String> = emptyList()
)

sealed interface CanonProposal {
    val id: String
    val status: CanonStatus
    val source: CanonSource
    val relatedEntityIds: List<String>

    data class UpsertNpc(
        override val id: String, val name: String, val role: String? = null,
        val description: String? = null, override val status: CanonStatus,
        override val source: CanonSource, override val relatedEntityIds: List<String> = emptyList()
    ) : CanonProposal
    data class DiscoverLocation(
        override val id: String, val name: String, val description: String,
        override val status: CanonStatus, override val source: CanonSource,
        override val relatedEntityIds: List<String> = emptyList()
    ) : CanonProposal
    data class AddImportantItem(
        override val id: String, val name: String, val description: String? = null,
        override val status: CanonStatus, override val source: CanonSource,
        override val relatedEntityIds: List<String> = emptyList()
    ) : CanonProposal
    data class CreateQuest(
        override val id: String, val name: String, val description: String,
        override val status: CanonStatus, override val source: CanonSource,
        override val relatedEntityIds: List<String> = emptyList()
    ) : CanonProposal
    data class AddDiscovery(
        override val id: String, val text: String,
        override val status: CanonStatus, override val source: CanonSource,
        override val relatedEntityIds: List<String> = emptyList()
    ) : CanonProposal
}
