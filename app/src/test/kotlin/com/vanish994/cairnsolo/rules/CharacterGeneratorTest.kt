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
        val result = rollCharacter(FixedRandomSource(4, d6Value = 4))
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
        val rolled = RolledCharacter(9, 11, 13, 5, background = Background.PROWLER, age = 31)
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
            for (roll in 1..6) {
                val outcomes = backgroundOutcomes(background, BackgroundRolls(roll, roll))
                assertEquals(2, outcomes.size, background.displayName)
                assertNotNull(outcomes.first().key)
                assertNotNull(outcomes[1].key)
            }
        }
    }


    @Test
    fun directBackgroundCreationEffectsAreResolved() {
        val effects = backgroundCreationEffects(
            Background.FIELDWARDEN,
            BackgroundRolls(6, 1),
            FixedRandomSource(3, d4Value = 3)
        )
        assertEquals(3, effects.bonusHp)

        val gold = backgroundCreationEffects(
            Background.OUTRIDER,
            BackgroundRolls(4, 1),
            FixedRandomSource(3)
        )
        assertEquals(30, gold.bonusGold)

        val bonds = backgroundCreationEffects(
            Background.OUTRIDER,
            BackgroundRolls(6, 1),
            FixedRandomSource(17)
        )
        assertEquals(17, bonds.secondBondRoll)
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
    fun startingGearCarriesMechanicalMetadata() {
        val cutpurse = createCharacter("Patch", RolledCharacter(9, 11, 13, 5, Background.CUTPURSE))
        val daggers = cutpurse.campaign.rules.inventory.first { it.id == "twin-daggers" }
        assertEquals("d6+d6", daggers.damage)
        assertEquals(setOf("paired"), daggers.tags)
        assertEquals(1, cutpurse.campaign.rules.inventory.first { it.id == "padded-leather" }.armor)

        val outrider = createCharacter("Rider", RolledCharacter(9, 11, 13, 5, Background.OUTRIDER))
        assertEquals("d10", outrider.campaign.rules.inventory.first { it.id == "long-sword" }.damage)
        assertEquals("d8", outrider.campaign.rules.inventory.first { it.id == "crossbow" }.damage)

        val mountaineer = createCharacter("Bank", RolledCharacter(9, 11, 13, 5, Background.MOUNTEBANK))
        assertEquals(setOf("capacity:+4", "bulky-when-pulled"), mountaineer.campaign.rules.inventory.first { it.id == "cart" }.tags)
    }

    @Test
    fun backgroundCompanionsBecomeStructuredState() {
        val fletch = rollCharacter(FixedRandomSource(7, d6Value = 2))
        val state = createCharacter("Fletch", fletch)
        assertEquals("falcon", state.campaign.profile.companions.single().id)

        val rider = rollCharacter(FixedRandomSource(17, d6Value = 1))
        val riderState = createCharacter("Rider", rider)
        assertEquals(emptyList(), riderState.campaign.profile.companions)
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
