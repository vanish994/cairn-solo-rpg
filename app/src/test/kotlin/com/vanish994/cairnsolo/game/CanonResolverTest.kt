package com.vanish994.cairnsolo.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CanonResolverTest {
    @Test
    fun appliesNarrativeProposalAndRecordsHistory() {
        val state = newCharacter("Mira", 10, 12, 9)
        val next = CanonResolver().apply(
            state,
            listOf(
                CanonProposal.UpsertNpc(
                    id = "npc-edran",
                    name = "Edran",
                    role = "arquivista",
                    status = CanonStatus.CONFIRMED,
                    source = CanonSource.NPC
                )
            )
        )

        assertEquals("Edran", next.campaign.worldCanon.npcs.single().name)
        assertEquals(HistoryEventType.CANON_UPDATED, next.campaign.history.single().type)
    }

    @Test
    fun rejectsInvalidIds() {
        val state = newCharacter("Mira", 10, 12, 9)
        assertFails {
            CanonResolver().apply(
                state,
                listOf(CanonProposal.AddDiscovery("INVALID ID", "fato", CanonStatus.CONFIRMED, CanonSource.GUARDIAN))
            )
        }
    }

    @Test
    fun persistenceRoundTripPreservesCanonAndHistory() {
        val state = CanonResolver().apply(
            newCharacter("Mira", 10, 12, 9),
            listOf(CanonProposal.DiscoverLocation("location-tower", "Torre", "Uma torre antiga", CanonStatus.DISCOVERED, CanonSource.PLAYER))
        )
        val restored = GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(state))
        assertEquals(state.campaign.worldCanon, restored?.campaign?.worldCanon)
        assertEquals(state.campaign.history, restored?.campaign?.history)
    }
}
