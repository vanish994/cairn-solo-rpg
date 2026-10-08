package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.game.CombatOpponentState
import com.vanish994.cairnsolo.game.CombatState
import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.FixedRandomSource
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

        val ended = rules.resolve(result.state, GameAction.EndCombat)
        assertNull(ended.state.campaign.combat)
        assertEquals(listOf("cultist-a", "cultist-b"), ended.encounterContexts.map { it.opponentId })
        assertEquals(listOf(first.narrative, second.narrative), ended.encounterContexts.map { it.narrative })
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
