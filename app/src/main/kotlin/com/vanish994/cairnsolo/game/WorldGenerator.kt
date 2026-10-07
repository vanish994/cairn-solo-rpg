package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.RandomSource

data class WorldSeed(val campaignName: String, val concept: String, val startingPoint: String = "starting-settlement") {
    init { require(campaignName.isNotBlank()); require(concept.isNotBlank()); require(startingPoint.isNotBlank()) }
}

data class WorldState(
    val id: String,
    val campaignName: String,
    val concept: String,
    val region: RegionState,
    val currentLocationId: String,
    val settlements: List<SettlementState>,
    val landmarks: List<LandmarkState>,
    val factions: List<FactionState>,
    val npcs: List<WorldNpcState>,
    val dungeons: List<DungeonSiteState>,
    val threats: List<ThreatState>,
    val treasures: List<TreasureState>,
    val rumors: List<RumorState>
)

data class RegionState(val id: String, val name: String, val terrain: RegionTerrain, val description: String)
enum class RegionTerrain { EASY, TOUGH, PERILOUS }

data class SettlementState(val id: String, val name: String, val type: SettlementType, val description: String)
enum class SettlementType { VILLAGE, TOWN, CITY, OUTPOST }

data class LandmarkState(val id: String, val name: String, val terrain: String, val description: String)

data class FactionState(
    val id: String,
    val name: String,
    val type: String,
    val agent: String,
    val traits: List<String>,
    val advantages: List<String>,
    val agenda: String,
    val goals: List<String>,
    val obstacle: String,
    val goalProgress: Int = 0
) {
    init { require(goals.size in 3..5); require(goalProgress in 0..goals.size) }
}

data class WorldNpcState(val id: String, val name: String, val role: String, val locationId: String, val factionId: String? = null)
data class DungeonSiteState(val id: String, val name: String, val entranceLocationId: String, val theme: String, val discovered: Boolean = false)
data class ThreatState(val id: String, val name: String, val description: String, val factionId: String? = null)
data class TreasureState(val id: String, val name: String, val locationId: String, val discovered: Boolean = false)
data class RumorState(val id: String, val text: String, val reliability: Int = 1, val discovered: Boolean = false) {
    init { require(reliability in 1..3) }
}

class WorldGenerator(private val random: RandomSource) {
    fun generate(seed: WorldSeed): WorldState {
        val regionTerrain = terrain(random.d6())
        val regionName = listOf("Cinzas", "Bosque", "Coroa", "Fronteira", "Véu", "Ermos")[random.d6() - 1]
        val region = RegionState("region-1", "${regionName} de ${seed.concept}", regionTerrain, "Uma região ${terrainDescription(regionTerrain)} moldada por ${seed.concept}.")
        val settlementCount = (random.d6() - 2).coerceIn(1, 4)
        val settlements = (0 until settlementCount).map { settlement(it, seed.startingPoint, regionTerrain) }
        val landmarks = (0 until (random.d6() - 3).coerceAtLeast(1)).map { landmark(it, regionTerrain) }
        val factionCount = (random.d6() + 1).coerceIn(2, 5)
        val factions = (0 until factionCount).map { faction(it) }
        val npcs = (0 until (random.d6() - 2).coerceAtLeast(2)).map { npc(it, settlements, factions) }
        val dungeons = listOf(dungeon(0, settlements.first().id, seed.concept))
        val threats = factions.take(2).mapIndexed { index, faction -> ThreatState("threat-$index", "A ameaça de ${faction.name}", faction.obstacle, faction.id) }
        val treasures = listOf(TreasureState("treasure-0", treasureName(random.d20()), dungeons.first().id))
        val rumors = listOf(
            RumorState("rumor-0", "A facção ${factions.first().name} procura algo enterrado sob a região."),
            RumorState("rumor-1", "Há um caminho antigo que leva ao local chamado ${landmarks.first().name}.", reliability = 2),
            RumorState("rumor-2", "O poder da dungeon não está adormecido.", reliability = 3)
        )
        return WorldState(
            id = stableId(seed), campaignName = seed.campaignName, concept = seed.concept,
            region = region, currentLocationId = settlements.first().id, settlements = settlements,
            landmarks = landmarks, factions = factions, npcs = npcs, dungeons = dungeons,
            threats = threats, treasures = treasures, rumors = rumors
        )
    }

