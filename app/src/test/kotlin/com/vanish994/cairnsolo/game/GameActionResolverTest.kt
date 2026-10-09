package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.DieRollResult
import com.vanish994.cairnsolo.rules.RandomSource
import com.vanish994.cairnsolo.rules.RulesEngine
import com.vanish994.cairnsolo.rules.Scar
import com.vanish994.cairnsolo.rules.ScarRecovery
import com.vanish994.cairnsolo.rules.WeaponProfile
import com.vanish994.cairnsolo.rules.MagicItems
import com.vanish994.cairnsolo.rules.DowntimeAction
import com.vanish994.cairnsolo.rules.DowntimeActionType
import com.vanish994.cairnsolo.rules.DungeonAction
import com.vanish994.cairnsolo.rules.DungeonActionKind
import com.vanish994.cairnsolo.rules.WildernessAction
import com.vanish994.cairnsolo.rules.WildernessState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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
    fun duplicateInventoryIdsAreRejectedAtActionBoundary() {
        val actionResolver = resolver()
        val item = com.vanish994.cairnsolo.rules.InventoryItem("torch")
        val added = actionResolver.resolve(state(), GameAction.AddItem(item)).state
        assertFailsWith<IllegalArgumentException> { actionResolver.resolve(added, GameAction.AddItem(item)) }
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
    fun magicMarketplaceDowntimeDungeonAndWildernessUseResolverState() {
        val random = FixedRandomSource(d20Value = 10, d6Value = 6)
        val template = state()
        val base = template.copy(campaign = template.campaign.copy(
            profile = CharacterProfile(gold = 20),
            rules = template.campaign.rules.copy(inventory = listOf(MagicItems.scroll("scroll-1", "detect-magic"))),
            dungeon = com.vanish994.cairnsolo.rules.DungeonState("crypt", safeLocation = true, light = com.vanish994.cairnsolo.rules.DungeonLight.TORCH),
            wilderness = WildernessState("village", rations = 2)
        ))
        val resolver = GameActionResolver(ExplorationEngine(random), RulesEngine(random))
        val cast = resolver.resolve(base, GameAction.CastSpell("scroll-1"))
        assertTrue(cast.state.campaign.rules.inventory.isEmpty())
        val bought = resolver.resolve(cast.state, GameAction.Purchase("dagger"))
        assertEquals(15, bought.state.campaign.profile.gold)
        val rested = resolver.resolve(bought.state, GameAction.PerformDowntime(DowntimeAction(DowntimeActionType.FOLLOW_LEAD)))
        assertEquals(1, rested.state.campaign.downtime.completedActions)
        val dungeon = resolver.resolve(rested.state, GameAction.DungeonAct(DungeonAction(DungeonActionKind.MOVE)))
        assertEquals(1, dungeon.state.campaign.dungeon?.turn)
        val wild = resolver.resolve(dungeon.state, GameAction.WildernessAct(WildernessAction.MAKE_CAMP))
        assertEquals(1, wild.state.campaign.wilderness?.rations)
    }

    @Test
    fun newCampaignInitializesTravelAndDungeonStateAndPersistsThem() {
        val random = FixedRandomSource(d20Value = 10, d6Value = 3)
        val created = GameActionResolver(ExplorationEngine(random), RulesEngine(random)).resolve(
            state(), GameAction.CreateCharacter("Mara", com.vanish994.cairnsolo.rules.RolledCharacter(10, 10, 10, 6, null, null))
        ).state
        assertTrue(created.campaign.wilderness != null)
        assertTrue(created.campaign.dungeon != null)
        val restored = GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(created))!!
        assertEquals(created.campaign.wilderness, restored.campaign.wilderness)
        assertEquals(created.campaign.dungeon, restored.campaign.dungeon)
        assertEquals(created.campaign.downtime, restored.campaign.downtime)
    }

    @Test
    fun wardenActionsResolveAndPersistHirelingState() {
        val template = state().copy(campaign = state().campaign.copy(profile = CharacterProfile(gold = 40)))
        val resolver = GameActionResolver(ExplorationEngine(FixedRandomSource(10, d6Value = 6)), RulesEngine(FixedRandomSource(10, d6Value = 6)))
        val hired = resolver.resolve(template, GameAction.HireHireling("hireling-scholar", "scholar-1", "Iria"))
        assertEquals(20, hired.state.campaign.profile.gold)
        assertEquals("scholar-1", hired.state.campaign.hirelings.single().id)
        val paid = resolver.resolve(hired.state, GameAction.PayHirelings)
        assertEquals(0, paid.state.campaign.profile.gold)
        val morale = resolver.resolve(paid.state, GameAction.CheckHirelingMorale("scholar-1"))
        assertEquals(false, morale.state.campaign.hirelings.single().active)
        val restored = GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(morale.state))!!
        assertEquals(morale.state.campaign.hirelings, restored.campaign.hirelings)
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
    private fun state(hp: Int = 6, armor: Int = 0): GameState = GameState(
        campaign = CampaignState(
            character = CharacterIdentity(name = "Tester"),
            rules = CharacterState(10, 10, 10, hp, 6, armor)
        )
    )

    private fun resolver(random: RandomSource): GameActionResolver =
        GameActionResolver(ExplorationEngine(random), RulesEngine(random))

    private fun opponent(
        id: String,
        hp: Int = 6,
        str: Int = 10,
        wil: Int = 10,
        status: CombatOpponentStatus = CombatOpponentStatus.ACTIVE,
        weapon: WeaponProfile = WeaponProfile("claws-$id", "d4")
    ): CombatOpponentState = CombatOpponentState(
        id = id,
        narrative = CombatOpponentNarrative(name = id),
        stats = CharacterState(str = str, dex = 10, wil = wil, hp = hp, maxHp = maxOf(1, hp), armor = 0),
        weapon = weapon,
        status = status
    )

    private fun combatState(
        opponents: List<CombatOpponentState>,
        hp: Int = 6,
        armor: Int = 0,
        moraleLeaderId: String? = null,
        resolvedMoraleTriggers: Set<CombatMoraleTrigger> = emptySet()
    ): GameState {
        val base = state(hp, armor)
        return base.copy(campaign = base.campaign.copy(combat = CombatState(
            opponents = opponents,
            moraleLeaderId = moraleLeaderId,
            resolvedMoraleTriggers = resolvedMoraleTriggers
        )))
    }

    private class SequentialRandomSource(vararg rolls: Pair<Int, Int>) : RandomSource {
        private val expectedRolls = rolls.toList()
        private var nextRollIndex = 0

        override fun roll(sides: Int): Int {
            val expected = expectedRolls.getOrNull(nextRollIndex)
                ?: error("Unexpected d$sides roll at position ${nextRollIndex + 1}")
            nextRollIndex += 1
            assertEquals(expected.first, sides, "Unexpected die at roll ${nextRollIndex}")
            return expected.second
        }

        fun assertAllRollsUsed() {
            assertEquals(expectedRolls.size, nextRollIndex, "Not all scripted rolls were consumed")
        }
    }

    @Test
    fun combatFlowUsesDexInitiativeThenChecksSolitaryOpponentMoraleAtZeroHp() {
        val enemy = CharacterState(4, 4, 4, 6, 6, 0)
        val base = state()
        val armed = base.copy(campaign = base.campaign.copy(
            rules = base.campaign.rules.copy(inventory = listOf(InventoryItem("sword", damage = "d8")))
        ))
        val started = resolver(FixedRandomSource(10, d8Value = 6)).resolve(
            armed, GameAction.BeginCombat("wolf", enemy, WeaponProfile("bite", "d4"))
        )
        assertTrue(started.state.campaign.combat?.playerCanAct == true)
        assertIs<GameEvent.CombatStarted>(started.events.first())

        val attacked = resolver(FixedRandomSource(10, d8Value = 6)).resolve(
            started.state, GameAction.CombatAttack(targetOpponentId = "wolf", weapon = WeaponProfile("sword", "d8"))
        )
        assertEquals(null, attacked.state.campaign.combat)
        val attack = attacked.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()
        assertEquals("wolf", attack.targetOpponentId)
        val damageRoll = attacked.events.filterIsInstance<GameEvent.DiceRollResolved>().single()
        assertEquals(RollPurpose.PLAYER_ATTACK_DAMAGE, damageRoll.purpose)
        assertEquals(listOf(DieRollResult(8, 6)), damageRoll.dice)
        assertEquals(CombatMoraleTrigger.SINGLE_OPPONENT_ZERO_HP, attack.moraleOutcomes.single().trigger)
        assertEquals(listOf("wolf"), attack.fledOpponentIds)
        val ended = attacked.events.filterIsInstance<GameEvent.CombatEnded>().single()
        assertEquals(listOf("wolf"), ended.opponentIds)
        assertEquals(CombatEndReason.OPPONENTS_FLED, ended.reason)
        assertTrue(attacked.state.campaign.history.any { it.type == HistoryEventType.COMBAT_UPDATED })
    }

    @Test
    fun factionProgressIsChangedOnlyByResolverAndPersistsInWorldState() {
        val random = FixedRandomSource(d20Value = 10, d6Value = 3)
        val world = WorldGenerator(random).generate(WorldSeed("campaign", "mistério"))
        val base = state().copy(campaign = state().campaign.copy(worldState = world))
        val result = resolver(random).resolve(base, GameAction.AdvanceFaction(world.factions.first().id, 2, "A personagem ajudou a facção contra seu obstáculo."))
        val faction = result.state.campaign.worldState!!.factions.first()
        assertEquals(2, faction.goalProgress)
        assertIs<GameEvent.FactionProgressChanged>(result.events.single())
        assertEquals(HistoryEventType.FACTION_UPDATED, result.state.campaign.history.single().type)
        val restored = GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(result.state))!!
        assertEquals(result.state.campaign.worldState, restored.campaign.worldState)
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

    @Test
    fun multiOpponentCombatStillUsesDexSaveOnFirstRound() {
        val random = SequentialRandomSource(20 to 20, 4 to 2, 6 to 4)
        val started = resolver(random).resolve(
            state(armor = 2),
            GameAction.BeginCombat(opponents = listOf(
                opponent("cultist-a", weapon = WeaponProfile("knife-a", "d4")),
                opponent("cultist-b", weapon = WeaponProfile("knife-b", "d6"))
            ))
        )

        val startEvent = assertIs<GameEvent.CombatStarted>(
            started.events.filterIsInstance<GameEvent.CombatStarted>().single()
        )
        assertEquals(listOf("cultist-a", "cultist-b"), startEvent.opponentIds)
        assertEquals(1, startEvent.round)
        assertEquals(false, startEvent.playerCanAct)

        val attack = started.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()
        assertEquals(1, attack.round)
        assertEquals(true, attack.playerCanAct)
        assertEquals(null, attack.damageDealtByPlayer)
        assertEquals(listOf("cultist-a", "cultist-b"), attack.enemyAttackRolls.map { it.opponentId })
        assertEquals(listOf(2, 4), attack.enemyAttackRolls.map { it.damageRolled })
        assertEquals(4, attack.damageDealtByEnemies?.rawDamage)
        assertEquals(2, attack.damageDealtByEnemies?.armorAbsorbed)
        assertEquals(2, attack.damageDealtByEnemies?.hpDamage)
        assertEquals(4, started.state.campaign.rules.hp)
        assertEquals(2, started.state.campaign.combat?.round)
        assertEquals(true, started.state.campaign.combat?.playerCanAct)
        random.assertAllRollsUsed()
    }

    @Test
    fun defeatedOpponentBeforeEnemyPhaseDoesNotAct() {
        val random = SequentialRandomSource(4 to 1, 4 to 2)
        val before = combatState(listOf(
            opponent("fallen", hp = 0, status = CombatOpponentStatus.DEFEATED),
            opponent("active", weapon = WeaponProfile("active-claws", "d4"))
        ))

        val result = resolver(random).resolve(before, GameAction.CombatAttack(targetOpponentId = "active"))
        val attack = result.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()

        assertEquals(listOf("active"), attack.enemyAttackRolls.map { it.opponentId })
        assertEquals(listOf(2), attack.enemyAttackRolls.map { it.damageRolled })
        assertEquals(CombatOpponentStatus.DEFEATED, result.state.campaign.combat?.opponents?.first()?.status)
        random.assertAllRollsUsed()
    }

    @Test
    fun fledOpponentBeforeEnemyPhaseDoesNotAct() {
        val random = SequentialRandomSource(4 to 1, 4 to 2)
        val before = combatState(listOf(
            opponent("fled", hp = 0, status = CombatOpponentStatus.FLED),
            opponent("active", weapon = WeaponProfile("active-claws", "d4"))
        ))

        val result = resolver(random).resolve(before, GameAction.CombatAttack(targetOpponentId = "active"))
        val attack = result.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()

        assertEquals(listOf("active"), attack.enemyAttackRolls.map { it.opponentId })
        assertEquals(listOf(2), attack.enemyAttackRolls.map { it.damageRolled })
        assertEquals(CombatOpponentStatus.FLED, result.state.campaign.combat?.opponents?.first()?.status)
        random.assertAllRollsUsed()
    }

    @Test
    fun playerCanChooseOpponentAndCombatDoesNotEndUntilAllInactive() {
        val random = SequentialRandomSource(4 to 4, 20 to 20, 20 to 4, 20 to 4, 6 to 2)
        val before = combatState(listOf(
            opponent("opponent-a", weapon = WeaponProfile("claws-a", "d6")),
            opponent("opponent-b", hp = 1, str = 1)
        ))

        val result = resolver(random).resolve(before, GameAction.CombatAttack(targetOpponentId = "opponent-b"))
        val attack = result.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()
        val remainingCombat = result.state.campaign.combat

        assertEquals("opponent-b", attack.targetOpponentId)
        assertEquals(CombatOpponentStatus.ACTIVE, remainingCombat?.opponents?.first { it.id == "opponent-a" }?.status)
        assertEquals(CombatOpponentStatus.DEFEATED, remainingCombat?.opponents?.first { it.id == "opponent-b" }?.status)
        assertEquals(listOf("opponent-a"), attack.enemyAttackRolls.map { it.opponentId })
        assertTrue(remainingCombat?.opponents?.any { it.status == CombatOpponentStatus.ACTIVE } == true)
        random.assertAllRollsUsed()
    }

    @Test
    fun twoEnemyGroupChecksBothMoraleThresholdsInOrder() {
        val random = SequentialRandomSource(4 to 4, 20 to 20, 20 to 4, 20 to 18)
        val before = combatState(
            opponents = listOf(opponent("cultist-a", hp = 1, str = 1, wil = 1), opponent("cultist-b", wil = 10)),
            moraleLeaderId = "cultist-a"
        )

        val result = resolver(random).resolve(before, GameAction.CombatAttack(targetOpponentId = "cultist-a"))
        val attack = result.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()

        assertEquals(listOf(
            CombatMoraleTrigger.FIRST_CASUALTY,
            CombatMoraleTrigger.HALF_GROUP
        ), attack.moraleOutcomes.map { it.trigger })
        assertEquals(listOf(4, 18), attack.moraleOutcomes.map { it.roll })
        assertEquals(listOf(10, 10), attack.moraleOutcomes.map { it.attributeValue })
        assertEquals(listOf("cultist-b", "cultist-b"), attack.moraleOutcomes.map { it.opponentId })
        assertEquals(listOf(false, true), attack.moraleOutcomes.map { it.fled })
        assertEquals(listOf("cultist-a"), attack.defeatedOpponentIds)
        assertEquals(listOf("cultist-b"), attack.fledOpponentIds)
        assertEquals(null, result.state.campaign.combat)
        val ended = result.events.filterIsInstance<GameEvent.CombatEnded>().single()
        assertEquals(listOf("cultist-a", "cultist-b"), ended.opponentIds)
        assertEquals(CombatEndReason.OPPONENTS_DEFEATED_AND_FLED, ended.reason)
        random.assertAllRollsUsed()
    }

    @Test
    fun threeOpponentGroupChecksHalfAtTwoDefeats() {
        val random = SequentialRandomSource(
            4 to 4, 20 to 20, 20 to 1, 20 to 1, 4 to 1, 4 to 2,
            4 to 4, 20 to 20, 20 to 2, 4 to 1
        )
        val before = combatState(listOf(
            opponent("a"), opponent("b", hp = 1, str = 1), opponent("c", hp = 1, str = 1)
        ))
        val first = resolver(random).resolve(before, GameAction.CombatAttack(targetOpponentId = "b"))
        val firstAttack = first.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()
        val second = resolver(random).resolve(first.state, GameAction.CombatAttack(targetOpponentId = "c"))
        val secondAttack = second.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()

        assertEquals(listOf("a", "c"), firstAttack.moraleOutcomes.map { it.opponentId })
        assertEquals(listOf(CombatMoraleTrigger.FIRST_CASUALTY, CombatMoraleTrigger.FIRST_CASUALTY), firstAttack.moraleOutcomes.map { it.trigger })
        assertEquals(listOf(CombatMoraleTrigger.HALF_GROUP), secondAttack.moraleOutcomes.map { it.trigger })
        assertEquals(listOf("a"), secondAttack.moraleOutcomes.map { it.opponentId })
        assertTrue(CombatMoraleTrigger.FIRST_CASUALTY in second.state.campaign.combat!!.resolvedMoraleTriggers)
        assertTrue(CombatMoraleTrigger.HALF_GROUP in second.state.campaign.combat!!.resolvedMoraleTriggers)
        assertEquals(CombatOpponentStatus.DEFEATED, second.state.campaign.combat!!.opponents.first { it.id == "b" }.status)
        assertEquals(CombatOpponentStatus.DEFEATED, second.state.campaign.combat!!.opponents.first { it.id == "c" }.status)
        assertEquals(CombatOpponentStatus.ACTIVE, second.state.campaign.combat!!.opponents.first { it.id == "a" }.status)
        random.assertAllRollsUsed()
    }

    @Test
    fun leaderDefeatedUsesSurvivorWil() {
        val random = SequentialRandomSource(4 to 4, 20 to 20, 20 to 4, 4 to 1)
        val before = combatState(
            opponents = listOf(
                opponent("leader", hp = 0, wil = 1, status = CombatOpponentStatus.DEFEATED),
                opponent("target", hp = 1, str = 1, wil = 1),
                opponent("survivor", wil = 18)
            ),
            moraleLeaderId = "leader",
            resolvedMoraleTriggers = setOf(CombatMoraleTrigger.FIRST_CASUALTY)
        )

        val result = resolver(random).resolve(before, GameAction.CombatAttack(targetOpponentId = "target"))
        val outcome = result.events.filterIsInstance<GameEvent.CombatAttackResolved>().single().moraleOutcomes.single()

        assertEquals(CombatMoraleTrigger.HALF_GROUP, outcome.trigger)
        assertEquals("survivor", outcome.opponentId)
        assertEquals(18, outcome.attributeValue)
        assertEquals(4, outcome.roll)
        assertEquals(false, outcome.fled)
        assertEquals(CombatOpponentStatus.ACTIVE, result.state.campaign.combat?.opponents?.first { it.id == "survivor" }?.status)
        random.assertAllRollsUsed()
    }

    @Test
    fun solitaryOpponentAtZeroHpChecksWilAndCanFlee() {
        val random = SequentialRandomSource(4 to 2, 20 to 18)
        val before = combatState(listOf(opponent("solitary-cultist", hp = 2, wil = 10)))

        val result = resolver(random).resolve(before, GameAction.CombatAttack(targetOpponentId = "solitary-cultist"))
        val attack = result.events.filterIsInstance<GameEvent.CombatAttackResolved>().single()
        val outcome = attack.moraleOutcomes.single()

        assertEquals(CombatMoraleTrigger.SINGLE_OPPONENT_ZERO_HP, outcome.trigger)
        assertEquals(10, outcome.attributeValue)
        assertEquals(18, outcome.roll)
        assertEquals(true, outcome.fled)
        assertEquals(emptyList(), attack.defeatedOpponentIds)
        assertEquals(listOf("solitary-cultist"), attack.fledOpponentIds)
        assertEquals(emptyList(), attack.enemyAttackRolls)
        assertEquals(2, attack.damageDealtByPlayer?.rawDamage)
        assertEquals(null, attack.damageDealtByPlayer?.scar)
        assertEquals(false, attack.damageDealtByPlayer?.dead)
        assertEquals(null, result.state.campaign.combat)
        val ended = result.events.filterIsInstance<GameEvent.CombatEnded>().single()
        assertEquals(listOf("solitary-cultist"), ended.opponentIds)
        assertEquals(CombatEndReason.OPPONENTS_FLED, ended.reason)
        random.assertAllRollsUsed()
    }
}

