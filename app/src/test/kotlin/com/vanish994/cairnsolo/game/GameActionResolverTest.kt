package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.FixedRandomSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GameActionResolverTest {
    private fun state(): GameState =
        GameState(
            campaign = CampaignState(
                character = CharacterIdentity(name = "Tester"),
                rules = CharacterState(
                    str = 10,
                    dex = 10,
                    wil = 10,
                    hp = 6,
                    maxHp = 6,
                    armor = 0
                )
            )
        )

    @Test
    fun continueExplorationReturnsAuthoritativeStateAndEvent() {
        val resolver = GameActionResolver(
            ExplorationEngine(FixedRandomSource(d20Value = 10, d6Value = 1))
        )
        val result = resolver.resolve(state(), GameAction.ExploreContinue)
        assertEquals("old_road", result.state.campaign.sceneId)
        assertEquals(1L, result.state.campaign.turn)
        assertIs<GameEvent.SceneAdvanced>(result.events.single())
    }

    @Test
    fun investigatePreservesSceneWhileAdvancingCampaignTurn() {
        val resolver = GameActionResolver(
            ExplorationEngine(FixedRandomSource(d20Value = 10, d6Value = 5))
        )
        val result = resolver.resolve(state(), GameAction.ExploreInvestigate)
        assertEquals("prologue", result.state.campaign.sceneId)
        assertEquals(1L, result.state.campaign.turn)
        assertIs<GameEvent.SceneInvestigated>(result.events.single())
    }
}
