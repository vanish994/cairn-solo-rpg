package com.vanish994.cairnsolo.game

/** Codec do mundo gerado; ausência das chaves mantém compatibilidade com saves antigos. */
object WorldStatePersistenceCodec {
    private const val FIELD = "\u001E"
    private const val LIST = "\u001D"

    fun encode(world: WorldState): Map<String, String> = buildMap {
        put("id", world.id); put("campaignName", world.campaignName); put("concept", world.concept); put("currentLocationId", world.currentLocationId)
        put("region", listOf(world.region.id, world.region.name, world.region.terrain.name, world.region.description).joinToString(FIELD))
        put("settlementCount", world.settlements.size.toString())
        world.settlements.forEachIndexed { i, x -> put("settlement_$i", listOf(x.id, x.name, x.type.name, x.description).joinToString(FIELD)) }
        put("landmarkCount", world.landmarks.size.toString())
        world.landmarks.forEachIndexed { i, x -> put("landmark_$i", listOf(x.id, x.name, x.terrain, x.description).joinToString(FIELD)) }
        put("factionCount", world.factions.size.toString())
        world.factions.forEachIndexed { i, x -> put("faction_$i", listOf(x.id, x.name, x.type, x.agent, x.traits.joinToString(LIST), x.advantages.joinToString(LIST), x.agenda, x.goals.joinToString(LIST), x.obstacle, x.goalProgress.toString()).joinToString(FIELD)) }
        put("npcCount", world.npcs.size.toString())
        world.npcs.forEachIndexed { i, x -> put("npc_$i", listOf(x.id, x.name, x.role, x.locationId, x.factionId ?: "").joinToString(FIELD)) }
        put("dungeonCount", world.dungeons.size.toString())
        world.dungeons.forEachIndexed { i, x -> put("dungeon_$i", listOf(x.id, x.name, x.entranceLocationId, x.theme, x.discovered.toString()).joinToString(FIELD)) }
        put("threatCount", world.threats.size.toString())
        world.threats.forEachIndexed { i, x -> put("threat_$i", listOf(x.id, x.name, x.description, x.factionId ?: "").joinToString(FIELD)) }
        put("treasureCount", world.treasures.size.toString())
        world.treasures.forEachIndexed { i, x -> put("treasure_$i", listOf(x.id, x.name, x.locationId, x.discovered.toString()).joinToString(FIELD)) }
        put("rumorCount", world.rumors.size.toString())
        world.rumors.forEachIndexed { i, x -> put("rumor_$i", listOf(x.id, x.text, x.reliability.toString(), x.discovered.toString()).joinToString(FIELD)) }
    }

    fun decode(values: Map<String, String>): WorldState? {
        if (values["world_id"].isNullOrBlank()) return null
        fun value(key: String) = values["world_$key"] ?: ""
        fun int(key: String, default: Int = 0) = value(key).toIntOrNull() ?: default
        fun fields(key: String) = value(key).split(FIELD)
        fun list(value: String) = value.split(LIST).filter { it.isNotBlank() }
        return runCatching {
            val region = fields("region")
            require(region.size >= 4)
            val settlements = (0 until int("settlementCount")).map { val x = fields("settlement_$it"); SettlementState(x[0], x[1], SettlementType.valueOf(x[2]), x[3]) }
            val landmarks = (0 until int("landmarkCount")).map { val x = fields("landmark_$it"); LandmarkState(x[0], x[1], x[2], x[3]) }
            val factions = (0 until int("factionCount")).map { val x = fields("faction_$it"); FactionState(x[0], x[1], x[2], x[3], list(x[4]), list(x[5]), x[6], list(x[7]), x[8], x[9].toInt()) }
            val npcs = (0 until int("npcCount")).map { val x = fields("npc_$it"); WorldNpcState(x[0], x[1], x[2], x[3], x[4].takeIf { it.isNotBlank() }) }
            val dungeons = (0 until int("dungeonCount")).map { val x = fields("dungeon_$it"); DungeonSiteState(x[0], x[1], x[2], x[3], x[4].toBoolean()) }
            val threats = (0 until int("threatCount")).map { val x = fields("threat_$it"); ThreatState(x[0], x[1], x[2], x[3].takeIf { it.isNotBlank() }) }
            val treasures = (0 until int("treasureCount")).map { val x = fields("treasure_$it"); TreasureState(x[0], x[1], x[2], x[3].toBoolean()) }
            val rumors = (0 until int("rumorCount")).map { val x = fields("rumor_$it"); RumorState(x[0], x[1], x[2].toInt(), x[3].toBoolean()) }
            WorldState(value("id"), value("campaignName"), value("concept"), RegionState(region[0], region[1], RegionTerrain.valueOf(region[2]), region[3]), value("currentLocationId"), settlements, landmarks, factions, npcs, dungeons, threats, treasures, rumors)
        }.getOrNull()
    }
}
