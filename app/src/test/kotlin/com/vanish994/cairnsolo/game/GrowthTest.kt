package com.vanish994.cairnsolo.game

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
        val proposal = GrowthChangeProposal("growth-heartseed", listOf("heartseed"), "RAISE_MAX_ATTRIBUTE", attribute = "WIL", amount = 1, rationale = "A exposição e o risco alteraram sua vontade.")
        val staged = actions.resolve(recorded, GameAction.RecordGrowthChangeProposal(proposal)).state
        assertEquals(10, staged.campaign.rules.wil)
        assertEquals(listOf(proposal), staged.campaign.growth.pendingChangeProposals)
        val restoredPending = GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(staged))!!
        assertEquals(staged.campaign.growth, restoredPending.campaign.growth)
        val applied = actions.resolve(restoredPending, GameAction.DecideGrowthChangeProposal(proposal.id, accepted = true)).state
        assertEquals(11, applied.campaign.rules.wil)
        assertEquals(11, applied.campaign.rules.maxWil)
        assertEquals(listOf("growth-heartseed"), applied.campaign.growth.appliedProposalIds)
        assertEquals(emptyList(), applied.campaign.growth.pendingChangeProposals)
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
            actions.resolve(recorded, GameAction.RecordGrowthChangeProposal(GrowthChangeProposal("invalid-growth", listOf("risk-only"), "GAIN_ABILITY", abilityId = "green-skin", abilityName = "Pele Verde", abilityDescription = "A pele se adapta à floresta.", rationale = "Uma única cena.")))
        }
    }

    @Test
    fun guardianEvidenceProposalIsValidatedAndGetsAuthoritativeTurn() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val proposal = GrowthEvidenceProposal("guardian-relic", "Mara tocou a relíquia desconhecida apesar do risco.", listOf("relic-1"), seriousRisk = true, uniqueInteraction = true)
        val result = actions.resolve(base, GameAction.RecordGrowthEvidenceProposal(proposal)).state
        val accepted = result.campaign.growth.evidence.single()
        assertEquals(0L, accepted.turn)
        assertEquals(listOf("relic-1"), accepted.relatedEntityIds)
        assertEquals(HistoryEventType.GROWTH, result.campaign.history.single().type)
    }

    @Test
    fun growthCanAddAnAbilityWithoutLettingGuardianChangeRulesDirectly() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val recorded = actions.resolve(base, GameAction.RecordGrowthEvidence(evidence("relic", pattern = true, unique = true))).state
        val proposal = GrowthChangeProposal("relic-bond", listOf("relic"), "GAIN_ABILITY", abilityId = "plant-speech", abilityName = "Fala das Plantas", abilityDescription = "Pode compreender sinais simples de plantas e animais.", abilityCost = "Requer contato com a floresta.", rationale = "A relação contínua com a relíquia.")
        val staged = actions.resolve(recorded, GameAction.RecordGrowthChangeProposal(proposal)).state
        assertEquals(emptyList(), staged.campaign.growth.abilities)
        val applied = actions.resolve(staged, GameAction.DecideGrowthChangeProposal(proposal.id, true)).state
        assertEquals("plant-speech", applied.campaign.growth.abilities.single().id)
        assertEquals(10, applied.campaign.rules.wil)
    }

    @Test
    fun guardianChangeProposalRaisesAttributeOnlyWithAcceptedEvidence() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val withEvidence = actions.resolve(base, GameAction.RecordGrowthEvidenceProposal(
            GrowthEvidenceProposal("trial", "Mara enfrentou o risco e persistiu.", seriousRisk = true, focusedPattern = true)
        )).state
        val proposal = GrowthChangeProposal("raise-wil", listOf("trial"), "RAISE_MAX_ATTRIBUTE", attribute = "WIL", amount = 1, rationale = "A experiência sustentou a mudança.")
        val pending = actions.resolve(withEvidence, GameAction.RecordGrowthChangeProposal(proposal)).state
        assertEquals(10, pending.campaign.rules.wil)
        val result = actions.resolve(pending, GameAction.DecideGrowthChangeProposal(proposal.id, true))
        assertEquals(11, result.state.campaign.rules.wil)
        assertEquals(11, result.state.campaign.rules.maxWil)
        assertEquals(true, result.events.any { it is GameEvent.GrowthApplied })
    }

    @Test
    fun guardianChangeProposalCanGrantAbilityAndRejectsMissingEvidence() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        assertFailsWith<IllegalStateException> {
            actions.resolve(base, GameAction.RecordGrowthChangeProposal(
                GrowthChangeProposal("invalid", listOf("missing"), "GAIN_ABILITY", abilityId = "stone-skin", abilityName = "Pele de Pedra", abilityDescription = "Resiste a perigos", rationale = "Sem evidência não vale.")
            ))
        }
        val evidence = actions.resolve(base, GameAction.RecordGrowthEvidenceProposal(
            GrowthEvidenceProposal("stone-trial", "Mara sobreviveu ao colapso.", seriousRisk = true, uniqueInteraction = true)
        )).state
        val proposal = GrowthChangeProposal("stone-growth", listOf("stone-trial"), "GAIN_ABILITY", abilityId = "stone-skin", abilityName = "Pele de Pedra", abilityDescription = "Resiste a perigos", rationale = "A sobrevivência deixou uma marca.")
        val pending = actions.resolve(evidence, GameAction.RecordGrowthChangeProposal(proposal)).state
        val result = actions.resolve(pending, GameAction.DecideGrowthChangeProposal(proposal.id, true))
        assertEquals("stone-skin", result.state.campaign.growth.abilities.single().id)
    }

    @Test
    fun decliningGrowthDoesNotChangeCharacterAndPersistsDecision() {
        val base = newCharacter("Mara", 10, 10, 10)
        val actions = GameActionResolver(ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10)))
        val evidence = actions.resolve(base, GameAction.RecordGrowthEvidenceProposal(
            GrowthEvidenceProposal("trial", "Mara enfrentou o risco e persistiu.", seriousRisk = true, uniqueInteraction = true)
        )).state
        val proposal = GrowthChangeProposal("raise-str", listOf("trial"), "RAISE_MAX_ATTRIBUTE", attribute = "STR", amount = 1, rationale = "A experiência fortaleceu Mara.")
        val pending = actions.resolve(evidence, GameAction.RecordGrowthChangeProposal(proposal)).state
        val declined = actions.resolve(pending, GameAction.DecideGrowthChangeProposal(proposal.id, accepted = false)).state
        assertEquals(10, declined.campaign.rules.str)
        assertEquals(emptyList(), declined.campaign.growth.pendingChangeProposals)
        assertEquals(listOf(proposal.id), declined.campaign.growth.declinedProposalIds)
        assertEquals(declined.campaign.growth, GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(declined))!!.campaign.growth)
    }

    @Test
    fun olderSavesWithoutGrowthReviewFieldsRemainReadable() {
        val base = newCharacter("Mara", 10, 10, 10)
        val legacyValues = GameStatePersistenceCodec.encode(base).toMutableMap().apply {
            remove("growthPendingChangeCount")
            remove("growthDeclinedProposalCount")
        }
        val restored = GameStatePersistenceCodec.decode(legacyValues)!!
        assertEquals(emptyList(), restored.campaign.growth.pendingChangeProposals)
        assertEquals(emptyList(), restored.campaign.growth.declinedProposalIds)
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
