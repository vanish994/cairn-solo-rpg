package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.CampaignHistoryEntry
import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.game.CombatOpponentStatus
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.WorldCanon
import com.vanish994.cairnsolo.game.WorldState
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.MarketplaceCatalog
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
    val campaignSeed: String,
    val turn: Long,
    val character: GuardianCharacterContext,
    val scene: GuardianSceneContext,
    val world: GuardianWorldContext?,
    val knownNpcs: List<GuardianKnownNpcContext>,
    val canon: GuardianCanonContext,
    val growth: GuardianGrowthContext,
    val recentHistory: List<GuardianHistoryContext>,
    val recentNarrative: List<String>,
    val availableActions: List<String>,
    val freeSlots: Int,
    val rewardableItems: List<RewardableItemContext>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("campaignId", campaignId)
        put("campaignSeed", campaignSeed)
        put("turn", turn)
        put("character", character.toJson())
        put("scene", scene.toJson())
        world?.let { put("world", it.toJson()) }
        put("knownNpcs", guardianArray(knownNpcs.map { it.toJson() }))
        put("canon", canon.toJson())
        put("growth", growth.toJson())
        put("recentHistory", guardianArray(recentHistory.map { it.toJson() }))
        put("recentNarrative", guardianArray(recentNarrative))
        put("availableActions", guardianArray(availableActions))
        put("freeSlots", freeSlots)
        put("rewardableItems", guardianArray(rewardableItems.map { it.toJson() }))
    }
}

data class GuardianCharacterContext(
    val name: String,
    val age: Int?,
    val background: String?,
    val backgroundFeatures: List<String>,
    val traits: Map<String, String>,
    val str: Int,
    val dex: Int,
    val wil: Int,
    val hp: Int,
    val maxHp: Int,
    val armor: Int,
    val conditions: List<String>,
    val goldGp: Int,
    val inventory: List<GuardianInventoryContext>,
    val combat: GuardianCombatContext?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        age?.let { put("age", it) }
        background?.let { put("background", it) }
        put("backgroundFeatures", guardianArray(backgroundFeatures))
        put("traits", guardianMapJson(traits))
        put("stats", JSONObject().apply {
            put("str", str); put("dex", dex); put("wil", wil)
            put("hp", hp); put("maxHp", maxHp); put("armor", armor)
        })
        put("conditions", guardianArray(conditions))
        put("goldGp", goldGp)
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

data class RewardableItemContext(val catalogId: String, val name: String, val slotCost: Int) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("catalogId", catalogId); put("name", name); put("slotCost", slotCost)
    }
}

data class GuardianCombatContext(
    val opponents: List<GuardianCombatOpponentContext>,
    val round: Int,
    val playerCanAct: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("round", round); put("playerCanAct", playerCanAct)
        put("opponents", guardianArray(opponents.map { it.toJson() }))
    }

    /** Temporary singular adapters for consumers not yet migrated in Task 3. */
    val opponentId: String get() = opponents.first().id
    val opponent: GuardianCombatOpponentContext get() = opponents.first()
}

data class GuardianCombatOpponentContext(
    val id: String,
    val status: CombatOpponentStatus,
    val narrative: CombatOpponentNarrative,
    val hp: Int,
    val maxHp: Int,
    val armor: Int,
    val weapon: GuardianCombatWeaponContext,
    val str: Int = 0,
    val dex: Int = 0,
    val wil: Int = 0,
    val conditions: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("status", status.name)
        put("narrative", JSONObject().apply {
            put("name", narrative.name)
            put("appearance", narrative.appearance)
            put("behavior", narrative.behavior)
            put("intent", narrative.intent)
            put("context", narrative.context)
        })
        put("hp", hp); put("maxHp", maxHp); put("armor", armor)
        put("stats", JSONObject().apply { put("str", str); put("dex", dex); put("wil", wil) })
        put("conditions", guardianArray(conditions))
        put("weapon", weapon.toJson())
    }
}

