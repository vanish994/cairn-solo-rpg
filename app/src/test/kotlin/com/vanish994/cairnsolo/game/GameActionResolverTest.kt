package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.RulesEngine
import com.vanish994.cairnsolo.rules.Scar
import com.vanish994.cairnsolo.rules.ScarRecovery
import com.vanish994.cairnsolo.rules.WeaponProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameActionResolverTest {
    private fun state(
        hp: Int = 6,
        maxHp: Int = 6,
        fatigue: Int = 0,
        deprived: Boolean = false
    ): GameState =
        GameState(
            campaign = CampaignState(
                character = CharacterIdentity(name = "Tester"),
                rules = CharacterState(
                    str = 10,
                    dex = 10,
                    wil = 10,
                    hp = hp,
                    maxHp = maxHp,
                    armor = 0,
                    fatigue = fatigue,
                    deprived = deprived
                )
            )
        )

    private fun resolver(
        random: FixedRandomSource = FixedRandomSource(d20Value = 10, d6Value = 1)
    ): GameActionResolver =
        GameActionResolver(
            exploration = ExplorationEngine(random),
            rules = RulesEngine(random)
        )

    @Test
    fun continueExplorationReturnsAuthoritativeStateAndEvent() {
        val result = resolver().resolve(state(), GameAction.ExploreContinue)
        assertEquals("old_road", result.state.campaign.sceneId)
        assertEquals(1L, result.state.campaign.turn)
        assertIs<GameEvent.SceneAdvanced>(result.events.single())
    }

    @Test
    fun investigatePreservesSceneWhileAdvancingCampaignTurn() {
        val result = resolver(
            FixedRandomSource(d20Value = 10, d6Value = 5)
        ).resolve(state(), GameAction.ExploreInvestigate)
        assertEquals("prologue", result.state.campaign.sceneId)
        assertEquals(1L, result.state.campaign.turn)
        assertIs<GameEvent.SceneInvestigated>(result.events.single())
    }

    @Test
    fun restUsesRulesEngineAndAdvancesCampaignTurn() {
        val result = resolver().resolve(
            state(hp = 2, maxHp = 6, fatigue = 2),
            GameAction.ExploreRest
        )
        assertEquals(6, result.state.campaign.rules.hp)
        assertEquals(0, result.state.campaign.rules.fatigue)
        assertEquals(1L, result.state.campaign.turn)

        val event = assertIs<GameEvent.RestCompleted>(result.events.single())
        assertEquals(4, event.hpRecovered)
        assertEquals(2, event.fatigueRecovered)
    }

    @Test
    fun damageIsResolvedByRulesEngineThroughGameAction() {
        val result = resolver().resolve(state(hp = 6), GameAction.ApplyDamage(2))
        assertEquals(4, result.state.campaign.rules.hp)
        assertIs<GameEvent.DamageResolved>(result.events.single())
    }

    @Test
    fun addAndRemoveItemAreResolvedThroughGameAction() {
        val added = resolver().resolve(
            state(),
            GameAction.AddItem(com.vanish994.cairnsolo.rules.InventoryItem("torch"))
        )
        assertEquals(1, added.state.campaign.rules.inventory.size)
        assertIs<GameEvent.ItemAdded>(added.events.single())

        val removed = resolver().resolve(added.state, GameAction.RemoveItem("torch"))
        assertEquals(0, removed.state.campaign.rules.inventory.size)
        assertIs<GameEvent.ItemRemoved>(removed.events.single())
    }

    @Test
    fun fatigueAndSaveAreResolvedThroughRulesEngine() {
        val fatigued = resolver().resolve(state(), GameAction.AddFatigue(2))
        assertEquals(2, fatigued.state.campaign.rules.fatigue)
        assertIs<GameEvent.FatigueAdded>(fatigued.events.single())

        val saved = resolver(FixedRandomSource(d20Value = 5)).resolve(
            state(), GameAction.Save(com.vanish994.cairnsolo.rules.Attribute.STR)
        )
        val event = assertIs<GameEvent.SaveResolved>(saved.events.single())
        assertEquals(5, event.roll)
        assertEquals(true, event.success)
    }

    @Test
    fun deprivationAndStabilizationAreAuthoritativeActions() {
        val deprived = resolver().resolve(state(), GameAction.MarkDeprived(true))
        assertEquals(true, deprived.state.campaign.rules.deprived)
        assertIs<GameEvent.DeprivationChanged>(deprived.events.single())

        val critical = state().copy(
            campaign = state().campaign.copy(
                rules = state().campaign.rules.copy(critical = true)
            )
        )
        val stabilized = resolver().resolve(critical, GameAction.StabilizeCritical)
        assertEquals(false, stabilized.state.campaign.rules.critical)
        assertIs<GameEvent.CriticalStabilized>(stabilized.events.single())
    }

    @Test
    fun scarRecoveryIsResolvedThroughRulesEngine() {
        val damaged = state(hp = 3, maxHp = 6).let {
            resolver(FixedRandomSource(d20Value = 10, d6Value = 2)).resolve(it, GameAction.ApplyDamage(3))
        }
        assertEquals(Scar.WALLOPED, damaged.state.campaign.rules.scar)
        assertEquals(ScarRecovery.WALLOPED, damaged.state.campaign.rules.scarRecovery)

        val recovered = resolver(FixedRandomSource(d20Value = 10, d6Value = 2))
            .resolve(damaged.state, GameAction.RecoverScar)
        assertEquals(6, recovered.state.campaign.rules.maxHp)
        assertEquals(6, recovered.state.campaign.rules.hp)
        assertEquals(null, recovered.state.campaign.rules.scarRecovery)
        assertIs<GameEvent.ScarRecovered>(recovered.events.single())
    }

    @Test
    fun createCharacterActionStoresRolledProfile() {
        val rolled = com.vanish994.cairnsolo.rules.RolledCharacter(
            9, 11, 13, 5,
            com.vanish994.cairnsolo.rules.Background.PROWLER,
            null,
            age = 29
        )
        val result = resolver().resolve(state(), GameAction.CreateCharacter("Aran", rolled))
        assertEquals("Aran", result.state.campaign.character.name)
        assertEquals(29, result.state.campaign.profile.age)
        assertEquals(com.vanish994.cairnsolo.rules.Background.PROWLER, result.state.campaign.profile.background)
        assertIs<GameEvent.CharacterCreated>(result.events.single())
    }

    @Test
    fun guardianIntentIsRecordedWithoutChangingRules() {
        val before = state(hp = 6, maxHp = 6)
        val result = resolver().resolve(before, GameAction.GuardianIntent("seguir em direção à luz"))
        assertEquals(6, result.state.campaign.rules.hp)
        assertEquals(1L, result.state.campaign.turn)
        assertTrue(result.state.campaign.guardianMessage.contains("seguir em direção à luz"))
        assertTrue(result.state.campaign.log.last().contains("seguir em direção à luz"))
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun deprivedRestDoesNotRecoverHpOrFatigue() {
        val result = resolver().resolve(
            state(hp = 2, maxHp = 6, fatigue = 2, deprived = true),
            GameAction.ExploreRest
        )
        assertEquals(2, result.state.campaign.rules.hp)
        assertEquals(2, result.state.campaign.rules.fatigue)

        val event = assertIs<GameEvent.RestCompleted>(result.events.single())
        assertEquals(0, event.hpRecovered)
        assertEquals(0, event.fatigueRecovered)
    }
}


class CombatGameActionResolverTest {
    private fun state(hp: Int = 6): GameState = GameState(
        campaign = CampaignState(
            character = CharacterIdentity(name = "Tester"),
            rules = CharacterState(10, 10, 10, hp, 6, 0)
        )
    )

    private fun resolver(random: FixedRandomSource): GameActionResolver =
        GameActionResolver(ExplorationEngine(random), RulesEngine(random))

    @Test
    fun combatFlowUsesDexInitiativeThenResolvesAttackAndEndsOnOpponentZeroHp() {
        val enemy = CharacterState(4, 4, 4, 6, 6, 0)
        val started = resolver(FixedRandomSource(10, d8Value = 6)).resolve(
            state(), GameAction.BeginCombat("wolf", enemy, WeaponProfile("bite", "d4"))
        )
        assertTrue(started.state.campaign.combat?.playerCanAct == true)
        assertIs<GameEvent.CombatStarted>(started.events.first())

        val attacked = resolver(FixedRandomSource(10, d8Value = 6)).resolve(
            started.state, GameAction.CombatAttack(WeaponProfile("sword", "d8"))
        )
        assertEquals(null, attacked.state.campaign.combat)
        assertIs<GameEvent.CombatEnded>(attacked.events.last())
        assertTrue((attacked.events.last() as GameEvent.CombatEnded).victory)
    }

    @Test
    fun failedFirstDexSaveLetsEnemyActAndAdvancesToRoundTwo() {
        val enemy = CharacterState(4, 4, 4, 6, 6, 0)
        val result = resolver(FixedRandomSource(20)).resolve(
            state(), GameAction.BeginCombat("wolf", enemy)
        )
        assertEquals(2, result.state.campaign.combat?.round)
        assertTrue(result.state.campaign.combat?.playerCanAct == true)
        assertEquals(5, result.state.campaign.rules.hp)
        assertIs<GameEvent.CombatAttackResolved>(result.events.last())
    }
}
