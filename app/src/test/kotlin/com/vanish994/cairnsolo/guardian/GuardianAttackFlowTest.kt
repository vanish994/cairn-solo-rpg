package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CampaignNpcState
import com.vanish994.cairnsolo.game.CanonProposal
import com.vanish994.cairnsolo.game.CanonStatus
import com.vanish994.cairnsolo.game.CanonSource
import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.RulesEngine
import com.vanish994.cairnsolo.rules.WeaponProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardianAttackFlowTest {
    @Test
    fun explicitAttackFallbackOnlyMatchesClearPositiveDeclarations() {
        assertTrue(GuardianAttackFlow.isExplicitAttackDeclaration("Ataco Garrick com um soco."))
        assertTrue(GuardianAttackFlow.isExplicitAttackDeclaration("Eu quero atacar o duelista."))
        assertTrue(GuardianAttackFlow.isExplicitAttackDeclaration("Vou golpear o cultista."))
        assertTrue(!GuardianAttackFlow.isExplicitAttackDeclaration("Não vou atacar agora."))
        assertTrue(!GuardianAttackFlow.isExplicitAttackDeclaration("Se ele me atacar, eu recuo."))
        assertTrue(!GuardianAttackFlow.isExplicitAttackDeclaration("Talvez eu ataque depois."))
    }

    private fun opponent() = GuardianOpponentProposal(
        opponentId = "garrick",
        narrative = CombatOpponentNarrative(
            name = "Garrick",
            appearance = "Um duelista de casaco escuro.",
            behavior = "Mantém a guarda alta.",
            intent = "Quer testar o aventureiro.",
            context = "No pátio da estalagem."
        ),
        stats = CharacterState(8, 12, 9, 8, 8, 1),
        weapon = WeaponProfile("rapier-garrick", "d6", ranged = false)
    )

    private fun combatRequest() = GuardianRuleRequest(
        type = "BEGIN_COMBAT",
        encounter = GuardianEncounterProposal(listOf(opponent()))
    )

    private fun flow(d20: Int = 10, d6: Int = 4, d4: Int = 2): Pair<GuardianAttackFlow, GuardianRuleResolver> {
        val random = FixedRandomSource(d20Value = d20, d6Value = d6, d4Value = d4)
        val ruleResolver = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        return GuardianAttackFlow(ruleResolver) to ruleResolver
    }

    @Test
    fun newNarrativeNpcIsPreparedAndOriginalActionWaitsForApproval() {
        val (flow, _) = flow()
        val base = newCharacter("Mara", 10, 11, 12)
        val declared = base.copy(campaign = base.campaign.copy(
            guardianMessage = "Você: aceito o duelo de Garrick e avanço com a adaga.",
            rules = base.campaign.rules.copy(inventory = listOf(InventoryItem("adaga", damage = "d6")))
        ))
        val npcCreatedByNarration = GameActionResolver(
            ExplorationEngine(FixedRandomSource(10)), RulesEngine(FixedRandomSource(10))
        ).resolve(declared, GameAction.ApplyCanonProposals(listOf(
            CanonProposal.UpsertNpc(
                id = "garrick", name = "Garrick", role = "duelista", description = "Encontrado no pátio",
                status = CanonStatus.CONFIRMED, source = CanonSource.GUARDIAN
            )
        ))).state
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "garrick", "adaga", "Garrick")

        val result = flow.prepare(npcCreatedByNarration, intent, combatRequest(), "duel-garrick-1")

        val awaiting = assertIs<GuardianAttackFlowResult.AwaitingApproval>(result)
        assertEquals("duel-garrick-1", awaiting.approval.actionId)
        assertEquals("garrick", awaiting.approval.targetOpponentId)
        assertEquals("adaga", awaiting.approval.weaponId)
        assertEquals(npcCreatedByNarration.campaign.guardianMessage, awaiting.state.campaign.guardianMessage)
        assertNull(awaiting.state.campaign.combat)
        assertTrue(awaiting.state.campaign.knownNpcs.any { it.id == "garrick" && it.combatProfile == null })
    }

    @Test
    fun approvalRunsInitiativeAttackDamageAndEnemyResponseThroughRulesEngine() {
        val (flow, ruleResolver) = flow()
        val base = newCharacter("Mara", 10, 11, 12)
        val declared = base.copy(campaign = base.campaign.copy(
            guardianMessage = "Você: avanço contra Garrick com a adaga.",
            rules = base.campaign.rules.copy(inventory = listOf(InventoryItem("adaga", damage = "d6")))
        ))
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "garrick", "adaga", "Garrick")
        val awaiting = assertIs<GuardianAttackFlowResult.AwaitingApproval>(
            flow.prepare(declared, intent, combatRequest(), "duel-garrick-2")
        )

        val resolved = ruleResolver.resolvePendingAttack(awaiting.state)
        val attack = resolved.gameResult.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()

        assertTrue(resolved.gameResult.events.any { it is GameEvent.CombatStarted })
        assertEquals("garrick", attack.targetOpponentId)
        assertEquals(4, attack.damageDealtByPlayer?.rawDamage)
        assertEquals(listOf("player", "garrick"), resolved.gameResult.events
            .filterIsInstance<GameEvent.DiceRollResolved>().map { it.actorId })
        assertNull(resolved.state.campaign.pendingCombatApproval)
        assertTrue("duel-garrick-2" in resolved.state.campaign.resolvedCombatActionIds)
        assertEquals("Garrick", resolved.state.campaign.knownNpcs.single { it.id == "garrick" }.name)
        assertNotNull(resolved.state.campaign.knownNpcs.single { it.id == "garrick" }.combatProfile)
    }

    @Test
    fun rejectingEncounterCancelsOnlyTheProposalAndDoesNotCreateCombatEffects() {
        val (flow, _) = flow()
        val base = newCharacter("Mara", 10, 11, 12)
        val declared = base.copy(campaign = base.campaign.copy(
            knownNpcs = listOf(CampaignNpcState("garrick", "Garrick", "duelista", "Encontrado no pátio"))
        ))
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "garrick", null, "Garrick")
        val awaiting = assertIs<GuardianAttackFlowResult.AwaitingApproval>(
            flow.prepare(declared, intent, combatRequest(), "duel-garrick-3")
        )

        val rejected = flow.reject(awaiting.state)

        assertNull(rejected.campaign.pendingCombatApproval)
        assertNull(rejected.campaign.combat)
        assertEquals(declared.campaign.rules, rejected.campaign.rules)
        assertEquals("Garrick", rejected.campaign.knownNpcs.single().name)
        assertNull(rejected.campaign.knownNpcs.single().combatProfile)
    }

    @Test
    fun incompleteProposalIsRejectedWithoutPublishingGuardianNarrationAsOutcome() {
        val (flow, _) = flow()
        val base = newCharacter("Mara", 10, 11, 12)
        val declared = base.copy(campaign = base.campaign.copy(
            guardianMessage = "Você: aceito o duelo de Garrick."
        ))
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "garrick", null, "Garrick")

        val result = flow.prepare(declared, intent, GuardianRuleRequest("BEGIN_COMBAT"), "duel-garrick-4")

        val rejected = assertIs<GuardianAttackFlowResult.Rejected>(result)
        assertTrue(rejected.message.contains("proposta completa") || rejected.message.contains("preparar o perfil"))
        assertEquals(declared.campaign.guardianMessage, rejected.state.campaign.guardianMessage)
        assertNull(rejected.state.campaign.pendingCombatApproval)
        assertNull(rejected.state.campaign.combat)
    }
}
