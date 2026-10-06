package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    fun fullCharacterRollIncludesBackgroundTraitsAndAge() {
        val result = rollCharacter(FixedRandomSource(4))
        assertNotNull(result.background)
        assertNotNull(result.traits)
        assertNotNull(result.age)
        assertEquals(18, result.age)
    }

    @Test
    fun backgroundRollsAreRolledAndPersisted() {
        val result = rollCharacter(FixedRandomSource(4))
        assertEquals(4, result.backgroundRolls?.first)
        assertEquals(4, result.backgroundRolls?.second)

        val state = createCharacter("Aran", result)
        assertEquals(4, state.campaign.profile.backgroundRolls?.first)
        assertEquals(4, state.campaign.profile.backgroundRolls?.second)
    }

    @Test
    fun attributesCanBeSwappedAfterRolling() {
        val result = RolledCharacter(8, 12, 15, 4)
            .swapAttributes(AttributeSlot.STR, AttributeSlot.WIL)
        assertEquals(15, result.str)
        assertEquals(12, result.dex)
        assertEquals(8, result.wil)
        assertEquals(4, result.hp)
    }

    @Test
    fun createdCharacterUsesRolledHpAsMaxHp() {
        val state = createCharacter("Aran", RolledCharacter(9, 11, 13, 5))
        assertEquals(5, state.campaign.rules.hp)
        assertEquals(5, state.campaign.rules.maxHp)
        assertEquals("Aran", state.campaign.character.name)
        assertEquals(0, state.campaign.rules.inventory.size)
    }


    @Test
    fun aurifexUsesOfficialStartingGear() {
        val state = createCharacter("Jazia", RolledCharacter(9, 11, 13, 5, Background.AURIFEX))
        assertEquals(0, state.campaign.rules.armor)
        assertEquals(
            listOf("rations-3-uses", "lantern", "oil-can-6-uses", "needle-knife", "protective-gloves"),
            state.campaign.rules.inventory.map { it.id }
        )
    }

    @Test
    fun cutpurseUsesOfficialGearAndOneArmor() {
        val state = createCharacter("Patch", RolledCharacter(9, 11, 13, 5, Background.CUTPURSE))
        assertEquals(1, state.campaign.rules.armor)
        assertEquals(2, state.campaign.rules.inventory.first { it.id == "twin-daggers" }.slots)
        assertEquals(0, state.campaign.rules.inventory.first { it.id == "black-outfit" }.slotCost)
    }

    @Test
    fun everyBackgroundFitsTheTenSlotStartingInventory() {
        for (background in Background.entries) {
            val state = createCharacter("Tester", RolledCharacter(9, 11, 13, 5, background))
            assertTrue(state.campaign.rules.usedSlots <= 10, background.displayName)
        }
    }

    @Test
    fun createdCharacterPersistsProfileMetadata() {
        val rolled = RolledCharacter(
            9, 11, 13, 5,
            background = Background.SCRIVENER,
            traits = CharacterTraits("Athletic", "Tanned", "Long", "Sharp", "Precise", "Frayed", "Cautious", "Greedy"),
            age = 27,
            gold = 12,
            bondRoll = 7
        )
        val state = createCharacter("  Aran  ", rolled)
        assertEquals("Aran", state.campaign.character.name)
        assertEquals(27, state.campaign.profile.age)
        assertEquals(Background.SCRIVENER, state.campaign.profile.background)
        assertNotNull(state.campaign.profile.traits)
        assertEquals("Greedy", state.campaign.profile.traits?.vice)
        assertEquals(12, state.campaign.profile.gold)
        assertEquals(7, state.campaign.profile.bondRoll)
    }

    @Test
    fun mjContextExposesReadOnlyProfile() {
        val rolled = RolledCharacter(9, 11, 13, 5, Background.PROWLER, null, 31)
        val context = MJContext.from(createCharacter("Aran", rolled))
        assertEquals(31, context.profile.age)
        assertEquals(Background.PROWLER, context.profile.background)
    }

    @Test
    fun backgroundTableRollsResolveToStableMechanicalKeys() {
        val state = createCharacter(
            "Aran",
            RolledCharacter(
                9, 11, 13, 5,
                background = Background.AURIFEX,
                backgroundRolls = BackgroundRolls(1, 6)
            )
        )
        assertEquals(listOf("gold_scent", "homunculus"), state.campaign.profile.backgroundFeatures)
    }

    @Test
    fun everyBackgroundHasTwelveCataloguedOutcomes() {
        for (background in Background.entries) {
            val outcomes = BACKGROUND_OUTCOME_KEYS[background]
            assertEquals(12, outcomes?.size, background.displayName)
            for (roll in 1..6) {
                assertNotNull(backgroundOutcomes(background, BackgroundRolls(roll, roll)).first())
                assertNotNull(backgroundOutcomes(background, BackgroundRolls(roll, roll)).second())
            }
        }
    }


    @Test
    fun directBackgroundCreationEffectsAreResolved() {
        val fieldwarden = createCharacter(
            "Field",
            RolledCharacter(
                9, 11, 13, 5,
                background = Background.FIELDWARDEN,
                backgroundRolls = BackgroundRolls(6, 1)
            )
        )
        assertEquals(5, fieldwarden.campaign.rules.maxHp)

        val outrider = createCharacter(
            "Rider",
            RolledCharacter(
                9, 11, 13, 5,
                background = Background.OUTRIDER,
                backgroundRolls = BackgroundRolls(4, 1)
            )
        )
        assertEquals(0, outrider.campaign.profile.gold)
    }

    @Test
    fun backgroundSecondaryRollsPersist() {
        val rolled = RolledCharacter(
            9, 11, 13, 5,
            background = Background.OUTRIDER,
            secondBondRoll = 17,
            omenRoll = 9
        )
        val state = createCharacter("Aran", rolled)
        assertEquals(17, state.campaign.profile.secondBondRoll)
        assertEquals(9, state.campaign.profile.omenRoll)
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
