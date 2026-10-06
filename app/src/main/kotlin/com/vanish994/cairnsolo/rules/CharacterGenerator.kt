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


/** Mechanical keys for the two official Background tables. */
data class BackgroundOutcome(val table: Int, val roll: Int, val key: String)

private val BACKGROUND_OUTCOME_KEYS: Map<Background, List<String>> = mapOf(
Background.AURIFEX to listOf("gold_scent","invisible_pet","truth_serum","fake_gold","blunderbuss","universal_solvent","pyrophoric_gel","blast_sphere","aqua_vita","mimic_stone","spark_dust","homunculus"),
Background.BARBER_SURGEON to listOf("magnifying_eye","metal_foot","gold_finger","enhanced_hearing","sigil_armor","metal_arm","regrowth_salve","graftgrub","woundwax","quicksilver","pneuma_pump","lodestone"),
Background.BEAST_HANDLER to listOf("arachnid_tools","feline_whiskerwort","canine_net","bird_whistle","rodent_windpipe","serpent_warming_stone","borrow_senses","beast_weather","predator_sense","specialty_terrain","beast_warning","daily_beast_feature"),
Background.BONEKEEPER to listOf("ask_dead","no_air_or_food","blood_servant","burial_wagon_donkey","detect_magic","plague_mask","manacles","undrying_sponge","pulley","incense","crowbar","repellent"),
Background.CUTPURSE to listOf("fence_cutters","visible_brand","ladder_blinding_powder","arcane_eye","silk_rope","smoke_pellets","catring","gildfinger","glimpse_glass","sweetwhistle","vagrants_veil","reverse_teetotum"),
Background.FIELDWARDEN to listOf("gale_seed","fireseeds","blood_knife","diseased_crop","werewolf","extra_hp_falchion","bloodvine_whip","clatter_keeper","sun_stick","root_tether","greenwhistle","everbloom_band"),
Background.FLETCHWIND to listOf("war_bow","falcon","better_supply","immobile_bow","first_strike_defense","better_travel","western_yew","sessile_oak","stone_pine","white_ash","striped_bamboo","wych_elm"),
Background.FOUNDLING to listOf("longbow_jerkin","healing_unguent","gnarled_staff","chainmail","storybook_dagger","control_plants","pipeweed","stink_jar","ivy_worm","dream_stone","drowning_rod","rabbit_foot"),
Background.FUNGAL_FORAGER to listOf("shrieking_trumpet","torch_fungus","murderous_truffle","hellcap","sproutcup","rootflower","glowsnail","silk_moth_shawl","milkflower","luxcompass","sloth_tarp","miners_grease"),
Background.GREENWISE to listOf("bezoar","soporific","antitoxin","plant_nourishment","prize_warrant","full_heal","amadou","delphinium","tacky_stalk","wisp_lantern","seed_bomb","briarvine"),
Background.HALF_WITCH to listOf("black_rose_fiddle","paper_legs","living_nightmare","raven_familiar","briar_thorn","true_name","rebirth_ash","glamour_feather","hawthorn_seed","stonetree_sap","nightdust","hex_stone"),
Background.HEXENBANE to listOf("leyfinder","star_iron_mace","glass_sigil","voidglass","wolf_chain","quell_stone","honesty_vow","disassemble_vow","selflessness_vow","mercy_vow","charity_vow","valor_vow"),
Background.JONGLEUR to listOf("rapier_identity","read_mind","astronomy","rhyming","stage_mail","puppet_rabbit_skull","false_cuffs","pocket_theatre","ghost_violin","tragic_tales","mythos_mask","rebreak_glass"),
Background.KETTLEWRIGHT to listOf("guild_contraptions","traveler_cant","smelting_hammer","mirrorwalk","extra_hp_gambeson","donkey_crossbow_saw","fire_eggs","black_tar","spiked_boots","tinker_paste","fireworks","carrion_cat"),
Background.MARCHGUARD to listOf("supply_training","safehouse_lockpicks","goosefelt_tarp","extra_rations_knives","snare_sketchbook","oilskin_map","guard_pin","oath_compass","pullstones","fireflask","pain_band","poachers_woe"),
Background.MOUNTEBANK to listOf("healing_knack","beauty_cream","omen_knife","captain_uniform","wild_magic","auditory_illusion","royal_crest","miracle_oil","surgeon_soap","goat_powder","cursed_sapphire","alchemical_tattoo"),
Background.OUTRIDER to listOf("buckler","whetstone","death_whistle","extra_gold","tally_stick","second_bond","destrier","blacklegged_dandy","rivertooth","piebald_cob","linden_white","stray_fogger"),
Background.PROWLER to listOf("alchemical_limb","rime_seed","stalking_tooth","heartseed_bracers","tunneling_wolf","paring_knife_gold","fermented_spirits","trail_shaker","drowse_balm","spike_cord","iron_rattle","hardening_glue"),
Background.RILL_RUNNER to listOf("reed_whistle","breeze_knot","celestial_lute","river_twine","stone_flute","map_quill","performance_gold","bodyguard_rapier","trade_goods","town_contacts","sailors_friend","journey_map"),
Background.SCRIVENER to listOf("wild_tongue","silent_symphony","abyss_treatise","star_waltz","cathedral_canopy","garden_of_glass","fib_ink","cipher_stone","everquill","whisper_vial","sanguine_lens","echo_leaf")
)

