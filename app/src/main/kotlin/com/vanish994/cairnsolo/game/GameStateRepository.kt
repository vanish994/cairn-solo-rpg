package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.Scar

object GameStatePersistenceCodec {
    private const val SEPARATOR = "\u001F"

    fun encode(state: GameState): Map<String, String> {
        val c = state.campaign
        val r = c.rules
        return buildMap {
            put("campaignId", c.campaignId); put("characterId", c.character.id); put("characterName", c.character.name)
            put("sceneId", c.sceneId); put("sceneType", c.sceneType.name); put("sceneTitle", c.sceneTitle)
            put("sceneDescription", c.sceneDescription); put("exits", c.exits.joinToString(SEPARATOR))
            put("log", c.log.joinToString(SEPARATOR)); put("turn", c.turn.toString()); put("updatedAt", state.updatedAtEpochMs.toString())
            put("str", r.str.toString()); put("dex", r.dex.toString()); put("wil", r.wil.toString())
            put("maxStr", r.maxStr.toString()); put("maxDex", r.maxDex.toString()); put("maxWil", r.maxWil.toString())
            put("hp", r.hp.toString()); put("maxHp", r.maxHp.toString()); put("armor", r.armor.toString()); put("fatigue", r.fatigue.toString())
            put("deprived", r.deprived.toString()); put("critical", r.critical.toString()); put("dead", r.dead.toString())
            put("scar", r.scar?.name ?: ""); put("lastingScar", r.lastingScar ?: ""); put("brokenLimb", r.brokenLimb ?: "")
            put("sundered", r.sundered.toString()); put("deafened", r.deafened.toString()); put("diseased", r.diseased.toString())
            put("hamstrung", r.hamstrung.toString()); put("doomed", r.doomed.toString()); put("inventoryCount", r.inventory.size.toString())
            r.inventory.forEachIndexed { index, item ->
                put("item_${index}_id", item.id); put("item_${index}_slots", item.slots.toString()); put("item_${index}_petty", item.petty.toString())
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
            InventoryItem(string("item_${index}_id", "item-$index"), int("item_${index}_slots", 1), bool("item_${index}_petty", false))
        }
        val rules = CharacterState(
            str = int("str", 10), dex = int("dex", 10), wil = int("wil", 10),
            hp = int("hp", 6), maxHp = int("maxHp", 6), armor = int("armor", 0), inventory = inventory,
            fatigue = int("fatigue", 0), deprived = bool("deprived", false), critical = bool("critical", false), dead = bool("dead", false),
            scar = nullableString("scar")?.let { runCatching { Scar.valueOf(it) }.getOrNull() },
            maxStr = int("maxStr", int("str", 10)), maxDex = int("maxDex", int("dex", 10)), maxWil = int("maxWil", int("wil", 10)),
            lastingScar = nullableString("lastingScar"), brokenLimb = nullableString("brokenLimb"),
            sundered = bool("sundered", false), deafened = bool("deafened", false), diseased = bool("diseased", false),
            hamstrung = bool("hamstrung", false), doomed = bool("doomed", false)
        )
        val sceneType = runCatching { SceneType.valueOf(string("sceneType", SceneType.EXPLORATION.name)) }.getOrDefault(SceneType.EXPLORATION)
        return GameState(
            campaign = CampaignState(
                campaignId = string("campaignId"), character = CharacterIdentity(string("characterId"), string("characterName", "Aventureiro")),
                rules = rules, sceneId = string("sceneId", "prologue"), sceneType = sceneType,
                sceneTitle = string("sceneTitle", "Prologue"), sceneDescription = string("sceneDescription", "A aventura começa."),
                exits = string("exits").split(SEPARATOR).filter { it.isNotBlank() }, log = string("log").split(SEPARATOR).filter { it.isNotBlank() },
                turn = long("turn", 0L)
            ), updatedAtEpochMs = long("updatedAt", 0L)
        )
    }
}