class RewardGameActionResolverTest {
    private fun state(
        inventory: List<InventoryItem> = emptyList(),
        gold: Int = 0,
        hp: Int = 6
    ): GameState {
        val base = newCharacter("Mara", 10, 10, 10)
        return base.copy(campaign = base.campaign.copy(
            profile = CharacterProfile(gold = gold),
            rules = base.campaign.rules.copy(hp = hp, inventory = inventory)
        ))
    }

    private fun resolver(): GameActionResolver {
        val random = FixedRandomSource(d20Value = 10)
        return GameActionResolver(ExplorationEngine(random), RulesEngine(random))
    }

    @Test
    fun creditGoldUpdatesBalanceAndRecordsEvent() {
        val initial = state(gold = 5)
        val result = resolver().resolve(initial, GameAction.AddGold("quest-pay", 12))

        assertEquals(17, result.state.campaign.profile.gold)
        assertEquals(initial.campaign.turn + 1, result.state.campaign.turn)
        assertEquals(GameEvent.GoldCredited("quest-pay", 12, 17), result.events.single())
        assertTrue("quest-pay:gold" in result.state.campaign.appliedRewardIds)
    }

    @Test
    fun creditGoldIsIdempotent() {
        val action = GameAction.AddGold("quest-pay", 12)
        val resolver = resolver()
        val paid = resolver.resolve(state(gold = 5), action)
        val replay = resolver.resolve(paid.state, action)

        assertEquals(paid.state, replay.state)
        assertTrue(replay.events.isEmpty())
    }

