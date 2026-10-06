package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.Background
import com.vanish994.cairnsolo.rules.CharacterTraits
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.Scar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

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

    @Test
    fun persistenceCodecRoundTripPreservesFullCharacterState() {
        val original = GameState(
            campaign = CampaignState(
                campaignId = "campaign-1",
                character = CharacterIdentity(id = "character-1", name = "Mara"),
                rules = CharacterState(
                    str = 7, dex = 12, wil = 9, hp = 0, maxHp = 8, armor = 3,
                    inventory = listOf(InventoryItem("torch", 1), InventoryItem("coin", 0, petty = true)),
                    fatigue = 1, critical = true, scar = Scar.BROKEN_LIMB, scarRecovery = com.vanish994.cairnsolo.rules.ScarRecovery.BROKEN_LIMB, scarAttribute = com.vanish994.cairnsolo.rules.Attribute.DEX,
                    maxStr = 10, maxDex = 14, maxWil = 11,
                    lastingScar = "old wound", brokenLimb = "left arm",
                    sundered = true, deafened = true, diseased = true, hamstrung = true
                ),
                profile = CharacterProfile(
                    age = 34,
                    background = Background.PROWLER,
                    traits = CharacterTraits("Rugged", "Tanned", "Braided", "Sharp", "Blunt", "Frayed", "Cautious", "Greedy"),
                    gold = 12,
                    bondRoll = 7
                ),
                sceneId = "ruined_shrine", turn = 42L,
                sceneType = SceneType.EXPLORATION, sceneTitle = "Ruined Shrine",
                sceneDescription = "Uma capela em ruínas.",
                exits = listOf("continuar", "investigar"),
                log = listOf("Chegou ao santuário.", "Encontrou uma pista.")
            ),
            updatedAtEpochMs = 123456789L
        )

        val restored = assertNotNull(GameStatePersistenceCodec.decode(GameStatePersistenceCodec.encode(original)))
        assertEquals(original, restored)
    }

    @Test
    fun legacyPersistenceDefaultsNewFieldsSafely() {
        val restored = assertNotNull(
            GameStatePersistenceCodec.decode(
                mapOf(
                    "campaignId" to "legacy", "characterId" to "char", "characterName" to "Legacy",
                    "str" to "10", "dex" to "10", "wil" to "10", "hp" to "6", "maxHp" to "6",
                    "armor" to "0", "inventoryCount" to "0"
                )
            )
        )

        assertEquals(10, restored.campaign.rules.maxStr)
        assertEquals(10, restored.campaign.rules.maxDex)
        assertEquals(10, restored.campaign.rules.maxWil)
        assertEquals(null, restored.campaign.rules.lastingScar)
        assertEquals(false, restored.campaign.rules.sundered)
        assertEquals(null, restored.campaign.rules.scarRecovery)
        assertEquals(null, restored.campaign.rules.scarAttribute)
        assertEquals(null, restored.campaign.profile.age)
        assertEquals(null, restored.campaign.profile.background)
        assertEquals(null, restored.campaign.profile.traits)
        assertEquals(0, restored.campaign.profile.gold)
        assertEquals(null, restored.campaign.profile.bondRoll)
    }
}
