package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.game.CombatState
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GuardianRuleResolverTest {
    @Test
    fun acceptedEncounterProposalStartsCombatThroughRulesEngine() {
        val random = FixedRandomSource(10, d8Value = 6)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val proposal = GuardianEncounterProposal(
            opponentId = "wolf-alpha",
            narrative = CombatOpponentNarrative(
                "Lobo cinzento", "Grande.", "Ronda em círculos.", "Protege a carcaça.", "Na trilha."
            ),
            stats = CharacterState(5, 7, 3, 4, 4, 1),
            weapon = WeaponProfile("fangs", "d6")
        )

        val result = rules.resolve(newCharacter("Mara", 10, 11, 12), GuardianRuleRequest("BEGIN_COMBAT", encounter = proposal))

        assertEquals("Lobo cinzento", result.state.campaign.combat?.opponentNarrative?.name)
        assertIs<GameEvent.CombatStarted>(result.gameResult.events.first())
        assertTrue(result.resultText.contains("Combate iniciado contra wolf-alpha"))
    }

    @Test
    fun failedInitiativeSummaryReportsOnlyEngineResolvedDamage() {
        val random = FixedRandomSource(20)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val proposal = GuardianEncounterProposal(
            opponentId = "wolf-alpha",
            narrative = CombatOpponentNarrative(
                "Lobo cinzento", "Grande.", "Ronda em círculos.", "Protege a carcaça.", "Na trilha."
            ),
            stats = CharacterState(5, 7, 3, 4, 4, 1),
            weapon = WeaponProfile("fangs", "d4")
        )

        val result = rules.resolve(newCharacter("Mara", 10, 11, 12), GuardianRuleRequest("BEGIN_COMBAT", encounter = proposal))

        assertTrue(result.resultText.contains("Teste de DEX: d20=20; falha."))
        assertTrue(result.resultText.contains("Dano causado pelo oponente ao jogador"))
        assertTrue(result.resultText.contains("1 HP perdido"))
        assertFalse(result.resultText.contains("Dano causado pelo jogador"))
    }

    @Test
    fun incompleteSaveAndDamageRequestsAreRejectedBeforeExecution() {
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
    }

    @Test
    fun guardianDamageRequestCannotBypassCombatAttackResolution() {
        val random = FixedRandomSource(10)
        val rules = GuardianRuleResolver(GameActionResolver(ExplorationEngine(random), RulesEngine(random)))
        val base = newCharacter("Mara", 10, 11, 12)
        val active = base.copy(campaign = base.campaign.copy(
            combat = CombatState(
                opponentId = "wolf-alpha",
                opponent = CharacterState(5, 7, 3, 4, 4, 1),
                opponentNarrative = CombatOpponentNarrative("Lobo")
            )
        ))
        val request = GuardianRuleRequest("DAMAGE", amount = 2)

        assertEquals(
            "Pedidos DAMAGE do Guardião não podem causar dano durante combate; use uma ação de ataque do motor de regras.",
            rules.validationError(active, request)
        )
        assertFailsWith<IllegalArgumentException> { rules.resolve(active, request) }
    }
}