    @Test
    fun invalidGrantRewardRejected() {
        val invalidActions = listOf(
            GameAction.GrantReward("bad/id", 12, emptyList()),
            GameAction.GrantReward("quest-pay", -1, emptyList()),
            GameAction.GrantReward("quest-pay", 0, emptyList()),
            GameAction.GrantReward("quest-pay", 0, List(6) { "dagger" })
        )

        invalidActions.forEach { action ->
            assertFailsWith<IllegalArgumentException> { resolver().resolve(state(), action) }
        }
    }

    @Test
    fun grantRewardAppliesGoldAndItemsInOneTurn() {
        val initial = state()
        val result = resolver().resolve(initial, GameAction.GrantReward("quest-pay", 12, listOf("dagger")))

        assertEquals(12, result.state.campaign.profile.gold)
        assertEquals(initial.campaign.turn + 1, result.state.campaign.turn)
        assertEquals(1, result.state.campaign.rules.inventory.size)
        val item = result.state.campaign.rules.inventory.single()
        assertEquals("reward:quest-pay:0:dagger", item.id)
        assertTrue("reward-catalog:dagger" in item.tags)
        assertTrue(result.events.any { it == GameEvent.GoldCredited("quest-pay", 12, 12) })
        assertTrue(result.events.any { it == GameEvent.RewardItemAdded(item.id, "dagger") })
    }

