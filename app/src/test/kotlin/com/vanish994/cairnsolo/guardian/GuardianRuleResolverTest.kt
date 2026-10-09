package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.game.CombatOpponentState
import com.vanish994.cairnsolo.game.CombatEndReason
import com.vanish994.cairnsolo.game.CombatState
import com.vanish994.cairnsolo.game.CanonNpc
import com.vanish994.cairnsolo.game.CanonStatus
import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.RulesEngine
import com.vanish994.cairnsolo.rules.WeaponProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardianRuleResolverTest {
    @Test
    fun acceptedEncounterProposalStartsCombatThroughRulesEngine() {
        val random = FixedRandomSource(10, d8Value = 6)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val proposal = GuardianEncounterProposal(
            opponents = listOf(GuardianOpponentProposal(
                opponentId = "wolf-alpha",
                narrative = CombatOpponentNarrative(
                    "Lobo cinzento", "Grande.", "Ronda em círculos.", "Protege a carcaça.", "Na trilha."
                ),
                stats = CharacterState(5, 7, 3, 4, 4, 1),
                weapon = WeaponProfile("fangs", "d6")
            ))
        )

        val result = rules.resolve(newCharacter("Mara", 10, 11, 12), GuardianRuleRequest("BEGIN_COMBAT", encounter = proposal))

        assertEquals("Lobo cinzento", result.state.campaign.combat?.opponents?.single()?.narrative?.name)
        assertTrue(result.gameResult.events.any { it is GameEvent.CombatStarted })
        assertTrue(result.resultText.contains("wolf-alpha"))
    }

    @Test
    fun unacceptedEncounterDoesNotStartCombat() {
        val random = FixedRandomSource(10)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val state = newCharacter("Mara", 10, 11, 12)
        val request = GuardianRuleRequest(
            "BEGIN_COMBAT",
            encounter = GuardianEncounterProposal(
                opponents = listOf(
                    cultist("cultist-a", "Cultista da lamparina", CharacterState(5, 7, 8, 4, 4, 1), WeaponProfile("ritual-dagger", "d4")),
                    cultist("cultist-b", "Cultista do sino", CharacterState(8, 4, 6, 6, 6, 2), WeaponProfile("rusted-spear", "d8"))
                ),
                moraleLeaderId = "cultist-b"
            )
        )

        val stateBeforeValidation = state
        assertNull(rules.validationError(state, request))
        assertEquals(stateBeforeValidation, state)
        assertNull(state.campaign.combat)
    }

    @Test
    fun acceptedEncounterBeginsCombatWithEveryOpponent() {
        val random = FixedRandomSource(10, d8Value = 6)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val state = newCharacter("Mara", 10, 11, 12)
        val first = cultist(
            "cultist-a", "Cultista da lamparina", CharacterState(5, 7, 8, 4, 4, 1), WeaponProfile("ritual-dagger", "d4")
        )
        val second = cultist(
            "cultist-b", "Cultista do sino", CharacterState(8, 4, 6, 6, 6, 2), WeaponProfile("rusted-spear", "d8", ranged = true)
        )
        val acceptedProposal = GuardianEncounterProposal(
            opponents = listOf(first, second),
            moraleLeaderId = "cultist-b"
        )

        // Este resolve representa o fluxo acionado somente depois de o jogador aceitar o encontro inteiro.
        val result = rules.resolve(state, GuardianRuleRequest("BEGIN_COMBAT", encounter = acceptedProposal))

        val combat = assertNotNull(result.state.campaign.combat)
        assertEquals(2, combat.opponents.size)
        assertEquals(listOf("cultist-a", "cultist-b"), combat.opponents.map { it.id })
        assertEquals(first.narrative, combat.opponents[0].narrative)
        assertEquals(first.stats, combat.opponents[0].stats)
        assertEquals(first.weapon, combat.opponents[0].weapon)
        assertEquals(second.narrative, combat.opponents[1].narrative)
        assertEquals(second.stats, combat.opponents[1].stats)
        assertEquals(second.weapon, combat.opponents[1].weapon)
        assertEquals("cultist-b", combat.moraleLeaderId)
        assertTrue(result.resultText.contains("cultist-a"))
        assertTrue(result.resultText.contains("cultist-b"))
        assertEquals(listOf("cultist-a", "cultist-b"), result.encounterContexts.map { it.opponentId })
        assertEquals(listOf(first.narrative, second.narrative), result.encounterContexts.map { it.narrative })
        assertTrue(result.gameResult.events.any { it is GameEvent.CombatStarted })
    }

    @Test
    fun opponentContextsRemainAvailableWhenInitiativeEndsCombat() {
        val random = FixedRandomSource(20, d4Value = 4)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val base = newCharacter("Mara", 10, 11, 12)
        val fragile = base.copy(campaign = base.campaign.copy(
            rules = base.campaign.rules.copy(str = 1, hp = 1, armor = 0)
        ))
        val first = cultist(
            "cultist-a", "Cultista da lamparina", CharacterState(5, 7, 8, 4, 4, 0), WeaponProfile("ritual-dagger", "d4")
        )
        val second = cultist(
            "cultist-b", "Cultista do sino", CharacterState(8, 4, 6, 4, 4, 0), WeaponProfile("rusted-spear", "d4")
        )
        val request = GuardianRuleRequest(
            "BEGIN_COMBAT",
            encounter = GuardianEncounterProposal(listOf(first, second), moraleLeaderId = "cultist-b")
        )

        val result = rules.resolve(fragile, request)

        assertNull(result.state.campaign.combat)
        assertTrue(result.gameResult.events.any {
            it is GameEvent.CombatEnded && it.reason == CombatEndReason.PLAYER_DEFEATED
        })
        assertEquals(listOf("cultist-a", "cultist-b"), result.encounterContexts.map { it.opponentId })
        assertEquals(listOf(first.narrative, second.narrative), result.encounterContexts.map { it.narrative })
    }

    @Test
    fun attackSummaryIdentifiesSelectedTargetEnemyRollsAndAggregatePlayerDamage() {
        val random = FixedRandomSource(d20Value = 10, d4Value = 2)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val base = newCharacter("Mara", 10, 11, 12)
        val active = base.copy(campaign = base.campaign.copy(
            combat = CombatState(
                opponents = listOf(
                    CombatOpponentState(
                        "cultist-a",
                        CombatOpponentNarrative("Cultista da lamparina"),
                        CharacterState(5, 7, 8, 4, 4, 1),
                        WeaponProfile("ritual-dagger", "d4")
                    ),
                    CombatOpponentState(
                        "cultist-b",
                        CombatOpponentNarrative("Cultista do sino"),
                        CharacterState(8, 4, 6, 6, 6, 2),
                        WeaponProfile("rusted-spear", "d4")
                    )
                ),
                round = 2,
                playerCanAct = true
            )
        ))

        val result = rules.resolve(active, GameAction.CombatAttack(targetOpponentId = "cultist-b", weapon = null))
        val attack = result.gameResult.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()
        assertEquals("cultist-b", attack.targetOpponentId)
        assertEquals(listOf("cultist-a", "cultist-b"), attack.enemyAttackRolls.map { it.opponentId })
        assertEquals(listOf(2, 2), attack.enemyAttackRolls.map { it.damageRolled })
        assertEquals(2, attack.damageDealtByEnemies?.hpDamage)
        assertEquals(4, result.state.campaign.rules.hp)

        val summary = result.resultText
        assertTrue(summary.contains("cultist-b"), summary)
        assertTrue(Regex("cultist-a[^.]*rolou[^.]*2").containsMatchIn(summary), summary)
        assertTrue(Regex("cultist-b[^.]*rolou[^.]*2").containsMatchIn(summary), summary)
        assertTrue(summary.contains("2 HP perdido"), summary)
    }

    @Test
    fun failedInitiativeSummaryReportsOnlyEngineResolvedDamage() {
        val random = FixedRandomSource(20)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val proposal = GuardianEncounterProposal(
            opponents = listOf(GuardianOpponentProposal(
                opponentId = "wolf-alpha",
                narrative = CombatOpponentNarrative(
                    "Lobo cinzento", "Grande.", "Ronda em círculos.", "Protege a carcaça.", "Na trilha."
                ),
                stats = CharacterState(5, 7, 3, 4, 4, 1),
                weapon = WeaponProfile("fangs", "d4")
            ))
        )

        val result = rules.resolve(newCharacter("Mara", 10, 11, 12), GuardianRuleRequest("BEGIN_COMBAT", encounter = proposal))

        assertTrue(result.resultText.contains("Teste de DEX: d20=20; falha."))
        assertTrue(result.resultText.contains("Dano causado pelo oponente ao jogador"))
        assertTrue(result.resultText.contains("1 HP perdido"))
        assertFalse(result.resultText.contains("Dano causado pelo jogador"))
    }

    @Test
    fun acceptedNaturalAttackAgainstKnownNpcStartsCombatAndUsesEngineRolls() {
        val random = FixedRandomSource(d20Value = 10, d6Value = 4, d4Value = 2)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val base = newCharacter("Mara", 10, 11, 12)
        val state = base.copy(campaign = base.campaign.copy(
            rules = base.campaign.rules.copy(inventory = listOf(InventoryItem("adaga", damage = "d6"))),
            worldCanon = base.campaign.worldCanon.copy(
                npcs = listOf(CanonNpc("mercenario-01", "Mizera", "mercenário", status = CanonStatus.CONFIRMED))
            )
        ))
        val proposal = GuardianEncounterProposal(listOf(
            cultist("mercenario-01", "Mizera", CharacterState(5, 7, 4, 10, 10, 0), WeaponProfile("faca", "d4"))
        ))
        val request = GuardianRuleRequest("BEGIN_COMBAT", encounter = proposal)
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "mercenario-01", "adaga", "Mizera")

        assertNull(rules.validateAttackIntent(state, intent, request))
        val result = rules.resolveAttackIntent(state, intent, request)

        assertTrue(result.gameResult.events.any { it is GameEvent.CombatStarted })
        val rolls = result.gameResult.events.filterIsInstance<GameEvent.DiceRollResolved>()
        assertEquals(listOf("player", "mercenario-01"), rolls.map { it.actorId })
        assertEquals(listOf(listOf(com.vanish994.cairnsolo.rules.DieRollResult(6, 4)), listOf(com.vanish994.cairnsolo.rules.DieRollResult(4, 2))), rolls.map { it.dice })
        assertTrue(result.gameResult.events.any { it is GameEvent.CombatAttackResolved && it.damageDealtByPlayer?.rawDamage == 4 })
        assertTrue(result.resultText.contains("d6=4"))
    }

    @Test
    fun attackAgainstNewNarrativeNpcPreparesProfileOnDemandWithoutGlobalRegistry() {
        val random = FixedRandomSource(10)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val base = newCharacter("Mara", 10, 11, 12)
        val stateWithoutNpcRegistry = base
        val proposed = GuardianRuleRequest("BEGIN_COMBAT", encounter = GuardianEncounterProposal(listOf(
            cultist("traveler-7", "Viajante", CharacterState(5, 7, 4, 4, 4, 0), WeaponProfile("faca", "d4"))
        )))
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "traveler-7", null, "Viajante")

        assertNull(rules.validateAttackIntent(stateWithoutNpcRegistry, intent, proposed))
        val pending = rules.pendingCombatApproval(stateWithoutNpcRegistry, intent, proposed, "action-unique-1")
        assertNotNull(pending)
        assertEquals("traveler-7", pending.targetOpponentId)
        assertEquals("Viajante", pending.targetName)
        assertEquals(4, pending.opponents.single().stats.hp)
        assertTrue(rules.validateAttackIntent(stateWithoutNpcRegistry, intent, null)?.contains("preparar o perfil") == true)
    }

    @Test
    fun targetNameMustMatchDynamicallyProposedProfile() {
        val random = FixedRandomSource(10)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val state = newCharacter("Mara", 10, 11, 12)
        val request = GuardianRuleRequest("BEGIN_COMBAT", encounter = GuardianEncounterProposal(listOf(
            cultist("guard-1", "Guarda", CharacterState(5, 7, 4, 4, 4, 0), WeaponProfile("spear", "d6"))
        )))
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "guard-1", null, "Mercador")

        assertTrue(rules.validateAttackIntent(state, intent, request)?.contains("não corresponde") == true)
    }

    @Test
    fun approvedDynamicNpcAttackPersistsProfileAndResolvedActionId() {
        val random = FixedRandomSource(d20Value = 10, d6Value = 4, d4Value = 2)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val original = newCharacter("Mara", 10, 11, 12)
        val base = original.copy(campaign = original.campaign.copy(
            rules = original.campaign.rules.copy(inventory = listOf(InventoryItem("adaga", damage = "d6")))
        ))
        val proposal = GuardianRuleRequest("BEGIN_COMBAT", encounter = GuardianEncounterProposal(listOf(
            cultist("sentry-7", "Sentinela", CharacterState(5, 7, 4, 8, 8, 0), WeaponProfile("lanca-curta", "d4"))
        )))
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "sentry-7", "adaga", "Sentinela")
        val pending = assertNotNull(rules.pendingCombatApproval(base, intent, proposal, "action-campaign-1"))
        val waiting = base.copy(campaign = base.campaign.copy(pendingCombatApproval = pending))

        val resolved = rules.resolvePendingAttack(waiting)
        val rolls = resolved.gameResult.events.filterIsInstance<GameEvent.DiceRollResolved>()
        assertEquals(listOf("player", "sentry-7"), rolls.map { it.actorId })
        assertNull(resolved.state.campaign.pendingCombatApproval)
        assertTrue("action-campaign-1" in resolved.state.campaign.resolvedCombatActionIds)
        val savedNpc = assertNotNull(resolved.state.campaign.knownNpcs.singleOrNull { it.id == "sentry-7" })
        assertEquals("Sentinela", savedNpc.name)
        assertEquals("lanca-curta", savedNpc.combatProfile?.weapon?.id)
        assertEquals(resolved.state.campaign.combat?.opponents?.singleOrNull()?.stats, savedNpc.combatProfile?.stats)

        val restored = assertNotNull(GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(resolved.state)))
        assertEquals(resolved.state.campaign.knownNpcs, restored.campaign.knownNpcs)
        assertEquals(resolved.state.campaign.resolvedCombatActionIds, restored.campaign.resolvedCombatActionIds)
        assertEquals(restored.campaign.knownNpcs.single().combatProfile?.stats, restored.campaign.combat?.opponents?.singleOrNull()?.stats)
    }

    @Test
    fun incompleteSaveDamageAndEncounterRequestsAreRejectedBeforeExecution() {
        val random = FixedRandomSource(10)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val state = newCharacter("Mara", 10, 11, 12)

        assertEquals(
            "SAVE precisa indicar o atributo STR, DEX ou WIL.",
            rules.validationError(state, GuardianRuleRequest("SAVE"))
        )
        assertEquals(
            "DAMAGE precisa indicar uma quantidade positiva.",
            rules.validationError(state, GuardianRuleRequest("DAMAGE"))
        )
        assertEquals(
            "BEGIN_COMBAT precisa trazer uma proposta completa de encontro.",
            rules.validationError(state, GuardianRuleRequest("BEGIN_COMBAT"))
        )
        assertEquals(
            "BEGIN_COMBAT precisa trazer uma proposta completa de encontro.",
            rules.validationError(state, GuardianRuleRequest("BEGIN_COMBAT"))
        )
    }

    @Test
    fun guardianDamageRequestCannotBypassCombatAttackResolution() {
        val random = FixedRandomSource(10)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val base = newCharacter("Mara", 10, 11, 12)
        val active = base.copy(campaign = base.campaign.copy(
            combat = CombatState(
                opponents = listOf(CombatOpponentState(
                    id = "wolf-alpha",
                    narrative = CombatOpponentNarrative("Lobo"),
                    stats = CharacterState(5, 7, 3, 4, 4, 1),
                    weapon = WeaponProfile("unarmed", "d4")
                ))
            )
        ))
        val request = GuardianRuleRequest("DAMAGE", amount = 2)

        assertEquals(
            "Pedidos DAMAGE do Guardião não podem causar dano durante combate; use uma ação de ataque do motor de regras.",
            rules.validationError(active, request)
        )
        assertFailsWith<IllegalArgumentException> { rules.resolve(active, request) }
    }

    private fun cultist(
        id: String,
        name: String,
        stats: CharacterState,
        weapon: WeaponProfile
    ) = GuardianOpponentProposal(
        opponentId = id,
        narrative = CombatOpponentNarrative(name, "$name", "Observa a passagem.", "Ataca se provocado.", "Na trilha."),
        stats = stats,
        weapon = weapon
    )
}
        val intent = GuardianActionIntent(GuardianActionType.ATTACK, "mercenario-01", "adaga", "Mizera")
import com.vanish994.cairnsolo.game.GameStatePersistenceCodec
