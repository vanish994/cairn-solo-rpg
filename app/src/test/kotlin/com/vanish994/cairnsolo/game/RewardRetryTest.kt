package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.RulesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewardRetryTest {
    private fun state(inventory: List<InventoryItem> = emptyList(), gold: Int = 0): GameState {
        val base = newCharacter("Mara", 10, 10, 10)
        return base.copy(campaign = base.campaign.copy(
            profile = CharacterProfile(gold = gold),
            rules = base.campaign.rules.copy(inventory = inventory)
        ))
    }

    private fun resolver(): GameActionResolver {
        val random = FixedRandomSource(d20Value = 10)
        return GameActionResolver(ExplorationEngine(random), RulesEngine(random))
    }

    @Test
    fun grantRewardWithSameIdAsGoldActionIsNoOp() {
        val resolver = resolver()
        val paid = resolver.resolve(state(), GameAction.AddGold("quest-pay", 12))
        val changedPayload = resolver.resolve(paid.state, GameAction.GrantReward("quest-pay", 12, listOf("dagger")))

        assertEquals(paid.state, changedPayload.state)
        assertTrue(changedPayload.events.isEmpty())
    }

    @Test
    fun addGoldWithSameIdAsRewardPackageIsNoOp() {
        val resolver = resolver()
        val paid = resolver.resolve(state(), GameAction.GrantReward("quest-pay", 0, listOf("dagger")))
        val changedPayload = resolver.resolve(paid.state, GameAction.AddGold("quest-pay", 12))

        assertEquals(paid.state, changedPayload.state)
        assertTrue(changedPayload.events.isEmpty())
    }

    @Test
    fun claimPendingRewardItemIsIdempotentAfterRetry() {
        val resolver = resolver()
        val initial = state(inventory = List(9) { InventoryItem("existing-$it") })
        val paid = resolver.resolve(initial, GameAction.GrantReward("quest-pack", 0, listOf("chainmail")))
        val pending = paid.state.campaign.pendingRewardItems.single()
        val freed = resolver.resolve(paid.state, GameAction.RemoveItem("existing-0"))
        val claimed = resolver.resolve(freed.state, GameAction.ClaimPendingRewardItem(pending.id))
        val retry = resolver.resolve(claimed.state, GameAction.ClaimPendingRewardItem(pending.id))

        assertEquals(claimed.state, retry.state)
        assertTrue(retry.events.isEmpty())
    }

    @Test
    fun rewardThatFillsTenthSlotAppliesCairn2eZeroHpRule() {
        val initial = state(inventory = List(9) { InventoryItem("existing-$it") })

        val result = resolver().resolve(initial, GameAction.GrantReward("quest-last-slot", 0, listOf("dagger")))
        val character = result.state.campaign.rules

        assertEquals(10, character.usedSlots)
        assertEquals(0, character.hp)
        assertTrue(character.inventory.any { it.id == "reward:quest-last-slot:0:dagger" })
        assertTrue(result.state.campaign.pendingRewardItems.isEmpty())
    }
}
