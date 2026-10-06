package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.RulesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
