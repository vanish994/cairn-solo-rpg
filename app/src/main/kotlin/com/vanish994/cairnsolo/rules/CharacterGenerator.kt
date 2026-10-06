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
    AURIFEX(1, "Aurifex"),
    BARBER_SURGEON(2, "Barber-Surgeon"),
    BEAST_HANDLER(3, "Beast Handler"),
    BONEKEEPER(4, "Bonekeeper"),
    CUTPURSE(5, "Cutpurse"),
    FIELDWARDEN(6, "Fieldwarden"),
    FLETCHWIND(7, "Fletchwind"),
    FOUNDLING(8, "Foundling"),
    FUNGAL_FORAGER(9, "Fungal Forager"),
    GREENWISE(10, "Greenwise"),
    HALF_WITCH(11, "Half Witch"),
    HEXENBANE(12, "Hexenbane"),
    JONGLEUR(13, "Jongleur"),
    KETTLEWRIGHT(14, "Kettlewright"),
    MARCHGUARD(15, "Marchguard"),
    MOUNTEBANK(16, "Mountebank"),
    OUTRIDER(17, "Outrider"),
    PROWLER(18, "Prowler"),
    RILL_RUNNER(19, "Rill Runner"),
    SCRIVENER(20, "Scrivener");

    companion object {
        fun fromD20(roll: Int): Background = entries.first { it.id == roll }
    }
}

data class StartingGear(
    val id: String,
    val slots: Int = 1,
    val petty: Boolean = false
) {
    fun toInventoryItem(): InventoryItem = InventoryItem(id = id, slots = slots, petty = petty)
}

fun startingGear(background: Background): List<StartingGear> = when (background) {
    Background.AURIFEX -> listOf(g("rations-3-uses"), g("lantern"), g("oil-can-6-uses"), g("needle-knife"), g("protective-gloves", petty = true))
    Background.BARBER_SURGEON -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("amputation-knife"), g("bandages-3-uses"), g("leech-3-uses"), g("stained-medical-finery", petty = true))
    Background.BEAST_HANDLER -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("leather-whip"), g("soporific-darts"), g("lure"), g("rope-25ft"))
    Background.BONEKEEPER -> listOf(g("rations-3-uses"), g("lantern"), g("oil-can-6-uses"), g("stake"), g("chains-10ft"))
    Background.CUTPURSE -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("twin-daggers", slots = 2), g("padded-leather"), g("lockpicks"), g("black-outfit", petty = true))
    Background.FIELDWARDEN -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("brigandine", slots = 2), g("sling"), g("hand-axe"), g("repellent-3-uses"))
    Background.FLETCHWIND -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("bow"), g("serrated-knife"), g("boiled-leather"), g("heartroot-salve"))
    Background.FOUNDLING -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("salt-pouch"), g("heirloom-amulet", petty = true), g("sling"), g("dagger"))
    Background.FUNGAL_FORAGER -> listOf(g("rations-3-uses"), g("sharpened-trowel"), g("candle-helmet"), g("rope-25ft"), g("metal-pail"))
    Background.GREENWISE -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("iron-pot"), g("root-knife"), g("healing-salve"), g("twine-bauble", petty = true))
    Background.HALF_WITCH -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("spellbook"), g("iron-dagger"), g("herbs-pouch-3-uses"), g("ghillie-suit"))
    Background.HEXENBANE -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("vestments-of-the-order", petty = true), g("blessed-tinctures"), g("silver-knife"), g("crossbow", slots = 2))
    Background.JONGLEUR -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("costume"), g("simple-instrument"), g("lucky-jerkin"), g("sling"))
    Background.KETTLEWRIGHT -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("pincers"), g("roll-of-tin"), g("gloves", petty = true), g("hammer"))
    Background.MARCHGUARD -> listOf(g("rations-3-uses"), g("lantern"), g("oil-can-6-uses"), g("long-sword", slots = 2), g("boiled-leather"))
    Background.MOUNTEBANK -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("cart", slots = 2), g("trick-playing-cards"), g("fancy-hat", petty = true), g("cane-sword"))
    Background.OUTRIDER -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("long-sword", slots = 2), g("leather-jerkin"), g("crossbow", slots = 2), g("spyglass"))
    Background.PROWLER -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("tarp"), g("boiled-leather"), g("short-sword"), g("spring-loaded-trap"))
    Background.RILL_RUNNER -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("water-shoes"), g("brigandine", slots = 2), g("compass"), g("dagger"))
    Background.SCRIVENER -> listOf(g("rations-3-uses"), g("torch-3-uses"), g("quill-and-ink"), g("blank-book"), g("awl"), g("badge", petty = true))
}

private fun g(id: String, slots: Int = 1, petty: Boolean = false) = StartingGear(id, slots, petty)

fun startingArmor(background: Background): Int = when (background) {
    Background.CUTPURSE, Background.FIELDWARDEN, Background.FLETCHWIND,
    Background.FUNGAL_FORAGER, Background.JONGLEUR, Background.MARCHGUARD,
    Background.OUTRIDER, Background.PROWLER, Background.RILL_RUNNER -> 1
    else -> 0
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
                armor = rolled.background?.let(::startingArmor) ?: 0,
                inventory = rolled.background?.let(::startingGear)?.map(StartingGear::toInventoryItem) ?: emptyList()
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



