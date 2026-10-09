package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.RulesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampaignOpeningTest {
    private fun resolver(): GameActionResolver {
        val random = FixedRandomSource(d20Value = 10, d6Value = 1)
        return GameActionResolver(
            exploration = ExplorationEngine(random),
            rules = RulesEngine(random)
        )
    }

    @Test
    fun successfulOpeningKeepsSuggestedActionsAndNeverCreditsPaidReward() {
        val initial = newCharacter("Mara", 10, 12, 9)
        val response = GuardianResponse(
            narration = "Uma corda recém-cortada balança sobre o vau.",
            sceneTitle = "O vau de pedra",
            sceneDescription = "A água escura cobre parte da travessia.",
            ruleRequest = GuardianRuleRequest(
                type = "REWARD",
                reward = GuardianRewardProposal(
                    id = "opening-payment",
                    status = RewardStatus.PAID,
                    amountGp = 12,
                    itemCatalogIds = emptyList()
                )
            ),
            suggestedActions = listOf("Examinar a corda", "Observar a margem oposta"),
            interactionId = "opening-interaction"
        )

        val outcome = resolveCampaignOpening(initial, Result.success(response), resolver())

        assertEquals(response.narration, outcome.state.campaign.guardianMessage)
        assertEquals(response.sceneTitle, outcome.state.campaign.sceneTitle)
        assertEquals(response.suggestedActions, outcome.suggestedActions)
        assertEquals("opening-interaction", outcome.state.campaign.guardianInteractionId)
        assertEquals(initial.campaign.profile.gold, outcome.state.campaign.profile.gold)
        assertEquals(initial.campaign.rules.hp, outcome.state.campaign.rules.hp)
        assertNull(outcome.error)
    }

    @Test
    fun failedOpeningPreservesPlayableLocalCampaignAndReportsError() {
        val initial = newCharacter("Mara", 10, 12, 9)

        val outcome = resolveCampaignOpening(
            initial,
            Result.failure(IllegalStateException("Guardian indisponível")),
            resolver()
        )

        assertEquals(initial, outcome.state)
        assertTrue(outcome.suggestedActions.isEmpty())
        assertEquals("Guardian indisponível", outcome.error)
    }
}
