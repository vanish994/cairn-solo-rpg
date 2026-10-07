package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WardenRulesTest {
    @Test
    fun reactionUsesCairnTwoD6Bands() {
        assertEquals(ReactionDisposition.ATTACK, ReactionRules(FixedRandomSource(10, d6Value = 1)).roll().disposition)
        assertEquals(ReactionDisposition.HELPFUL, ReactionRules(FixedRandomSource(10, d6Value = 6)).roll().disposition)
    }

    @Test
    fun moraleStandsOnOrBelowTargetAndRetreatsAboveIt() {
        val rules = MoraleRules(FixedRandomSource(10, d6Value = 3))
        assertEquals(MoraleOutcome.STAND, rules.check(6).outcome)
        val failure = MoraleRules(FixedRandomSource(10, d6Value = 6)).check(7)
        assertEquals(12, failure.roll)
        assertEquals(MoraleOutcome.RETREAT, failure.outcome)
    }

    @Test
    fun hirelingPaymentAndMoraleAreDeterministic() {
        val entry = MarketplaceCatalog.find("hireling-scholar")!!
        val rules = HirelingRules(MoraleRules(FixedRandomSource(10, d6Value = 6)))
        val scholar = rules.hire(entry, "h1", "Iria")
        val paid = rules.payWages(listOf(scholar), 25)
        assertEquals(5, paid.goldRemaining)
        val morale = rules.checkMorale(scholar)
        assertEquals(MoraleOutcome.RETREAT, morale.result.outcome)
        assertFalse(morale.hireling.active)
        assertTrue(paid.events.single().amountGp == 20)
    }
}
