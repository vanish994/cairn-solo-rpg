package com.vanish994.cairnsolo.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RewardPersistenceTest {
    @Test
    fun pendingAndAppliedRewardIdsRoundTrip() {
        val base = newCharacter("Mara", 10, 10, 10)
        val pending = PendingRewardItem(
            id = "quest-pack:pending:0",
            rewardId = "quest-pack",
            catalogItemId = "chainmail",
            itemInstanceId = "reward:quest-pack:0:chainmail"
        )
        val original = base.copy(campaign = base.campaign.copy(
            appliedRewardIds = setOf("quest-pay:grant", "quest-pay:gold", "quest-pack:item:0"),
            pendingRewardItems = listOf(pending)
        ))

        val restored = assertNotNull(GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(original)))

        assertEquals(original.campaign.appliedRewardIds, restored.campaign.appliedRewardIds)
        assertEquals(original.campaign.pendingRewardItems, restored.campaign.pendingRewardItems)
    }

    @Test
    fun legacySaveDefaultsRewardCollectionsToEmpty() {
        val base = newCharacter("Mara", 10, 10, 10)
        val values = GameStatePersistenceCodec.encode(base).toMutableMap().apply {
            remove("appliedRewardCount")
            remove("pendingRewardCount")
            keys.filter { it.startsWith("appliedReward_") || it.startsWith("pendingReward_") }
                .toList().forEach(::remove)
        }

        val restored = assertNotNull(GameStatePersistenceCodec.decode(values))

        assertEquals(emptySet(), restored.campaign.appliedRewardIds)
        assertEquals(emptyList(), restored.campaign.pendingRewardItems)
    }
}