    @Test
    fun grantRewardIsIdempotent() {
        val action = GameAction.GrantReward("quest-pay", 12, listOf("dagger"))
        val resolver = resolver()
        val paid = resolver.resolve(state(), action)
        val replay = resolver.resolve(paid.state, action)

        assertEquals(paid.state, replay.state)
        assertTrue(replay.events.isEmpty())
    }

    @Test
    fun grantRewardWithChangedPayloadIsNoOp() {
        val resolver = resolver()
        val paid = resolver.resolve(state(), GameAction.GrantReward("quest-pay", 12, emptyList()))
        val replay = resolver.resolve(paid.state, GameAction.GrantReward("quest-pay", 99, listOf("spear")))

        assertEquals(paid.state, replay.state)
        assertTrue(replay.events.isEmpty())
    }

    @Test
    fun fullInventoryStoresRewardAsPending() {
        val fullInventory = List(10) { InventoryItem("existing-$it") }
        val initial = state(inventory = fullInventory, hp = 0)
        val result = resolver().resolve(initial, GameAction.GrantReward("quest-pay", 12, listOf("dagger")))

        assertEquals(12, result.state.campaign.profile.gold)
        assertEquals(fullInventory, result.state.campaign.rules.inventory)
        assertEquals(1, result.state.campaign.pendingRewardItems.size)
        assertTrue(result.events.any { it is GameEvent.RewardItemPending && it.catalogItemId == "dagger" })
    }

