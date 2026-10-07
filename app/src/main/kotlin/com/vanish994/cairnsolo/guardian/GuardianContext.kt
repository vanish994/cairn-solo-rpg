package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CampaignHistoryEntry
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.WorldCanon
import com.vanish994.cairnsolo.game.WorldState
import com.vanish994.cairnsolo.rules.CharacterState
import org.json.JSONArray
import org.json.JSONObject

private fun guardianArray(values: Iterable<Any?>): JSONArray = JSONArray().apply {
    values.forEach { value -> value?.let(::put) }
}

private fun guardianMapJson(value: Map<String, Any?>): JSONObject = JSONObject().apply {
    value.forEach { (key, item) ->
        put(key, when (item) {
            is Map<*, *> -> guardianMapJson(item.entries.associate { it.key.toString() to it.value })
            is List<*> -> guardianArray(item.map { nested ->
                when (nested) {
                    is Map<*, *> -> guardianMapJson(nested.entries.associate { it.key.toString() to it.value })
                    else -> nested
                }
            })
            else -> item
        })
    }
}

/** Visão pública e deliberadamente menor que GameState. O Guardian recebe somente este contrato. */
data class GuardianContext(
    val campaignId: String,
    val turn: Long,
    val character: GuardianCharacterContext,
    val scene: GuardianSceneContext,
    val world: GuardianWorldContext?,
    val canon: GuardianCanonContext,
    val growth: GuardianGrowthContext,
    val recentHistory: List<GuardianHistoryContext>,
    val availableActions: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("campaignId", campaignId)
        put("turn", turn)
        put("character", character.toJson())
        put("scene", scene.toJson())
        world?.let { put("world", it.toJson()) }
        put("canon", canon.toJson())
        put("growth", growth.toJson())
        put("recentHistory", guardianArray(recentHistory.map { it.toJson() }))
        put("availableActions", guardianArray(availableActions))
    }
}

data class GuardianCharacterContext(
    val name: String,
    val str: Int,
    val dex: Int,
    val wil: Int,
    val hp: Int,
    val maxHp: Int,
    val armor: Int,
    val conditions: List<String>,
    val gold: Int,
    val inventory: List<GuardianInventoryContext>,
    val combat: GuardianCombatContext?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("stats", JSONObject().apply {
            put("str", str); put("dex", dex); put("wil", wil)
            put("hp", hp); put("maxHp", maxHp); put("armor", armor)
        })
        put("conditions", guardianArray(conditions))
        put("gold", gold)
        put("inventory", guardianArray(inventory.map { it.toJson() }))
        combat?.let { put("combat", it.toJson()) }
    }
}

data class GuardianInventoryContext(
    val id: String,
    val slotCost: Int,
    val damage: String?,
    val armor: Int,
    val uses: Int?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("slotCost", slotCost)
        damage?.let { put("damage", it) }
        put("armor", armor)
        uses?.let { put("uses", it) }
    }
}

data class GuardianCombatContext(val opponentId: String, val round: Int, val playerCanAct: Boolean) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("opponentId", opponentId); put("round", round); put("playerCanAct", playerCanAct)
    }
}

data class GuardianSceneContext(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val exits: List<String>,
    val lastGuardianMessage: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("type", type); put("title", title)
        put("description", description); put("exits", guardianArray(exits))
        put("lastGuardianMessage", lastGuardianMessage)
    }
}

data class GuardianWorldContext(
    val currentLocationId: String,
    val factions: List<GuardianFactionContext>,
    val npcs: List<GuardianNpcContext>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("currentLocationId", currentLocationId)
        put("factions", guardianArray(factions.map { it.toJson() }))
        put("npcs", guardianArray(npcs.map { it.toJson() }))
    }
}

data class GuardianFactionContext(
    val id: String,
    val name: String,
    val agenda: String,
    val goalProgress: Int,
    val goals: List<String>,
    val obstacle: String,
    val traits: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("agenda", agenda)
        put("goalProgress", goalProgress); put("goals", guardianArray(goals))
        put("obstacle", obstacle); put("traits", guardianArray(traits))
    }
}

data class GuardianNpcContext(val id: String, val name: String, val role: String, val locationId: String, val factionId: String?) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("role", role); put("locationId", locationId)
        factionId?.let { put("factionId", it) }
    }
}

data class GuardianCanonContext(
    val locations: List<Map<String, Any?>>,
    val npcs: List<Map<String, Any?>>,
    val importantItems: List<Map<String, Any?>>,
    val quests: List<Map<String, Any?>>,
    val discoveries: List<Map<String, Any?>>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("locations", guardianArray(locations.map(::guardianMapJson)))
        put("npcs", guardianArray(npcs.map(::guardianMapJson)))
        put("importantItems", guardianArray(importantItems.map(::guardianMapJson)))
        put("quests", guardianArray(quests.map(::guardianMapJson)))
        put("discoveries", guardianArray(discoveries.map(::guardianMapJson)))
    }
}

