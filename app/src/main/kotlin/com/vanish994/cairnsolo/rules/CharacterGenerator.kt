package com.vanish994.cairnsolo.rules

import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CampaignState
import com.vanish994.cairnsolo.game.CharacterProfile
import com.vanish994.cairnsolo.game.CharacterIdentity
import kotlin.random.Random

class KotlinRandomSource(private val random: Random = Random.Default) : RandomSource {
    override fun roll(sides: Int): Int = random.nextInt(1, sides + 1)
}

enum class Background(val id: Int, val displayName: String) {
    AURIFEX(1, "Aurifex"), BARBER_SURGEON(2, "Barber-Surgeon"), BEAST_HANDLER(3, "Beast Handler"), BONEKEEPER(4, "Bonekeeper"), CUTPURSE(5, "Cutpurse"), FIELDWARDEN(6, "Fieldwarden"), FLETCHWIND(7, "Fletchwind"), FOUNDLING(8, "Foundling"), FUNGAL_FORAGER(9, "Fungal Forager"), GREENWISE(10, "Greenwise"), HALF_WITCH(11, "Half-Witch"), HEXENBANE(12, "Hexenbane"), JONGLEUR(13, "Jongleur"), KETTLEWRIGHT(14, "Kettlewright"), MARCHGUARD(15, "Marchguard"), MOUNTEBANK(16, "Mountebank"), OUTRIDER(17, "Outrider"), PROWLER(18, "Prowler"), RILL_RUNNER(19, "Rill Runner"), SCRIVENER(20, "Scrivener");

    companion object {
        fun fromD20(roll: Int): Background = entries.first { it.id == roll }
    }
}

data class CharacterTraits(
    val physique: String,
    val skin: String,
    val hair: String,
    val face: String,
    val speech: String,
    val clothing: String,
    val virtue: String,
    val vice: String
)

data class RolledCharacter(
    val str: Int,
    val dex: Int,
    val wil: Int,
    val hp: Int,
    val background: Background? = null,
    val traits: CharacterTraits? = null,
    val age: Int? = null,
    val gold: Int = 0,
    val bondRoll: Int? = null
)

fun rollCharacter(random: RandomSource): RolledCharacter =
    RolledCharacter(
        str = random.d6() + random.d6() + random.d6(),
        dex = random.d6() + random.d6() + random.d6(),
        wil = random.d6() + random.d6() + random.d6(),
        hp = random.d6(),
        background = Background.fromD20(random.d20()),
        traits = rollTraits(random),
        age = rollAge(random).years,
        gold = random.d6() + random.d6() + random.d6(),
        bondRoll = random.d20()
    )

fun RolledCharacter.swapAttributes(first: AttributeSlot, second: AttributeSlot): RolledCharacter {
    require(first != second)
    val values = listOf(str, dex, wil).toMutableList()
    val firstIndex = first.ordinal
    val secondIndex = second.ordinal
    val temp = values[firstIndex]
    values[firstIndex] = values[secondIndex]
    values[secondIndex] = temp
    return copy(str = values[0], dex = values[1], wil = values[2])
}

enum class AttributeSlot { STR, DEX, WIL }

fun createCharacter(name: String, rolled: RolledCharacter): GameState {
    require(name.isNotBlank())
    return GameState(
        campaign = CampaignState(
            character = CharacterIdentity(name = name.trim()),
            profile = CharacterProfile(
                age = rolled.age,
                background = rolled.background,
                traits = rolled.traits,
                gold = rolled.gold,
                bondRoll = rolled.bondRoll
            ),
            rules = CharacterState(
                str = rolled.str,
                dex = rolled.dex,
                wil = rolled.wil,
                hp = rolled.hp,
                maxHp = rolled.hp,
                armor = 0,
                inventory = startingInventory()
            )
        )
    )
}


fun rollTraits(random: RandomSource): CharacterTraits {
    fun pick(values: List<String>): String = values[random.d10() - 1]
    return CharacterTraits(
        physique = pick(listOf("Athletic","Brawny","Flabby","Lanky","Rugged","Scrawny","Short","Statuesque","Stout","Towering")),
        skin = pick(listOf("Birthmarked","Marked","Oily","Rosy","Scarred","Soft","Tanned","Tattooed","Weathered","Webbed")),
        hair = pick(listOf("Bald","Braided","Curly","Filthy","Frizzy","Long","Luxurious","Oily","Wavy","Wispy")),
        face = pick(listOf("Bony","Broken","Chiseled","Elongated","Pale","Perfect","Rakish","Sharp","Square","Sunken")),
        speech = pick(listOf("Blunt","Booming","Cryptic","Droning","Formal","Gravelly","Precise","Squeaky","Stuttering","Whispery")),
        clothing = pick(listOf("Antique","Bloody","Elegant","Filthy","Foreign","Frayed","Frumpy","Livery","Rancid","Soiled")),
        virtue = pick(listOf("Ambitious","Cautious","Courageous","Disciplined","Gregarious","Honorable","Humble","Merciful","Serene","Tolerant")),
        vice = pick(listOf("Aggressive","Bitter","Craven","Deceitful","Greedy","Lazy","Nervous","Rude","Vain","Vengeful"))
    )
}

fun RandomSource.d10(): Int = roll(10)

data class CharacterAge(val years: Int)

fun rollAge(random: RandomSource): CharacterAge =
    CharacterAge(random.d20() + random.d20() + 10)


fun startingInventory(): List<InventoryItem> = listOf(
    InventoryItem("mochila", slots = 1),
    InventoryItem("racoes-3-dias", slots = 1),
    InventoryItem("tocha", slots = 1)
)