    @Test
    fun claimPendingRewardItemAfterSpaceFreed() {
        val initial = state(inventory = List(9) { InventoryItem("existing-$it") })
        val resolver = resolver()
        val paid = resolver.resolve(initial, GameAction.GrantReward("quest-pack", 0, listOf("chainmail")))
        val pending = assertNotNull(paid.state.campaign.pendingRewardItems.singleOrNull())
        val pendingEvent = assertIs<GameEvent.RewardItemPending>(paid.events.first { it is GameEvent.RewardItemPending })
        assertEquals(2, pendingEvent.slotsRequired)
        assertEquals(1, pendingEvent.freeSlots)

        val freed = resolver.resolve(paid.state, GameAction.RemoveItem("existing-0"))
        val claimed = resolver.resolve(freed.state, GameAction.ClaimPendingRewardItem(pending.id))

        assertTrue(claimed.state.campaign.pendingRewardItems.isEmpty())
        assertEquals("reward:quest-pack:0:chainmail", claimed.state.campaign.rules.inventory.last().id)
        assertEquals(10, claimed.state.campaign.rules.usedSlots)
        assertEquals(0, claimed.state.campaign.rules.hp)
        assertTrue(claimed.events.any { it == GameEvent.RewardItemClaimed(pending.id, pending.itemInstanceId) })
    }