fun backgroundOutcomes(background: Background, rolls: BackgroundRolls): List<BackgroundOutcome> = listOf(
    BackgroundOutcome(1, rolls.first, BACKGROUND_OUTCOME_KEYS.getValue(background)[rolls.first - 1]),
    BackgroundOutcome(2, rolls.second, BACKGROUND_OUTCOME_KEYS.getValue(background)[5 + rolls.second])
)

data class RolledCharacter(
    val str: Int,
    val dex: Int,
    val wil: Int,
    val hp: Int,
    val background: Background? = null,
    val backgroundRolls: BackgroundRolls? = null,
    val traits: CharacterTraits? = null,
    val age: Int? = null,
    val gold: Int = 0,
    val bondRoll: Int? = null,
    val secondBondRoll: Int? = null,
    val omenRoll: Int? = null
)

fun rollCharacter(random: RandomSource): RolledCharacter {
    val str = random.d6() + random.d6() + random.d6()
    val dex = random.d6() + random.d6() + random.d6()
    val wil = random.d6() + random.d6() + random.d6()
    val hp = random.d6()
    val background = Background.fromD20(random.d20())
    val backgroundRolls = BackgroundRolls(random.d6(), random.d6())
    val traits = rollTraits(random)
    val age = rollAge(random).years
    val gold = random.d6() + random.d6() + random.d6()
    val bondRoll = random.d20()
    val firstEffects = backgroundCreationEffects(background, backgroundRolls, random)
    return RolledCharacter(
        str = str,
        dex = dex,
        wil = wil,
        hp = hp + firstEffects.bonusHp,
        background = background,
        backgroundRolls = backgroundRolls,
        traits = traits,
        age = age,
        gold = gold + firstEffects.bonusGold,
        bondRoll = bondRoll,
        secondBondRoll = firstEffects.secondBondRoll,
        omenRoll = firstEffects.omenRoll
    )
}

data class BackgroundCreationEffects(
    val bonusHp: Int = 0,
    val bonusGold: Int = 0,
    val secondBondRoll: Int? = null,
    val omenRoll: Int? = null
)

fun backgroundCreationEffects(
    background: Background,
    rolls: BackgroundRolls,
    random: RandomSource
): BackgroundCreationEffects {
    val keys = backgroundOutcomes(background, rolls).map { it.key }
    val extraHp = when {
        "extra_hp_falchion" in keys || "extra_hp_gambeson" in keys -> random.roll(4)
        else -> 0
    }
    val extraGold = when {
        "extra_gold" in keys -> 30
        "fence_cutters" in keys || "paring_knife_gold" in keys -> 20
        "guild_contraptions" in keys -> 40
        "prize_warrant" in keys || "tragic_tales" in keys || "star_waltz" in keys -> 100
        "performance_gold" in keys -> random.roll(6)
        else -> 0
    }
    val secondBond = if ("second_bond" in keys) random.d20() else null
    val omen = if (
        "longbow_jerkin" in keys ||
        "healing_unguent" in keys ||
        "gnarled_staff" in keys ||
        "chainmail" in keys ||
        "storybook_dagger" in keys ||
        "control_plants" in keys ||
        "omen_knife" in keys
    ) random.d20() else null
    return BackgroundCreationEffects(
        bonusHp = extraHp,
        bonusGold = extraGold,
        secondBondRoll = secondBond,
        omenRoll = omen
    )
}

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
                bondRoll = rolled.bondRoll,
                secondBondRoll = rolled.secondBondRoll,
                omenRoll = rolled.omenRoll,
                backgroundRolls = rolled.backgroundRolls,
                backgroundFeatures = rolled.background?.let { bg -> rolled.backgroundRolls?.let { rs -> backgroundOutcomes(bg, rs).map { it.key } } } ?: emptyList()
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



