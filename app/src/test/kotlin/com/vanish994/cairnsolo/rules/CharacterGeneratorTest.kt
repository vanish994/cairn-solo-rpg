package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import com.vanish994.cairnsolo.game.MJContext

class CharacterGeneratorTest {
    @Test
    fun fixedDiceProduceThreeD6AttributesAndOneD6Hp() {
        val result = rollCharacter(FixedRandomSource(10, 4))
        assertEquals(12, result.str)
        assertEquals(12, result.dex)
        assertEquals(12, result.wil)
        assertEquals(4, result.hp)
    }

    @Test
    fun createdCharacterUsesRolledHpAsMaxHp() {
        val state = createCharacter("Aran", RolledCharacter(9, 11, 13, 5))
        assertEquals(5, state.campaign.rules.hp)
        assertEquals(5, state.campaign.rules.maxHp)
        assertEquals("Aran", state.campaign.character.name)
    }

    @Test
    fun createdCharacterPersistsProfileMetadata() {
        val rolled = RolledCharacter(
            9, 11, 13, 5,
            background = Background.SCRIVENER,
            traits = CharacterTraits("Athletic", "Tanned", "Long", "Sharp", "Precise", "Frayed", "Cautious", "Greedy"),
            age = 27
        )
        val state = createCharacter("  Aran  ", rolled)
        assertEquals("Aran", state.campaign.character.name)
        assertEquals(27, state.campaign.profile.age)
        assertEquals(Background.SCRIVENER, state.campaign.profile.background)
        assertNotNull(state.campaign.profile.traits)
        assertEquals("Greedy", state.campaign.profile.traits?.vice)
    }

    @Test
    fun mjContextExposesReadOnlyProfile() {
        val rolled = RolledCharacter(9, 11, 13, 5, Background.PROWLER, null, 31)
        val context = MJContext.from(createCharacter("Aran", rolled))
        assertEquals(31, context.profile.age)
        assertEquals(Background.PROWLER, context.profile.background)
    }

    @Test
    fun backgroundUsesD20Range() {
        assertEquals(Background.AURIFEX, Background.fromD20(1))
        assertEquals(Background.SCRIVENER, Background.fromD20(20))
    }

    @Test
    fun backgroundRejectsInvalidRoll() {
        assertFailsWith<NoSuchElementException> { Background.fromD20(0) }
        assertFailsWith<NoSuchElementException> { Background.fromD20(21) }
    }
}
