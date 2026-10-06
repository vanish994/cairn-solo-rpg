package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
