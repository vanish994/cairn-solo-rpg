package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.DungeonAction
import com.vanish994.cairnsolo.rules.DungeonActionKind
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.RulesEngine
import com.vanish994.cairnsolo.rules.WeaponProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CombatAuthorityContractTest {
    private val narrative = CombatOpponentNarrative(
        name = "Lobo cinzento",
        appearance = "Grande, com uma orelha rasgada.",
        behavior = "Ronda em círculos.",
        intent = "Protege a carcaça.",
        context = "Encontro na trilha ao anoitecer."
    )

    private fun state(hp: Int = 6): GameState = GameState(
        campaign = CampaignState(
            character = CharacterIdentity(name = "Tester"),
            rules = CharacterState(10, 10, 10, hp, 6, 0)
        )
    )

    private fun resolver(random: FixedRandomSource) =
        GameActionResolver(ExplorationEngine(random), RulesEngine(random))

    @Test
    fun confirmedCombatStoresNarrativeProfile() {
        val started = resolver(FixedRandomSource(10, d8Value = 6)).resolve(
            state(),
            GameAction.BeginCombat(
                "wolf-alpha",
                CharacterState(5, 7, 3, 4, 4, 1),
                WeaponProfile("fangs", "d6"),
                narrative
            )
        )

        assertEquals(narrative, started.state.campaign.combat?.opponentNarrative)
        assertIs<GameEvent.CombatStarted>(started.events.first())
    }

    @Test
    fun failedDexInitiativePreservesProfileAndAppliesExactlyOneOpponentAttack() {
        val started = resolver(FixedRandomSource(20)).resolve(
            state(),
            GameAction.BeginCombat(
                "wolf-alpha",
                CharacterState(5, 7, 3, 4, 4, 1),
                WeaponProfile("fangs", "d6"),
                narrative
            )
        )

        assertEquals(narrative, started.state.campaign.combat?.opponentNarrative)
        assertEquals(1, started.events.filterIsInstance<GameEvent.CombatAttackResolved>().size)
        assertEquals(5, started.state.campaign.rules.hp)
        val attack = assertNotNull(started.events.filterIsInstance<GameEvent.CombatAttackResolved>().singleOrNull())
        assertEquals(null, attack.damageDealtByPlayer)
        assertEquals(1, attack.damageDealtByOpponent?.hpDamage)
    }

    @Test
    fun beginCombatRejectsUnsupportedOpponentDamageBeforeInitiative() {
        assertFailsWith<IllegalArgumentException> {
            resolver(FixedRandomSource(20)).resolve(
                state(),
                GameAction.BeginCombat(
                    "wolf-alpha",
                    CharacterState(5, 7, 3, 4, 4, 1),
                    WeaponProfile("fangs", "4 STR"),
                    narrative
                )
            )
        }
    }

    @Test
    fun activeCombatBlocksExplorationRestTravelDungeonAndPurchase() {
        val active = state().copy(campaign = state().campaign.copy(
            combat = CombatState(
                opponentId = "wolf-alpha",
                opponent = CharacterState(5, 7, 3, 4, 4, 1),
                opponentNarrative = narrative
            )
        ))
        val blockedActions = listOf(
            GameAction.ExploreContinue,
            GameAction.ExploreInvestigate,
            GameAction.ExploreRest,
            GameAction.Rest,
            GameAction.EndCombat,
            GameAction.ApplyDamage(2),
            GameAction.AddItem(InventoryItem("torch")),
            GameAction.RemoveItem("torch"),
            GameAction.StartTravel("north"),
            GameAction.DungeonAct(DungeonAction(DungeonActionKind.MOVE)),
            GameAction.Purchase("torch")
        )

        blockedActions.forEach { action ->
            val error = assertFailsWith<IllegalArgumentException>(action.toString()) {
                resolver(FixedRandomSource(10)).resolve(active, action)
            }
            assertEquals("This action is unavailable during active combat", error.message)
        }
    }

    @Test
    fun activeCombatStillAllowsGuardianIntentAndCombatAttack() {
        val base = state()
        val active = base.copy(campaign = base.campaign.copy(
            rules = base.campaign.rules.copy(inventory = listOf(InventoryItem("sword", damage = "d4"))),
            combat = CombatState(
                opponentId = "wolf-alpha",
                opponent = CharacterState(5, 7, 3, 12, 12, 1),
                opponentWeapon = WeaponProfile("fangs", "d4"),
                opponentNarrative = narrative
            )
        ))
        val resolver = resolver(FixedRandomSource(10, d8Value = 1))

        val intent = resolver.resolve(active, GameAction.GuardianIntent("Tento recuar lentamente."))
        assertTrue(intent.state.campaign.guardianHistory.last().contains("recuar"))
        val attack = resolver.resolve(intent.state, GameAction.CombatAttack(WeaponProfile("sword", "d4")))
        assertTrue(attack.events.any { it is GameEvent.CombatAttackResolved })
        assertEquals(narrative, attack.state.campaign.combat?.opponentNarrative)
    }

    @Test
    fun combatAttackUsesOwnedWeaponDamageAndRejectsUnownedWeapons() {
        val base = state()
        val active = base.copy(campaign = base.campaign.copy(
            rules = base.campaign.rules.copy(inventory = listOf(InventoryItem("sword", damage = "d4"))),
            combat = CombatState(
                opponentId = "wolf-alpha",
                opponent = CharacterState(5, 7, 3, 12, 12, 0),
                opponentNarrative = narrative
            )
        ))
        val attack = resolver(FixedRandomSource(10, d4Value = 3, d12Value = 12)).resolve(
            active,
            GameAction.CombatAttack(WeaponProfile("sword", "d12"))
        )
        val attackEvent = assertNotNull(attack.events.filterIsInstance<GameEvent.CombatAttackResolved>().firstOrNull())

        assertEquals(3, attackEvent.damageDealtByPlayer?.rawDamage)
        val error = assertFailsWith<IllegalArgumentException> {
            resolver(FixedRandomSource(10)).resolve(active, GameAction.CombatAttack(WeaponProfile("stolen-sword", "d12")))
        }
        assertEquals("A arma escolhida não está no inventário.", error.message)
        assertFailsWith<IllegalArgumentException> {
            val withTrap = active.copy(campaign = active.campaign.copy(
                rules = active.campaign.rules.copy(inventory = listOf(InventoryItem("spring-loaded-trap", damage = "4 STR")))
            ))
            resolver(FixedRandomSource(10)).resolve(withTrap, GameAction.CombatAttack(WeaponProfile("spring-loaded-trap", "d4")))
        }
    }
}
