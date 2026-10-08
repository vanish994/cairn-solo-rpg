package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.RulesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GuardianRewardRuleTest {
    private fun rulesResolver(): GuardianRuleResolver {
        val random = FixedRandomSource(d20Value = 10)
        return GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
    }

    private fun request(status: RewardStatus, amountGp: Int = 12, items: List<String> = listOf("dagger")) =
        GuardianRuleRequest("REWARD", reward = GuardianRewardProposal("quest-pay", status, amountGp, items))

    @Test
    fun offeredRewardDoesNotChangeGameState() {
        val state = newCharacter("Mara", 10, 11, 12)
        val result = rulesResolver().resolve(state, request(RewardStatus.OFFERED))

        assertEquals(state, result.state)
        assertTrue(result.gameResult.events.isEmpty())
        assertTrue(result.resultText.contains("nenhum efeito mecânico"))
        assertFalse("quest-pay:grant" in result.state.campaign.appliedRewardIds)
    }

    @Test
    fun paidRewardDispatchesGoldAndItemsThroughResolver() {
        val initial = newCharacter("Mara", 10, 11, 12)
        val result = rulesResolver().resolve(initial, request(RewardStatus.PAID))

        assertEquals(12, result.state.campaign.profile.gold)
        assertEquals(initial.campaign.turn + 1, result.state.campaign.turn)
        assertEquals("reward:quest-pay:0:dagger", result.state.campaign.rules.inventory.single().id)
        assertTrue(result.gameResult.events.any { it is GameEvent.GoldCredited })
        assertTrue(result.gameResult.events.any { it is GameEvent.RewardItemAdded })
    }

    @Test
    fun offeredRewardCanBecomePaidWithSameId() {
        val rules = rulesResolver()
        val initial = newCharacter("Mara", 10, 11, 12)
        val offered = rules.resolve(initial, request(RewardStatus.OFFERED))
        val paid = rules.resolve(offered.state, request(RewardStatus.PAID))
        val replay = rules.resolve(paid.state, request(RewardStatus.PAID))

        assertEquals(12, paid.state.campaign.profile.gold)
        assertEquals(paid.state, replay.state)
        assertTrue(replay.gameResult.events.isEmpty())
    }

    @Test
    fun invalidRewardIdIsRejectedBeforeMutation() {
        val state = newCharacter("Mara", 10, 11, 12)
        val invalid = GuardianRuleRequest(
            "REWARD",
            reward = GuardianRewardProposal("bad/id", RewardStatus.PAID, 12, emptyList())
        )

        assertNotNull(rulesResolver().validationError(state, invalid))
        assertEquals(0, state.campaign.profile.gold)
    }

    @Test
    fun unknownCatalogItemDoesNotBlockPaidGold() {
        val state = newCharacter("Mara", 10, 11, 12)
        val result = rulesResolver().resolve(state, request(RewardStatus.PAID, items = listOf("unknown-relic")))

        assertEquals(12, result.state.campaign.profile.gold)
        assertTrue(result.gameResult.events.any {
            it == GameEvent.RewardItemRejected("unknown-relic", "UNKNOWN_CATALOG_ITEM")
        })
    }
}
