package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.FixedRandomSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExplorationEngineTest {
    private fun state() = GameState(
        campaign = CampaignState(
            character = CharacterIdentity(name = "Teste"),
            rules = CharacterState(10, 10, 10, 6, 6, 0)
        )
    )

    @Test
    fun continueChangesSceneDeterministically() {
        val result = ExplorationEngine(FixedRandomSource(1)).resolve(state(), ExplorationAction.CONTINUE)
        assertEquals("old_road", result.state.campaign.sceneId)
        assertEquals(1, result.state.campaign.turn)
        assertTrue(result.events.single() is ExplorationEvent.Advanced)
    }

    @Test
    fun investigateKeepsSceneAndAddsLog() {
        val result = ExplorationEngine(FixedRandomSource(5)).resolve(state(), ExplorationAction.INVESTIGATE)
        assertEquals("prologue", result.state.campaign.sceneId)
        assertEquals(1, result.state.campaign.turn)
        assertEquals(1, result.state.campaign.log.size)
    }
}
