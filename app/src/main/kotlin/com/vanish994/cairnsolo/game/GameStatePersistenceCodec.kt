package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.Background
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.CharacterTraits
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.Scar

object GameStatePersistenceCodec {
    private const val SEPARATOR = "\u001F"

    fun encode(state: GameState): Map<String, String> {
        val c = state.campaign
        val r = c.rules
        val p = c.profile
        return buildMap {
            put("campaignId", c.campaignId); put("characterId", c.character.id); put("characterName", c.character.name)
            put("profileAge", p.age?.toString() ?: ""); put("profileBackground", p.background?.name ?: "")
            put("profileGold", p.gold.toString()); put("profileBondRoll", p.bondRoll?.toString() ?: "")
            put("profileSecondBondRoll", p.secondBondRoll?.toString() ?: ""); put("profileOmenRoll", p.omenRoll?.toString() ?: "")
            p.backgroundRolls?.let { put("profileBackgroundRoll1", it.first.toString()); put("profileBackgroundRoll2", it.second.toString()) }
            put("profileBackgroundFeatures", p.backgroundFeatures.joinToString(SEPARATOR)); put("companionCount", p.companions.size.toString())
            p.companions.forEachIndexed { i, x -> put("comp_${i}_id", x.id); put("comp_${i}_hp", x.hp.toString()); put("comp_${i}_maxHp", x.maxHp.toString()); put("comp_${i}_armor", x.armor.toString()); put("comp_${i}_str", x.str.toString()); put("comp_${i}_dex", x.dex.toString()); put("comp_${i}_wil", x.wil.toString()); put("comp_${i}_slots", x.slots.toString()); put("comp_${i}_tags", x.tags.joinToString(SEPARATOR)) }
            p.traits?.let { t -> put("traitPhysique", t.physique); put("traitSkin", t.skin); put("traitHair", t.hair); put("traitFace", t.face); put("traitSpeech", t.speech); put("traitClothing", t.clothing); put("traitVirtue", t.virtue); put("traitVice", t.vice) }
            put("sceneId", c.sceneId); put("sceneType", c.sceneType.name); put("sceneTitle", c.sceneTitle)
            put("sceneDescription", c.sceneDescription); put("exits", c.exits.joinToString(SEPARATOR))
            put("log", c.log.joinToString(SEPARATOR)); put("turn", c.turn.toString()); put("updatedAt", state.updatedAtEpochMs.toString())
            put("str", r.str.toString()); put("dex", r.dex.toString()); put("wil", r.wil.toString())
            put("maxStr", r.maxStr.toString()); put("maxDex", r.maxDex.toString()); put("maxWil", r.maxWil.toString())
            put("hp", r.hp.toString()); put("maxHp", r.maxHp.toString()); put("armor", r.armor.toString()); put("fatigue", r.fatigue.toString())
            put("deprived", r.deprived.toString()); put("critical", r.critical.toString()); put("dead", r.dead.toString())
            put("scar", r.scar?.name ?: ""); put("lastingScar", r.lastingScar ?: ""); put("brokenLimb", r.brokenLimb ?: ""); put("scarRecovery", r.scarRecovery?.name ?: ""); put("scarAttribute", r.scarAttribute?.name ?: "")
            put("sundered", r.sundered.toString()); put("deafened", r.deafened.toString()); put("diseased", r.diseased.toString())
            put("hamstrung", r.hamstrung.toString()); put("doomed", r.doomed.toString()); put("inventoryCount", r.inventory.size.toString())
            r.inventory.forEachIndexed { index, item ->
                put("item_${index}_id", item.id); put("item_${index}_slots", item.slots.toString()); put("item_${index}_petty", item.petty.toString())
                put("item_${index}_damage", item.damage ?: ""); put("item_${index}_uses", item.uses?.toString() ?: "")
                put("item_${index}_armor", item.armor.toString()); put("item_${index}_tags", item.tags.joinToString(SEPARATOR))
            }
        }
    }

