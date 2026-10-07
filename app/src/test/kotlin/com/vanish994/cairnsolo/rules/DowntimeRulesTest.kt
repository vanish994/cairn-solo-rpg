package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DowntimeRulesTest {
    private val character = CharacterState(10, 10, 10, 6, 6, 0)
    private fun state(
        safe: Boolean = true,
        recovery: Boolean = false,
        gold: Int = 0,
        reputation: Int = 0,
        resources: Set<String> = emptySet(),
        milestones: List<Milestone> = emptyList()
    ) = DowntimeState(safe, recovery, gold, reputation, resources, milestones)

    private fun research(milestoneId: String? = null, save: Boolean = false) = DowntimeAction(
        DowntimeActionType.RESEARCH,
        question = "Where is the lost temple?",
        source = "Temple of Puppets",
        milestoneId = milestoneId,
        attemptWilSave = save
    )

    @Test
    fun downtimeRequiresSafetyAndNoRecovery() {
        val rules = DowntimeRules(RulesEngine(FixedRandomSource(1)))
        assertFailsWith<IllegalArgumentException> { rules.perform(state(safe = false), character, research()) }
        assertFailsWith<IllegalArgumentException> { rules.perform(state(recovery = true), character, research()) }
    }

    @Test
    fun researchRequiresQuestionAndSource() {
        val rules = DowntimeRules(RulesEngine(FixedRandomSource(1)))
        assertFailsWith<IllegalArgumentException> {
            rules.perform(state(), character, DowntimeAction(DowntimeActionType.RESEARCH, question = "", source = "archive"))
        }
        assertFailsWith<IllegalArgumentException> {
            rules.perform(state(), character, DowntimeAction(DowntimeActionType.RESEARCH, question = "Who knows?"))
        }
    }

    @Test
    fun trainingRequiresPreciseGoalAndMaster() {
        val rules = DowntimeRules(RulesEngine(FixedRandomSource(1)))
        assertFailsWith<IllegalArgumentException> {
            rules.perform(state(), character, DowntimeAction(DowntimeActionType.TRAINING, trainingGoal = "Herbology"))
        }
        val result = rules.perform(
            state(), character,
            DowntimeAction(DowntimeActionType.TRAINING, trainingGoal = "Herbology", master = "Elder herbalist")
        )
        assertIs<DowntimeEvent.ActionCompleted>(result.events.last())
    }

    @Test
    fun milestoneProgressesOneStepPerActionAndCompletesAtTotal() {
        val milestone = Milestone("temple", "Find the lost temple", total = 2)
        val rules = DowntimeRules(RulesEngine(FixedRandomSource(1)))
        val withMilestone = rules.addMilestone(state(), milestone)
        val first = rules.perform(withMilestone, character, research("temple"))
        assertEquals(1, first.state.milestones.single().progress)
        assertTrue(first.events.none { it is DowntimeEvent.MilestoneCompleted })
        val second = rules.perform(first.state, character, research("temple"))
        assertEquals(2, second.state.milestones.single().progress)
        assertTrue(second.events.any { it is DowntimeEvent.MilestoneCompleted })
        assertEquals(2, second.state.completedActions)
    }

    @Test
    fun goldCostIsPaidForEachMilestoneAndResourceCostIsRemoved() {
        val milestone = Milestone("sage", "Train with sage", 1, cost = DowntimeCost.Gold(5))
        val rules = DowntimeRules(RulesEngine(FixedRandomSource(1)))
        val result = rules.perform(
            state(gold = 7, milestones = listOf(milestone)), character,
            DowntimeAction(DowntimeActionType.TRAINING, trainingGoal = "Languages", master = "Sage", milestoneId = "sage")
        )
        assertEquals(2, result.state.gold)
        assertIs<DowntimeEvent.CostPaid>(result.events.first())

        val resourceMilestone = Milestone("herbs", "Collect herbs", 1, cost = DowntimeCost.Resource("rare-herb"))
        val resourceResult = rules.perform(
            state(resources = setOf("rare-herb"), milestones = listOf(resourceMilestone)), character,
            research("herbs")
        )
        assertTrue(resourceResult.state.resources.isEmpty())
    }

    @Test
    fun successfulWilSaveCanAvoidMilestoneCost() {
        val milestone = Milestone("access", "Enter the archive", 1, cost = DowntimeCost.Gold(10))
        val result = DowntimeRules(RulesEngine(FixedRandomSource(1))).perform(
            state(gold = 0, milestones = listOf(milestone)), character, research("access", save = true)
        )
        assertEquals(0, result.state.gold)
        assertIs<DowntimeEvent.CostAvoided>(result.events.first())
        assertTrue(result.save?.success == true)
    }
}