    @Test
    fun unsupportedItemIdDoesNotAddItem() {
        val result = resolver().resolve(state(), GameAction.GrantReward("quest-pay", 12, listOf("made-up-relic")))

        assertEquals(12, result.state.campaign.profile.gold)
        assertTrue(result.state.campaign.rules.inventory.isEmpty())
        assertTrue(result.events.any { it == GameEvent.RewardItemRejected("made-up-relic", "UNKNOWN_CATALOG_ITEM") })
    }

    @Test
    fun nonItemCatalogEntryCannotBeGranted() {
        val result = resolver().resolve(state(), GameAction.GrantReward("quest-pay", 0, listOf("horse")))

        assertTrue(result.state.campaign.rules.inventory.isEmpty())
        assertTrue(result.events.any { it == GameEvent.RewardItemRejected("horse", "NOT_AN_INVENTORY_ITEM") })
    }

    @Test
    fun grantRewardFillingTenthSlotReducesHpToZeroAccordingToCairn2e() {
        val initial = state(inventory = List(9) { InventoryItem("existing-$it") }, hp = 5)
        val result = resolver().resolve(initial, GameAction.GrantReward("quest-pay", 0, listOf("dagger")))

        assertEquals(0, result.state.campaign.rules.hp)
        assertEquals(10, result.state.campaign.rules.usedSlots)
        assertTrue(result.state.campaign.pendingRewardItems.isEmpty())
        assertEquals("reward:quest-pay:0:dagger", result.state.campaign.rules.inventory.last().id)
    }
}