    private fun settlement(index: Int, startingPoint: String, terrain: RegionTerrain): SettlementState {
        val type = if (index == 0) SettlementType.VILLAGE else SettlementType.entries[random.d6().coerceIn(1, 4) - 1]
        val names = listOf("Cinzália", "Pedra Alta", "Vau Sombrio", "Porto Velho")
        return SettlementState(if (index == 0) startingPoint else "settlement-$index", names[index % names.size], type, "Um assentamento ${terrainDescription(terrain)} com rumores e interesses conflitantes.")
    }

    private fun landmark(index: Int, terrain: RegionTerrain): LandmarkState {
        val names = when (terrain) {
            RegionTerrain.EASY -> listOf("Ponte Dourada", "Árvore-Coração", "Lago Opaco")
            RegionTerrain.TOUGH -> listOf("Floresta de Fungos", "Cascata Congelada", "Cratera Massiva")
            RegionTerrain.PERILOUS -> listOf("Raiz do Céu", "Vulcão Ativo", "Pedras-Sereia")
        }
        return LandmarkState("landmark-$index", names[index % names.size], terrain.name.lowercase(), "Um marco visível que orienta viajantes e esconde uma história.")
    }

    private fun faction(index: Int): FactionState {
        val types = listOf("Artisans", "Criminals", "Explorers", "Merchants", "Cultists", "Military", "Scholars")
        val agents = listOf("Academic", "Spy", "Thief", "Merchant", "Mystic", "Guard", "Sage")
        val traits = listOf(listOf("Cautious", "Adaptable"), listOf("Secretive", "Ruthless"), listOf("Popular", "Threatened"), listOf("Cunning", "Resourceful"))
        val agendas = listOf("Collect Artifacts", "Establish a Colony", "Protect a Secret", "Explore Uncharted Lands", "Forge an Alliance", "Reveal a Secret")
        val obstacles = listOf("a rival faction opposes the plan", "a key piece of information is missing", "a rare relic is required", "an internal schism threatens the group")
        val name = listOf("Conclave", "Círculo", "Companhia", "Ordem", "Irmandade")[index % 5]
        val agenda = agendas[random.d20().mod(agendas.size)]
        val goalTemplates = listOf("Acquire an advantage for $agenda", "Overcome the faction's first obstacle", "Advance the agenda beyond the region")
        return FactionState("faction-$index", "$name ${types[index % types.size]}", types[index % types.size], agents[index % agents.size], traits[index % traits.size], listOf("Information", "Resources"), agenda, goalTemplates, obstacles[random.d20().mod(obstacles.size)])
    }

    private fun npc(index: Int, settlements: List<SettlementState>, factions: List<FactionState>): WorldNpcState {
        val names = listOf("Edrin", "Mara", "Oren", "Veska", "Talin")
        val roles = listOf("merchant", "guide", "healer", "watcher", "scholar")
        return WorldNpcState("npc-$index", names[index % names.size], roles[index % roles.size], settlements[index % settlements.size].id, factions.getOrNull(index % factions.size)?.id)
    }

    private fun dungeon(index: Int, entrance: String, concept: String) = DungeonSiteState("dungeon-$index", "A Cripta de ${concept.replaceFirstChar { it.uppercase() }}", entrance, "ruined temple")

    private fun stableId(seed: WorldSeed): String = "world-${seed.campaignName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}"
    private fun terrain(value: Int): RegionTerrain = when (value) { in 1..3 -> RegionTerrain.EASY; in 4..5 -> RegionTerrain.TOUGH; else -> RegionTerrain.PERILOUS }
    private fun terrainDescription(terrain: RegionTerrain): String = when (terrain) { RegionTerrain.EASY -> "aberta e habitável"; RegionTerrain.TOUGH -> "acidentada e imprevisível"; RegionTerrain.PERILOUS -> "perigosa e difícil de atravessar" }
    private fun treasureName(value: Int): String = listOf("Relic of the First Flame", "Map of the Dead", "Crown of Thorns", "Silver Key")[value.mod(4)]
}
