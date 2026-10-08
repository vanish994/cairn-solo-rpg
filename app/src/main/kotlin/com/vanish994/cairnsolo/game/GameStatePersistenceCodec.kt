package com.vanish994.cairnsolo.game

import com.vanish994.cairnsolo.rules.Background
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.CharacterTraits
import com.vanish994.cairnsolo.rules.InventoryItem
import com.vanish994.cairnsolo.rules.Scar
import com.vanish994.cairnsolo.rules.DowntimeCost
import com.vanish994.cairnsolo.rules.Milestone
import com.vanish994.cairnsolo.rules.DungeonLight
import com.vanish994.cairnsolo.rules.DungeonState
import com.vanish994.cairnsolo.rules.PathType
import com.vanish994.cairnsolo.rules.TravelDistance
import com.vanish994.cairnsolo.rules.Terrain
import com.vanish994.cairnsolo.rules.Weather
import com.vanish994.cairnsolo.rules.Watch
import com.vanish994.cairnsolo.rules.HirelingState
import com.vanish994.cairnsolo.rules.isSupportedWeaponDamageExpression
import java.util.Base64

object GameStatePersistenceCodec {
    private const val SEPARATOR = "\u001F"

    fun encode(state: GameState): Map<String, String> {
        val c = state.campaign
        val r = c.rules
        val p = c.profile
        return buildMap {
            put("campaignId", c.campaignId); put("campaignSeed", c.campaignSeed); put("characterId", c.character.id); put("characterName", c.character.name)
            put("profileAge", p.age?.toString() ?: ""); put("profileBackground", p.background?.name ?: "")
            put("profileGold", p.gold.toString()); put("profileBondRoll", p.bondRoll?.toString() ?: "")
            put("profileSecondBondRoll", p.secondBondRoll?.toString() ?: ""); put("profileOmenRoll", p.omenRoll?.toString() ?: "")
            p.backgroundRolls?.let { put("profileBackgroundRoll1", it.first.toString()); put("profileBackgroundRoll2", it.second.toString()) }
            put("profileBackgroundFeatures", p.backgroundFeatures.joinToString(SEPARATOR)); put("companionCount", p.companions.size.toString())
            p.companions.forEachIndexed { i, x -> put("comp_${i}_id", x.id); put("comp_${i}_hp", x.hp.toString()); put("comp_${i}_maxHp", x.maxHp.toString()); put("comp_${i}_armor", x.armor.toString()); put("comp_${i}_str", x.str.toString()); put("comp_${i}_dex", x.dex.toString()); put("comp_${i}_wil", x.wil.toString()); put("comp_${i}_slots", x.slots.toString()); put("comp_${i}_tags", x.tags.joinToString(SEPARATOR)) }
            p.traits?.let { t -> put("traitPhysique", t.physique); put("traitSkin", t.skin); put("traitHair", t.hair); put("traitFace", t.face); put("traitSpeech", t.speech); put("traitClothing", t.clothing); put("traitVirtue", t.virtue); put("traitVice", t.vice) }
            put("sceneId", c.sceneId); put("sceneType", c.sceneType.name); put("sceneTitle", c.sceneTitle)
            put("sceneDescription", c.sceneDescription); put("exits", c.exits.joinToString(SEPARATOR))
            put("log", c.log.joinToString(SEPARATOR)); put("turn", c.turn.toString())
            put("guardianMessage", c.guardianMessage); put("guardianHistory", c.guardianHistory.joinToString(SEPARATOR))
            c.combat?.let { fight ->
                val o = fight.opponent
                put("combatOpponentId", fight.opponentId); put("combatRound", fight.round.toString()); put("combatPlayerCanAct", fight.playerCanAct.toString())
                put("combatWeaponId", fight.opponentWeapon.id); put("combatWeaponDamage", fight.opponentWeapon.damage ?: "")
                put("combatWeaponBlast", fight.opponentWeapon.blast.toString()); put("combatWeaponRanged", fight.opponentWeapon.ranged.toString())
                put("combatNarrativeName", fight.opponentNarrative.name)
                put("combatNarrativeAppearance", fight.opponentNarrative.appearance)
                put("combatNarrativeBehavior", fight.opponentNarrative.behavior)
                put("combatNarrativeIntent", fight.opponentNarrative.intent)
                put("combatNarrativeContext", fight.opponentNarrative.context)
                put("combatStr", o.str.toString()); put("combatDex", o.dex.toString()); put("combatWil", o.wil.toString()); put("combatHp", o.hp.toString()); put("combatMaxHp", o.maxHp.toString()); put("combatArmor", o.armor.toString())
            }
            c.worldState?.let { world -> WorldStatePersistenceCodec.encode(world).forEach { (key, value) -> put("world_$key", value) } }
            c.dungeon?.let { d -> put("dungeonLocation", d.locationId); put("dungeonTurn", d.turn.toString()); put("dungeonCycles", d.cyclesInLocation.toString()); put("dungeonLight", d.light.name); put("dungeonTorches", d.torchIgnitionsRemaining.toString()); put("dungeonOil", d.lanternOilUses.toString()); put("dungeonSafe", d.safeLocation.toString()); put("dungeonDanger", d.dangerPresent.toString()); put("dungeonPanicked", d.panicked.toString()) }
            c.wilderness?.let { w -> put("wildCurrent", w.currentPoint); put("wildDestination", w.destinationPoint ?: ""); put("wildWatches", w.remainingWatches.toString()); put("wildWatch", w.watch.name); put("wildPath", w.path.name); put("wildDistance", w.distance.name); put("wildTerrain", w.terrain.name); put("wildWeather", w.weather.name); put("wildNight", w.nightTravel.toString()); put("wildLost", w.lost.toString()); put("wildRations", w.rations.toString()); put("wildDeprived", w.deprived.toString()); put("wildExtreme", w.previousWeatherWasExtreme.toString()) }
            put("downtimeSafe", c.downtime.safe.toString()); put("downtimeRecovery", c.downtime.inRecovery.toString()); put("downtimeGold", c.downtime.gold.toString()); put("downtimeReputation", c.downtime.reputation.toString()); put("downtimeResources", c.downtime.resources.joinToString(SEPARATOR)); put("downtimeCompleted", c.downtime.completedActions.toString()); put("milestoneCount", c.downtime.milestones.size.toString())
            c.downtime.milestones.forEachIndexed { i, m -> put("milestone_$i", listOf(m.id, m.goal, m.total, m.progress, costType(m.cost), costValue(m.cost)).joinToString(SEPARATOR)) }
            put("hirelingCount", c.hirelings.size.toString())
            c.hirelings.forEachIndexed { i, h -> put("hireling_$i", listOf(h.id, h.name, h.role, h.wageGp, h.loyalty, h.morale, h.active, h.injured).joinToString(SEPARATOR)) }
            put("growthEvidenceCount", c.growth.evidence.size.toString())
            c.growth.evidence.forEachIndexed { i, e -> put("growthEvidence_$i", listOf(e.id, e.summary, e.turn, e.relatedEntityIds.joinToString(","), e.focusedPattern, e.seriousRisk, e.uniqueInteraction).joinToString(SEPARATOR)) }
            put("growthProposalCount", c.growth.appliedProposalIds.size.toString()); c.growth.appliedProposalIds.forEachIndexed { i, id -> put("growthProposal_$i", id) }
            put("growthAbilityCount", c.growth.abilities.size.toString())
            c.growth.abilities.forEachIndexed { i, a -> put("growthAbility_$i", listOf(a.id, a.name, a.description, a.cost ?: "", a.acquiredTurn).joinToString(SEPARATOR)) }
            put("growthPendingChangeCount", c.growth.pendingChangeProposals.size.toString())
            c.growth.pendingChangeProposals.forEachIndexed { i, proposal ->
                val fields = listOf(
                    proposal.id, proposal.evidenceIds.joinToString(","), proposal.changeType, proposal.attribute ?: "",
                    proposal.amount?.toString() ?: "", proposal.candidate?.toString() ?: "", proposal.abilityId ?: "",
                    proposal.abilityName ?: "", proposal.abilityDescription ?: "", proposal.abilityCost ?: "", proposal.rationale
                ).joinToString(SEPARATOR)
                put("growthPendingChange_$i", Base64.getUrlEncoder().withoutPadding().encodeToString(fields.toByteArray(Charsets.UTF_8)))
            }
            put("growthDeclinedProposalCount", c.growth.declinedProposalIds.size.toString())
            c.growth.declinedProposalIds.forEachIndexed { i, id -> put("growthDeclinedProposal_$i", id) }
            put("canonLocationCount", c.worldCanon.locations.size.toString())
            c.worldCanon.locations.forEachIndexed { i, x -> put("canonLocation_${i}", listOf(x.id, x.name, x.description, x.status.name, x.firstSeenTurn).joinToString(SEPARATOR)) }
            put("canonNpcCount", c.worldCanon.npcs.size.toString())
            c.worldCanon.npcs.forEachIndexed { i, x -> put("canonNpc_${i}", listOf(x.id, x.name, x.role ?: "", x.description ?: "", x.status.name, x.firstSeenTurn).joinToString(SEPARATOR)) }
            put("canonItemCount", c.worldCanon.importantItems.size.toString())
            c.worldCanon.importantItems.forEachIndexed { i, x -> put("canonItem_${i}", listOf(x.id, x.name, x.description ?: "", x.status.name, x.firstSeenTurn).joinToString(SEPARATOR)) }
            put("canonQuestCount", c.worldCanon.quests.size.toString())
            c.worldCanon.quests.forEachIndexed { i, x -> put("canonQuest_${i}", listOf(x.id, x.title, x.description, x.status, x.firstSeenTurn).joinToString(SEPARATOR)) }
            put("canonDiscoveryCount", c.worldCanon.discoveries.size.toString())
            c.worldCanon.discoveries.forEachIndexed { i, x -> put("canonDiscovery_${i}", listOf(x.id, x.text, x.status.name, x.source.name, x.turn).joinToString(SEPARATOR)) }
            put("historyCount", c.history.size.toString())
            c.history.forEachIndexed { i, x -> put("history_${i}", listOf(x.id, x.turn, x.type.name, x.summary, x.source.name, x.relatedEntityIds.joinToString(",")).joinToString(SEPARATOR)) }
            put("updatedAt", state.updatedAtEpochMs.toString())
            put("str", r.str.toString()); put("dex", r.dex.toString()); put("wil", r.wil.toString())
            put("maxStr", r.maxStr.toString()); put("maxDex", r.maxDex.toString()); put("maxWil", r.maxWil.toString())
            put("hp", r.hp.toString()); put("maxHp", r.maxHp.toString()); put("armor", r.armor.toString()); put("fatigue", r.fatigue.toString())
            put("deprived", r.deprived.toString()); put("deprivedDays", r.deprivedDays.toString()); put("critical", r.critical.toString()); put("dead", r.dead.toString())
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
            inventory = inventory, fatigue = int("fatigue", 0), deprived = bool("deprived", false), deprivedDays = int("deprivedDays", 0),
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
        fun fields(key: String): List<String> = string(key).split(SEPARATOR)
        val locations = (0 until int("canonLocationCount", 0)).mapNotNull { i -> fields("canonLocation_${i}").takeIf { it.size >= 5 }?.let { x -> runCatching { CanonLocation(x[0], x[1], x[2], CanonStatus.valueOf(x[3]), x[4].toLong()) }.getOrNull() } }
        val npcs = (0 until int("canonNpcCount", 0)).mapNotNull { i -> fields("canonNpc_${i}").takeIf { it.size >= 6 }?.let { x -> runCatching { CanonNpc(x[0], x[1], x[2].takeIf { it.isNotBlank() }, x[3].takeIf { it.isNotBlank() }, CanonStatus.valueOf(x[4]), x[5].toLong()) }.getOrNull() } }
        val items = (0 until int("canonItemCount", 0)).mapNotNull { i -> fields("canonItem_${i}").takeIf { it.size >= 5 }?.let { x -> runCatching { CanonItem(x[0], x[1], x[2].takeIf { it.isNotBlank() }, CanonStatus.valueOf(x[3]), x[4].toLong()) }.getOrNull() } }
        val quests = (0 until int("canonQuestCount", 0)).mapNotNull { i -> fields("canonQuest_${i}").takeIf { it.size >= 5 }?.let { x -> runCatching { CanonQuest(x[0], x[1], x[2], x[3], x[4].toLong()) }.getOrNull() } }
        val discoveries = (0 until int("canonDiscoveryCount", 0)).mapNotNull { i -> fields("canonDiscovery_${i}").takeIf { it.size >= 5 }?.let { x -> runCatching { CanonDiscovery(x[0], x[1], CanonStatus.valueOf(x[2]), CanonSource.valueOf(x[3]), x[4].toLong()) }.getOrNull() } }
        val history = (0 until int("historyCount", 0)).mapNotNull { i -> fields("history_${i}").takeIf { it.size >= 6 }?.let { x -> runCatching { CampaignHistoryEntry(x[0], x[1].toLong(), HistoryEventType.valueOf(x[2]), x[3], HistorySource.valueOf(x[4]), x[5].split(",").filter { it.isNotBlank() }) }.getOrNull() } }
        val combat = values["combatOpponentId"]?.takeIf { it.isNotBlank() }?.let { opponentId ->
            val opponentNarrative = runCatching {
                CombatOpponentNarrative(
                    name = string("combatNarrativeName").takeIf { it.isNotBlank() } ?: opponentId,
                    appearance = string("combatNarrativeAppearance"),
                    behavior = string("combatNarrativeBehavior"),
                    intent = string("combatNarrativeIntent"),
                    context = string("combatNarrativeContext")
                )
            }.getOrDefault(CombatOpponentNarrative(opponentId))
            CombatState(
                opponentId = opponentId,
                opponent = CharacterState(int("combatStr", 1), int("combatDex", 1), int("combatWil", 1), int("combatHp", 1), int("combatMaxHp", 1), int("combatArmor", 0)),
                opponentWeapon = com.vanish994.cairnsolo.rules.WeaponProfile(
                    id = string("combatWeaponId", "unarmed"),
                    damage = nullableString("combatWeaponDamage")
                        .takeIf { isSupportedWeaponDamageExpression(it) } ?: "d4",
                    blast = bool("combatWeaponBlast", false), ranged = bool("combatWeaponRanged", false)
                ),
                round = int("combatRound", 1), playerCanAct = bool("combatPlayerCanAct", true),
                opponentNarrative = opponentNarrative
            )
        }
        val dungeon = values["dungeonLocation"]?.takeIf { it.isNotBlank() }?.let { DungeonState(it, int("dungeonTurn", 0), int("dungeonCycles", 0), runCatching { DungeonLight.valueOf(string("dungeonLight", DungeonLight.DARK.name)) }.getOrDefault(DungeonLight.DARK), int("dungeonTorches", 3), int("dungeonOil", 0), bool("dungeonSafe", false), bool("dungeonDanger", false), bool("dungeonPanicked", false)) }
        val wilderness = values["wildCurrent"]?.takeIf { it.isNotBlank() }?.let { com.vanish994.cairnsolo.rules.WildernessState(it, nullableString("wildDestination"), int("wildWatches", 0), runCatching { Watch.valueOf(string("wildWatch", Watch.MORNING.name)) }.getOrDefault(Watch.MORNING), runCatching { PathType.valueOf(string("wildPath", PathType.ROAD.name)) }.getOrDefault(PathType.ROAD), runCatching { TravelDistance.valueOf(string("wildDistance", TravelDistance.SHORT.name)) }.getOrDefault(TravelDistance.SHORT), runCatching { Terrain.valueOf(string("wildTerrain", Terrain.EASY.name)) }.getOrDefault(Terrain.EASY), runCatching { Weather.valueOf(string("wildWeather", Weather.NICE.name)) }.getOrDefault(Weather.NICE), bool("wildNight", false), bool("wildLost", false), int("wildRations", 0), bool("wildDeprived", false), bool("wildExtreme", false)) }
        val milestones = (0 until int("milestoneCount", 0)).mapNotNull { i -> string("milestone_$i").split(SEPARATOR).takeIf { it.size >= 6 }?.let { x -> runCatching { Milestone(x[0], x[1], x[2].toInt(), x[3].toInt(), decodeCost(x[4], x[5])) }.getOrNull() } }
        val downtime = com.vanish994.cairnsolo.rules.DowntimeState(bool("downtimeSafe", true), bool("downtimeRecovery", false), int("downtimeGold", int("profileGold", 0)), int("downtimeReputation", 0), string("downtimeResources").split(SEPARATOR).filter { it.isNotBlank() }.toSet(), milestones, int("downtimeCompleted", 0))
        val hirelings = (0 until int("hirelingCount", 0)).mapNotNull { i -> string("hireling_$i").split(SEPARATOR).takeIf { it.size >= 8 }?.let { x -> runCatching { HirelingState(x[0], x[1], x[2], x[3].toInt(), x[4].toInt(), x[5].toInt(), x[6].toBoolean(), x[7].toBoolean()) }.getOrNull() } }
        val growthEvidence = (0 until int("growthEvidenceCount", 0)).mapNotNull { i -> string("growthEvidence_$i").split(SEPARATOR).takeIf { it.size >= 7 }?.let { x -> runCatching { GrowthEvidence(x[0], x[1], x[2].toLong(), x[3].split(",").filter { it.isNotBlank() }, x[4].toBoolean(), x[5].toBoolean(), x[6].toBoolean()) }.getOrNull() } }
        val appliedGrowth = (0 until int("growthProposalCount", 0)).map { i -> string("growthProposal_$i") }.filter { it.isNotBlank() }
        val growthAbilities = (0 until int("growthAbilityCount", 0)).mapNotNull { i -> string("growthAbility_$i").split(SEPARATOR).takeIf { it.size >= 5 }?.let { x -> runCatching { GrowthAbility(x[0], x[1], x[2], x[3].takeIf { it.isNotBlank() }, x[4].toLong()) }.getOrNull() } }
        val pendingGrowthChanges = (0 until int("growthPendingChangeCount", 0)).mapNotNull { i ->
            val decoded = runCatching {
                String(Base64.getUrlDecoder().decode(string("growthPendingChange_$i")), Charsets.UTF_8)
            }.getOrNull() ?: return@mapNotNull null
            decoded.split(SEPARATOR, limit = 11).takeIf { it.size == 11 }?.let { x -> runCatching {
                GrowthChangeProposal(
                    id = x[0], evidenceIds = x[1].split(",").filter { it.isNotBlank() }, changeType = x[2],
                    attribute = x[3].takeIf { it.isNotBlank() }, amount = x[4].toIntOrNull(), candidate = x[5].toIntOrNull(),
                    abilityId = x[6].takeIf { it.isNotBlank() }, abilityName = x[7].takeIf { it.isNotBlank() },
                    abilityDescription = x[8].takeIf { it.isNotBlank() }, abilityCost = x[9].takeIf { it.isNotBlank() }, rationale = x[10]
                )
            }.getOrNull() }
        }
        val declinedGrowth = (0 until int("growthDeclinedProposalCount", 0)).map { i -> string("growthDeclinedProposal_$i") }.filter { it.isNotBlank() }
        val worldState = WorldStatePersistenceCodec.decode(values)
        return GameState(
            campaign = CampaignState(
                campaignId = string("campaignId"), campaignSeed = string("campaignSeed", string("campaignId")), character = CharacterIdentity(string("characterId"), string("characterName", "Aventureiro")),
                rules = rules, profile = profile, sceneId = string("sceneId", "prologue"), sceneType = sceneType,
                sceneTitle = string("sceneTitle", "Prologue"), sceneDescription = string("sceneDescription", "A aventura começa."),
                exits = exits, log = log, turn = long("turn", 0L),
                guardianMessage = string("guardianMessage", DEFAULT_GUARDIAN_PROLOGUE),
                guardianHistory = string("guardianHistory").split(SEPARATOR).filter { it.isNotBlank() },
                combat = combat,
                dungeon = dungeon,
                wilderness = wilderness,
                downtime = downtime,
                hirelings = hirelings,
                growth = GrowthState(growthEvidence, appliedGrowth, growthAbilities, pendingGrowthChanges, declinedGrowth),
                worldState = worldState,
                worldCanon = WorldCanon(locations, npcs, items, quests, discoveries),
                history = history
            ), updatedAtEpochMs = long("updatedAt", 0L)
        )
    }

    private fun costType(cost: DowntimeCost): String = when (cost) { DowntimeCost.None -> "NONE"; is DowntimeCost.Gold -> "GOLD"; is DowntimeCost.Resource -> "RESOURCE"; is DowntimeCost.Reputation -> "REPUTATION"; is DowntimeCost.Loss -> "LOSS" }
    private fun costValue(cost: DowntimeCost): String = when (cost) { DowntimeCost.None -> ""; is DowntimeCost.Gold -> cost.amount.toString(); is DowntimeCost.Resource -> cost.id; is DowntimeCost.Reputation -> cost.amount.toString(); is DowntimeCost.Loss -> cost.resourceId }
    private fun decodeCost(type: String, value: String): DowntimeCost = when (type) { "GOLD" -> DowntimeCost.Gold(value.toInt()); "RESOURCE" -> DowntimeCost.Resource(value); "REPUTATION" -> DowntimeCost.Reputation(value.toInt()); "LOSS" -> DowntimeCost.Loss(value); else -> DowntimeCost.None }
}
