package com.vanish994.cairnsolo.game

import android.content.Context
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.InventoryItem

interface GameStateRepository {
    fun save(state: GameState)
    fun load(): GameState?
    fun clear()
}

class LocalGameStateRepository(context: Context) : GameStateRepository {
    private val prefs = context.getSharedPreferences("cairn_campaign", Context.MODE_PRIVATE)

    override fun save(state: GameState) {
        val e = prefs.edit()
        val c = state.campaign
        val r = c.rules
        e.putString("campaignId", c.campaignId)
        e.putString("characterId", c.character.id)
        e.putString("characterName", c.character.name)
        e.putString("sceneId", c.sceneId)
        e.putString("sceneType", c.sceneType.name)
        e.putString("sceneTitle", c.sceneTitle)
        e.putString("sceneDescription", c.sceneDescription)
        e.putString("exits", c.exits.joinToString("\u001F"))
        e.putString("log", c.log.joinToString("\u001F"))
        e.putLong("turn", c.turn)
        e.putLong("updatedAt", state.updatedAtEpochMs)
        e.putInt("str", r.str).putInt("dex", r.dex).putInt("wil", r.wil)
        e.putInt("hp", r.hp).putInt("maxHp", r.maxHp).putInt("armor", r.armor)
        e.putInt("fatigue", r.fatigue)
        e.putBoolean("deprived", r.deprived).putBoolean("critical", r.critical).putBoolean("dead", r.dead)
        e.putString("scar", r.scar?.name)
        e.putInt("inventoryCount", r.inventory.size)
        r.inventory.forEachIndexed { i, item ->
            e.putString("item_"+i+"_id", item.id)
            e.putInt("item_"+i+"_slots", item.slots)
            e.putBoolean("item_"+i+"_petty", item.petty)
        }
        e.apply()
    }

    override fun load(): GameState? {
        if (!prefs.contains("campaignId")) return null
        val count = prefs.getInt("inventoryCount", 0)
        val inventory = (0 until count).map { i ->
            InventoryItem(
                id = prefs.getString("item_"+i+"_id", "item-"+i)!!,
                slots = prefs.getInt("item_"+i+"_slots", 1),
                petty = prefs.getBoolean("item_"+i+"_petty", false)
            )
        }
        val rules = CharacterState(
            str = prefs.getInt("str", 10),
            dex = prefs.getInt("dex", 10),
            wil = prefs.getInt("wil", 10),
            hp = prefs.getInt("hp", 6),
            maxHp = prefs.getInt("maxHp", 6),
            armor = prefs.getInt("armor", 0),
            inventory = inventory,
            fatigue = prefs.getInt("fatigue", 0),
            deprived = prefs.getBoolean("deprived", false),
            critical = prefs.getBoolean("critical", false),
            dead = prefs.getBoolean("dead", false),
            scar = prefs.getString("scar", null)?.let { com.vanish994.cairnsolo.rules.Scar.valueOf(it) }
        )
        return GameState(
            campaign = CampaignState(
                campaignId = prefs.getString("campaignId", "")!!,
                character = CharacterIdentity(
                    id = prefs.getString("characterId", "")!!,
                    name = prefs.getString("characterName", "Aventureiro")!!
                ),
                rules = rules,
                sceneId = prefs.getString("sceneId", "prologue")!!,
                sceneType = prefs.getString("sceneType", SceneType.EXPLORATION.name)!!.let { runCatching { SceneType.valueOf(it) }.getOrDefault(SceneType.EXPLORATION) },
                sceneTitle = prefs.getString("sceneTitle", "Prologue")!!,
                sceneDescription = prefs.getString("sceneDescription", "A aventura começa.")!!,
                exits = prefs.getString("exits", "")!!.split("\u001F").filter { it.isNotBlank() },
                log = prefs.getString("log", "")!!.split("\u001F").filter { it.isNotBlank() },
                turn = prefs.getLong("turn", 0L)
            ),
            updatedAtEpochMs = prefs.getLong("updatedAt", 0L)
        )
    }

    override fun clear() { prefs.edit().clear().apply() }
}
