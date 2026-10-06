package com.vanish994.cairnsolo.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GameStateTest {
    @Test
    fun newCharacterCreatesValidCampaign() {
        val state = newCharacter("Aran", 10, 11, 9)
        assertEquals("Aran", state.campaign.character.name)
        assertEquals(10, state.campaign.rules.str)
        assertEquals(11, state.campaign.rules.dex)
        assertEquals(9, state.campaign.rules.wil)
        assertEquals(6, state.campaign.rules.hp)
        assertEquals(0, state.campaign.turn)
    }

    @Test
    fun withRulesAdvancesTurnAndPreservesCharacter() {
        val state = newCharacter("Aran", 10, 10, 10)
        val next = state.withRules(state.campaign.rules.copy(hp = 4))
        assertEquals("Aran", next.campaign.character.name)
        assertEquals(4, next.campaign.rules.hp)
        assertEquals(1, next.campaign.turn)
        assertNotEquals(state.updatedAtEpochMs, -1L)
    }
}