data class GuardianGrowthContext(
    val evidence: List<Map<String, Any?>>,
    val appliedProposalIds: List<String>,
    val abilities: List<Map<String, Any?>>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("evidence", guardianArray(evidence.map(::guardianMapJson)))
        put("appliedProposalIds", guardianArray(appliedProposalIds))
        put("abilities", guardianArray(abilities.map(::guardianMapJson)))
    }
}

data class GuardianHistoryContext(val id: String, val turn: Long, val type: String, val summary: String, val source: String, val relatedEntityIds: List<String>) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("turn", turn); put("type", type); put("summary", summary); put("source", source)
        put("relatedEntityIds", guardianArray(relatedEntityIds))
    }
}

object GuardianContextBuilder {
    private const val MAX_CANON_ENTRIES = 50
    private const val MAX_HISTORY = 12
    private const val MAX_GROWTH_ENTRIES = 12

    fun from(state: GameState): GuardianContext {
        val campaign = state.campaign
        val rules = campaign.rules
        return GuardianContext(
            campaignId = campaign.campaignId,
            turn = campaign.turn,
            character = character(campaign.character.name, rules, campaign.profile.gold, campaign.combat),
            scene = GuardianSceneContext(campaign.sceneId, campaign.sceneType.name, campaign.sceneTitle, campaign.sceneDescription, campaign.exits.take(20), campaign.guardianMessage),
            world = campaign.worldState?.let(::world),
            canon = canon(campaign.worldCanon),
            growth = growth(campaign.growth),
            recentHistory = campaign.history.takeLast(MAX_HISTORY).map(::history),
            availableActions = campaign.exits.take(20)
        )
    }

    private fun character(name: String, rules: CharacterState, gold: Int, combat: com.vanish994.cairnsolo.game.CombatState?): GuardianCharacterContext = GuardianCharacterContext(
        name, rules.str, rules.dex, rules.wil, rules.hp, rules.maxHp, rules.armor,
        buildList {
            if (rules.deprived) add("DEPRIVED")
            if (rules.fatigue > 0) add("FATIGUE:${rules.fatigue}")
            if (rules.critical) add("CRITICAL")
            if (rules.dead) add("DEAD")
            rules.scar?.let { add("SCAR:${it.name}") }
        }, gold,
        rules.inventory.take(10).map { GuardianInventoryContext(it.id, it.slotCost, it.damage, it.armor, it.uses) },
        combat?.let { GuardianCombatContext(it.opponentId, it.round, it.playerCanAct) }
    )

    private fun world(world: WorldState): GuardianWorldContext = GuardianWorldContext(
        world.currentLocationId,
        world.factions.take(MAX_CANON_ENTRIES).map { GuardianFactionContext(it.id, it.name, it.agenda, it.goalProgress, it.goals, it.obstacle, it.traits) },
        world.npcs.take(MAX_CANON_ENTRIES).map { GuardianNpcContext(it.id, it.name, it.role, it.locationId, it.factionId) }
    )

    private fun canon(canon: WorldCanon): GuardianCanonContext = GuardianCanonContext(
        canon.locations.takeLast(MAX_CANON_ENTRIES).map { mapOf("id" to it.id, "name" to it.name, "description" to it.description, "status" to it.status.name, "firstSeenTurn" to it.firstSeenTurn) },
        canon.npcs.takeLast(MAX_CANON_ENTRIES).map { mapOf("id" to it.id, "name" to it.name, "role" to it.role, "description" to it.description, "status" to it.status.name, "firstSeenTurn" to it.firstSeenTurn) },
        canon.importantItems.takeLast(MAX_CANON_ENTRIES).map { mapOf("id" to it.id, "name" to it.name, "description" to it.description, "status" to it.status.name, "firstSeenTurn" to it.firstSeenTurn) },
        canon.quests.takeLast(MAX_CANON_ENTRIES).map { mapOf("id" to it.id, "title" to it.title, "description" to it.description, "status" to it.status, "firstSeenTurn" to it.firstSeenTurn) },
        canon.discoveries.takeLast(MAX_CANON_ENTRIES).map { mapOf("id" to it.id, "text" to it.text, "status" to it.status.name, "source" to it.source.name, "turn" to it.turn) }
    )

    private fun growth(growth: com.vanish994.cairnsolo.game.GrowthState): GuardianGrowthContext = GuardianGrowthContext(
        growth.evidence.takeLast(MAX_GROWTH_ENTRIES).map { mapOf("id" to it.id, "summary" to it.summary, "turn" to it.turn, "relatedEntityIds" to it.relatedEntityIds, "focusedPattern" to it.focusedPattern, "seriousRisk" to it.seriousRisk, "uniqueInteraction" to it.uniqueInteraction) },
        growth.appliedProposalIds.takeLast(MAX_GROWTH_ENTRIES),
        growth.abilities.takeLast(MAX_GROWTH_ENTRIES).map { mapOf("id" to it.id, "name" to it.name, "description" to it.description, "cost" to it.cost, "acquiredTurn" to it.acquiredTurn) }
    )

    private fun history(entry: CampaignHistoryEntry): GuardianHistoryContext = GuardianHistoryContext(entry.id, entry.turn, entry.type.name, entry.summary, entry.source.name, entry.relatedEntityIds)
}