data class GuardianCombatWeaponContext(
    val id: String,
    val damage: String?,
    val blast: Boolean,
    val ranged: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        damage?.let { put("damage", it) }
        put("blast", blast); put("ranged", ranged)
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
    val campaignName: String,
    val concept: String,
    val region: Map<String, Any?>,
    val currentLocation: Map<String, Any?>,
    val settlements: List<Map<String, Any?>>,
    val landmarks: List<Map<String, Any?>>,
    val factions: List<GuardianFactionContext>,
    val npcs: List<GuardianNpcContext>,
    val threats: List<Map<String, Any?>>,
    val rumors: List<Map<String, Any?>>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("currentLocationId", currentLocationId)
        put("campaignName", campaignName); put("concept", concept)
        put("region", guardianMapJson(region)); put("currentLocation", guardianMapJson(currentLocation))
        put("settlements", guardianArray(settlements.map(::guardianMapJson)))
        put("landmarks", guardianArray(landmarks.map(::guardianMapJson)))
        put("factions", guardianArray(factions.map { it.toJson() }))
        put("npcs", guardianArray(npcs.map { it.toJson() }))
        put("threats", guardianArray(threats.map(::guardianMapJson)))
        put("rumors", guardianArray(rumors.map(::guardianMapJson)))
    }
}

