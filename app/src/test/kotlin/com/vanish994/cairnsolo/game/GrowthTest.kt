package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.Attribute
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.RulesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GrowthTest {
    private fun evidence(id: String, pattern: Boolean = false, risk: Boolean = false, unique: Boolean = false) = GrowthEvidence(
        id = id, summary = "Uma experiência que mudou a personagem.", turn = 1,
        focusedPattern = pattern, seriousRisk = risk, uniqueInteraction = unique
    )

    @Test
    fun resolverRequiresTwoTriggerConditionsAndPersistsAppliedGrowth() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val recorded = actions.resolve(base, GameAction.RecordGrowthEvidence(evidence("heartseed", unique = true, risk = true))).state
        val proposal = GrowthProposal("growth-heartseed", listOf("heartseed"), GrowthChange.RaiseMaxAttribute(Attribute.WIL), "A exposição e o risco alteraram sua vontade.")
        val applied = actions.resolve(recorded, GameAction.ApplyGrowth(proposal)).state
        assertEquals(11, applied.campaign.rules.wil)
        assertEquals(11, applied.campaign.rules.maxWil)
        assertEquals(listOf("growth-heartseed"), applied.campaign.growth.appliedProposalIds)
        assertEquals(HistoryEventType.GROWTH, applied.campaign.history.last().type)
        val restored = GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(applied))!!
        assertEquals(applied.campaign.growth, restored.campaign.growth)
        assertEquals(applied.campaign.history, restored.campaign.history)
    }

    @Test
    fun oneTriggerConditionCannotGrantGrowth() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val recorded = actions.resolve(base, GameAction.RecordGrowthEvidence(evidence("risk-only", risk = true))).state
        assertFailsWith<IllegalArgumentException> {
            actions.resolve(recorded, GameAction.ApplyGrowth(GrowthProposal("invalid-growth", listOf("risk-only"), GrowthChange.GainAbility("green-skin", "Pele Verde", "A pele se adapta à floresta."), "Uma única cena.")))
        }
    }

    @Test
    fun guardianEvidenceProposalIsValidatedAndGetsAuthoritativeTurn() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val proposal = GrowthEvidenceProposal("guardian-relic", "Mara tocou a relíquia desconhecida apesar do risco.", listOf("relic-1"), seriousRisk = true, uniqueInteraction = true)
        val result = actions.resolve(base, GameAction.RecordGrowthEvidenceProposal(proposal)).state
        val accepted = result.campaign.growth.evidence.single()
        assertEquals(1L, accepted.turn)
        assertEquals(listOf("relic-1"), accepted.relatedEntityIds)
        assertEquals(HistoryEventType.GROWTH, result.campaign.history.single().type)
    }

    @Test
    fun growthCanAddAnAbilityWithoutLettingGuardianChangeRulesDirectly() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val recorded = actions.resolve(base, GameAction.RecordGrowthEvidence(evidence("relic", pattern = true, unique = true))).state
        val applied = actions.resolve(recorded, GameAction.ApplyGrowth(GrowthProposal("relic-bond", listOf("relic"), GrowthChange.GainAbility("plant-speech", "Fala das Plantas", "Pode compreender sinais simples de plantas e animais.", "Requer contato com a floresta."), "A relação contínua com a relíquia."))).state
        assertEquals("plant-speech", applied.campaign.growth.abilities.single().id)
        assertEquals(10, applied.campaign.rules.wil)
    }

    @Test
    fun canonProposalsAreAppliedThroughTheActionResolver() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val proposal = CanonProposal.DiscoverLocation("moon-hill", "Moon Hill", "Uma colina coberta por névoa.", CanonStatus.DISCOVERED, CanonSource.GUARDIAN)
        val result = actions.resolve(base, GameAction.ApplyCanonProposals(listOf(proposal)))
        assertEquals("Moon Hill", result.state.campaign.worldCanon.locations.single().name)
        assertEquals(HistoryEventType.CANON_UPDATED, result.state.campaign.history.single().type)
    }
}