    fun decode(values: Map<String, String>): GameState? {
        if (!values.containsKey("campaignId")) return null
        fun string(key: String, default: String = "") = values[key] ?: default
        fun int(key: String, default: Int) = values[key]?.toIntOrNull() ?: default
        fun long(key: String, default: Long) = values[key]?.toLongOrNull() ?: default
        fun bool(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
        fun nullableString(key: String): String? = values[key]?.takeIf { it.isNotEmpty() }

        val count = int("inventoryCount", 0)
        val inventory = (0 until count).map { index ->
            InventoryItem(
                id = string("item_${index}_id", "item-$index"),
                slots = int("item_${index}_slots", 1),
                petty = bool("item_${index}_petty", false),
                damage = nullableString("item_${index}_damage"),
                uses = values["item_${index}_uses"]?.toIntOrNull(),
                armor = int("item_${index}_armor", 0),
                tags = string("item_${index}_tags").split(SEPARATOR).filter { it.isNotBlank() }.toSet()
            )
        }
        val rules = CharacterState(
            str = int("str", 10), dex = int("dex", 10), wil = int("wil", 10),
            hp = int("hp", 6), maxHp = int("maxHp", 6), armor = int("armor", 0),
            inventory = inventory, fatigue = int("fatigue", 0), deprived = bool("deprived", false),
            critical = bool("critical", false), dead = bool("dead", false),
            scar = nullableString("scar")?.let { runCatching { Scar.valueOf(it) }.getOrNull() },
            maxStr = int("maxStr", int("str", 10)), maxDex = int("maxDex", int("dex", 10)), maxWil = int("maxWil", int("wil", 10)),
            lastingScar = nullableString("lastingScar"), brokenLimb = nullableString("brokenLimb"), scarRecovery = nullableString("scarRecovery")?.let { runCatching { com.vanish994.cairnsolo.rules.ScarRecovery.valueOf(it) }.getOrNull() }, scarAttribute = nullableString("scarAttribute")?.let { runCatching { com.vanish994.cairnsolo.rules.Attribute.valueOf(it) }.getOrNull() },
            sundered = bool("sundered", false), deafened = bool("deafened", false), diseased = bool("diseased", false),
            hamstrung = bool("hamstrung", false), doomed = bool("doomed", false)
        )
        val traits = if (values.containsKey("traitPhysique")) CharacterTraits(string("traitPhysique"), string("traitSkin"), string("traitHair"), string("traitFace"), string("traitSpeech"), string("traitClothing"), string("traitVirtue"), string("traitVice")) else null
        val background = values["profileBackground"]?.takeIf { it.isNotEmpty() }?.let { runCatching { Background.valueOf(it) }.getOrNull() }
        val companionCount = int("companionCount", 0)
        val companions = (0 until companionCount).map { i -> CompanionState(string("comp_${i}_id", "companion-$i"), int("comp_${i}_hp", 1), int("comp_${i}_maxHp", 1), int("comp_${i}_armor", 0), int("comp_${i}_str", 0), int("comp_${i}_dex", 0), int("comp_${i}_wil", 0), int("comp_${i}_slots", 0), string("comp_${i}_tags").split(SEPARATOR).filter { it.isNotBlank() }.toSet()) }
        val profile = CharacterProfile(
            age = values["profileAge"]?.toIntOrNull(),
            background = background,
            traits = traits,
            gold = int("profileGold", 0),
            bondRoll = values["profileBondRoll"]?.toIntOrNull(),
            secondBondRoll = values["profileSecondBondRoll"]?.toIntOrNull(),
            omenRoll = values["profileOmenRoll"]?.toIntOrNull(),
            backgroundRolls = values["profileBackgroundRoll1"]?.toIntOrNull()?.let { first -> values["profileBackgroundRoll2"]?.toIntOrNull()?.let { second -> runCatching { com.vanish994.cairnsolo.rules.BackgroundRolls(first, second) }.getOrNull() } },
            backgroundFeatures = string("profileBackgroundFeatures").split(SEPARATOR).filter { it.isNotBlank() },
            companions = companions
        )
        val exits = string("exits").split(SEPARATOR).filter { it.isNotBlank() }
        val log = string("log").split(SEPARATOR).filter { it.isNotBlank() }
        val sceneType = runCatching { SceneType.valueOf(string("sceneType", SceneType.EXPLORATION.name)) }.getOrDefault(SceneType.EXPLORATION)
        return GameState(
            campaign = CampaignState(
                campaignId = string("campaignId"), character = CharacterIdentity(string("characterId"), string("characterName", "Aventureiro")),
                rules = rules, profile = profile, sceneId = string("sceneId", "prologue"), sceneType = sceneType,
                sceneTitle = string("sceneTitle", "Prologue"), sceneDescription = string("sceneDescription", "A aventura começa."),
                exits = exits, log = log, turn = long("turn", 0L)
            ), updatedAtEpochMs = long("updatedAt", 0L)
        )
    }
}