data class GuardianKnownNpcContext(
    val id: String,
    val name: String,
    val role: String?,
    val description: String?,
    val locationId: String?,
    val combatProfile: GuardianCombatOpponentContext?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name)
        role?.let { put("role", it) }
        description?.let { put("description", it) }
        locationId?.let { put("locationId", it) }
        combatProfile?.let { put("combatProfile", it.toJson()) }
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
    private const val MAX_NARRATIVE = 24
    private const val MAX_GROWTH_ENTRIES = 12

    fun from(state: GameState): GuardianContext {
        val campaign = state.campaign
        val rules = campaign.rules
        return GuardianContext(
            campaignId = campaign.campaignId,
            campaignSeed = campaign.campaignSeed,
            turn = campaign.turn,
            character = character(campaign, campaign.combat),
            scene = GuardianSceneContext(campaign.sceneId, campaign.sceneType.name, campaign.sceneTitle, campaign.sceneDescription, campaign.exits.take(20), campaign.guardianMessage),
            world = campaign.worldState?.let(::world),
            knownNpcs = campaign.knownNpcs.takeLast(MAX_CANON_ENTRIES).map { npc ->
                GuardianKnownNpcContext(
                    npc.id, npc.name, npc.role, npc.description, npc.locationId,
                    npc.combatProfile?.let { opponent ->
                        GuardianCombatOpponentContext(
                            opponent.id, opponent.status, opponent.narrative, opponent.stats.hp,
                            opponent.stats.maxHp, opponent.stats.armor,
                            GuardianCombatWeaponContext(opponent.weapon.id, opponent.weapon.damage, opponent.weapon.blast, opponent.weapon.ranged),
                            opponent.stats.str, opponent.stats.dex, opponent.stats.wil, characterConditions(opponent.stats)
                        )
                    }
                )
            },
            canon = canon(campaign.worldCanon),
            growth = growth(campaign.growth),
            recentHistory = campaign.history.takeLast(MAX_HISTORY).map(::history),
            recentNarrative = campaign.guardianHistory.takeLast(MAX_NARRATIVE),
            availableActions = campaign.exits.take(20),
            freeSlots = rules.freeSlots,
            rewardableItems = MarketplaceCatalog.entries.mapNotNull { entry ->
                entry.item?.let { RewardableItemContext(entry.id, entry.name, it.slotCost) }
            }
        )
    }

    private fun character(campaign: com.vanish994.cairnsolo.game.CampaignState, combat: com.vanish994.cairnsolo.game.CombatState?): GuardianCharacterContext {
        val rules = campaign.rules
        val profile = campaign.profile
        val traits = profile.traits?.let { t -> mapOf(
            "physique" to t.physique, "skin" to t.skin, "hair" to t.hair, "face" to t.face,
            "speech" to t.speech, "clothing" to t.clothing, "virtue" to t.virtue, "vice" to t.vice
        ) }.orEmpty()
        return GuardianCharacterContext(
        campaign.character.name, profile.age, profile.background?.name, profile.backgroundFeatures, traits,
        rules.str, rules.dex, rules.wil, rules.hp, rules.maxHp, rules.armor,
        buildList {
            if (rules.deprived) add("DEPRIVED")
            if (rules.fatigue > 0) add("FATIGUE:${rules.fatigue}")
            if (rules.critical) add("CRITICAL")
            if (rules.dead) add("DEAD")
            rules.scar?.let { add("SCAR:${it.name}") }
        }, profile.gold,
        rules.inventory.take(10).map { GuardianInventoryContext(it.id, it.slotCost, it.damage, it.armor, it.uses) },
        combat?.let { fight ->
            GuardianCombatContext(
                opponents = fight.opponents.map { opponent ->
                    GuardianCombatOpponentContext(
                        id = opponent.id,
                        status = opponent.status,
                        narrative = opponent.narrative,
                        hp = opponent.stats.hp,
                        maxHp = opponent.stats.maxHp,
                        armor = opponent.stats.armor,
                        weapon = GuardianCombatWeaponContext(
                            id = opponent.weapon.id,
                            damage = opponent.weapon.damage,
                            blast = opponent.weapon.blast,
                            ranged = opponent.weapon.ranged
                        ),
                        str = opponent.stats.str, dex = opponent.stats.dex, wil = opponent.stats.wil,
                        conditions = characterConditions(opponent.stats)
                    )
                },
                round = fight.round,
                playerCanAct = fight.playerCanAct
            )
        }
    )
    }

    private fun world(world: WorldState): GuardianWorldContext = GuardianWorldContext(
        currentLocationId = world.currentLocationId,
        campaignName = world.campaignName,
        concept = world.concept,
        region = mapOf("id" to world.region.id, "name" to world.region.name, "terrain" to world.region.terrain.name, "description" to world.region.description),
        currentLocation = world.settlements.firstOrNull { it.id == world.currentLocationId }?.let { mapOf("id" to it.id, "name" to it.name, "type" to it.type.name, "description" to it.description) }.orEmpty(),
        settlements = world.settlements.take(20).map { mapOf("id" to it.id, "name" to it.name, "type" to it.type.name, "description" to it.description) },
        landmarks = world.landmarks.take(20).map { mapOf("id" to it.id, "name" to it.name, "terrain" to it.terrain, "description" to it.description) },
        factions = world.factions.take(MAX_CANON_ENTRIES).map { GuardianFactionContext(it.id, it.name, it.agenda, it.goalProgress, it.goals, it.obstacle, it.traits) },
        npcs = world.npcs.take(MAX_CANON_ENTRIES).map { GuardianNpcContext(it.id, it.name, it.role, it.locationId, it.factionId) },
        threats = world.threats.take(20).map { mapOf("id" to it.id, "name" to it.name, "description" to it.description, "factionId" to it.factionId) },
        rumors = world.rumors.take(20).map { mapOf("id" to it.id, "text" to it.text, "reliability" to it.reliability, "discovered" to it.discovered) }
    )

    private fun characterConditions(character: CharacterState): List<String> = buildList {
        if (character.critical) add("CRITICAL")
        if (character.dead) add("DEAD")
        if (character.fatigue > 0) add("FATIGUE:${character.fatigue}")
        if (character.deprived) add("DEPRIVED")
        character.scar?.let { add("SCAR:${it.name}") }
        character.brokenLimb?.let { add("BROKEN_LIMB:$it") }
        if (character.sundered) add("SUNDERED")
        if (character.deafened) add("DEAFENED")
        if (character.diseased) add("DISEASED")
        if (character.hamstrung) add("HAMSTRUNG")
        if (character.doomed) add("DOOMED")
    }

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