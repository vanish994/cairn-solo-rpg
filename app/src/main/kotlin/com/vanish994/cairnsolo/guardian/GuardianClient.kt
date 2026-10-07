package com.vanish994.cairnsolo.guardian

import android.util.Log
import com.vanish994.cairnsolo.BuildConfig
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CanonProposal
import com.vanish994.cairnsolo.game.CanonStatus
import com.vanish994.cairnsolo.game.CanonSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GuardianRuleRequest(
    val type: String,
    val attribute: String? = null,
    val amount: Int? = null
)

data class GuardianResponse(
    val narration: String,
    val sceneTitle: String,
    val sceneDescription: String,
    val ruleRequest: GuardianRuleRequest?,
    val suggestedActions: List<String>,
    val interactionId: String?,
    val canonProposals: List<CanonProposal> = emptyList()
)

interface GuardianClient {
    suspend fun narrate(state: GameState, playerIntent: String): Result<GuardianResponse>
}

class HttpGuardianClient(
    private val baseUrl: String = BuildConfig.GUARDIAN_API_URL
) : GuardianClient {

    override suspend fun narrate(
        state: GameState,
        playerIntent: String
    ): Result<GuardianResponse> = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "Guardião online não configurado." }
        require(playerIntent.isNotBlank()) { "A intenção do jogador está vazia." }

        var lastFailure: Throwable? = null
        repeat(2) { attempt ->
            runCatching { narrateOnce(state, playerIntent) }
                .onSuccess { return@withContext Result.success(it) }
                .onFailure {
                    lastFailure = it
                    if (attempt == 0) delay(1_500)
                }
        }
        Result.failure(lastFailure ?: IllegalStateException("Falha desconhecida do Guardião"))
    }

    private fun narrateOnce(state: GameState, playerIntent: String): GuardianResponse {
        val url = baseUrl.trimEnd('/')
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val payload = JSONObject().apply {
                put("playerIntent", playerIntent.trim())
                put("campaign", campaignJson(state))
            }
            connection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Log.e("CairnGuardian", "Guardian request failed: HTTP " + code + ", body=" + body)
                error("O Guardião está temporariamente em silêncio. Tente novamente.")
            }
            parseResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun campaignJson(state: GameState): JSONObject {
        val c = state.campaign
        val r = c.rules
        return JSONObject().apply {
            put("campaignId", c.campaignId)
            put("characterName", c.character.name)
            put("turn", c.turn)
            put("sceneId", c.sceneId)
            put("sceneType", c.sceneType.name)
            put("sceneTitle", c.sceneTitle)
            put("sceneDescription", c.sceneDescription)
            put("exits", JSONArray(c.exits))
            put("guardianMessage", c.guardianMessage)
            put("guardianInteractionId", c.guardianInteractionId ?: JSONObject.NULL)
            put("guardianHistory", JSONArray(c.guardianHistory.takeLast(12)))
            put("worldCanon", JSONObject().apply {
                put("locations", JSONArray(c.worldCanon.locations.map { JSONObject().apply { put("id", it.id); put("name", it.name); put("description", it.description); put("status", it.status.name) } }))
                put("npcs", JSONArray(c.worldCanon.npcs.map { JSONObject().apply { put("id", it.id); put("name", it.name); put("role", it.role ?: JSONObject.NULL); put("description", it.description ?: JSONObject.NULL); put("status", it.status.name) } }))
                put("importantItems", JSONArray(c.worldCanon.importantItems.map { JSONObject().apply { put("id", it.id); put("name", it.name); put("description", it.description ?: JSONObject.NULL); put("status", it.status.name) } }))
                put("quests", JSONArray(c.worldCanon.quests.map { JSONObject().apply { put("id", it.id); put("title", it.title); put("description", it.description); put("status", it.status) } }))
                put("discoveries", JSONArray(c.worldCanon.discoveries.map { JSONObject().apply { put("id", it.id); put("text", it.text); put("status", it.status.name); put("source", it.source.name) } }))
            })
            put("recentHistory", JSONArray(c.history.takeLast(12).map { JSONObject().apply { put("id", it.id); put("turn", it.turn); put("type", it.type.name); put("summary", it.summary); put("source", it.source.name) } }))
            put("growth", JSONObject().apply {
                put("evidence", JSONArray(c.growth.evidence.takeLast(12).map { evidence -> JSONObject().apply {
                    put("id", evidence.id); put("summary", evidence.summary); put("turn", evidence.turn)
                    put("relatedEntityIds", JSONArray(evidence.relatedEntityIds)); put("focusedPattern", evidence.focusedPattern)
                    put("seriousRisk", evidence.seriousRisk); put("uniqueInteraction", evidence.uniqueInteraction)
                } }))
                put("appliedProposalIds", JSONArray(c.growth.appliedProposalIds.takeLast(12)))
                put("abilities", JSONArray(c.growth.abilities.takeLast(12).map { ability -> JSONObject().apply {
                    put("id", ability.id); put("name", ability.name); put("description", ability.description)
                    put("cost", ability.cost ?: JSONObject.NULL); put("acquiredTurn", ability.acquiredTurn)
                } }))
            })
            c.worldState?.let { world ->
                put("world", JSONObject().apply {
                    put("currentLocationId", world.currentLocationId)
                    put("factions", JSONArray(world.factions.map { faction -> JSONObject().apply {
                        put("id", faction.id); put("name", faction.name); put("agenda", faction.agenda)
                        put("goalProgress", faction.goalProgress); put("goals", JSONArray(faction.goals))
                        put("obstacle", faction.obstacle); put("traits", JSONArray(faction.traits))
                    } }))
                    put("npcs", JSONArray(world.npcs.map { npc -> JSONObject().apply {
                        put("id", npc.id); put("name", npc.name); put("role", npc.role)
                        put("locationId", npc.locationId); put("factionId", npc.factionId ?: JSONObject.NULL)
                    } }))
                })
            }
            put("stats", JSONObject().apply {
                put("str", r.str)
                put("dex", r.dex)
                put("wil", r.wil)
                put("hp", r.hp)
                put("maxHp", r.maxHp)
                put("armor", r.armor)
                put("deprived", r.deprived)
                put("critical", r.critical)
                put("dead", r.dead)
            })
            put("inventory", JSONArray(r.inventory.map { item ->
                JSONObject().apply {
                    put("id", item.id)
                    put("slotCost", item.slotCost)
                    put("damage", item.damage ?: JSONObject.NULL)
                    put("armor", item.armor)
                    put("uses", item.uses ?: JSONObject.NULL)
                }
            }))
        }
    }

    private fun parseResponse(body: String): GuardianResponse {
        val json = JSONObject(body)
        val ruleObject = json.optJSONObject("ruleRequest")
        val ruleRequest = ruleObject?.let {
            GuardianRuleRequest(
                type = it.getString("type"),
                attribute = it.optString("attribute").takeIf { value -> value.isNotBlank() },
                amount = if (it.has("amount") && !it.isNull("amount")) it.getInt("amount") else null
            )
        }
        val actions = buildList {
            val array = json.optJSONArray("suggestedActions") ?: JSONArray()
            for (i in 0 until array.length()) add(array.getString(i))
        }
        val proposals = buildList {
            val array = json.optJSONArray("canonProposals") ?: JSONArray()
            for (i in 0 until array.length()) parseCanonProposal(array.getJSONObject(i))?.let(::add)
        }
        return GuardianResponse(
            narration = json.getString("narration"),
            sceneTitle = json.getString("sceneTitle"),
            sceneDescription = json.getString("sceneDescription"),
            ruleRequest = ruleRequest,
            suggestedActions = actions,
            interactionId = json.optString("interactionId").takeIf { it.isNotBlank() },
            canonProposals = proposals
        )
    }

    private fun parseCanonProposal(json: JSONObject): CanonProposal? {
        val type = json.optString("type").uppercase()
        val id = json.optString("id")
        val status = runCatching { CanonStatus.valueOf(json.optString("status").uppercase()) }.getOrNull() ?: return null
        val source = runCatching { CanonSource.valueOf(json.optString("source").uppercase()) }.getOrNull() ?: return null
        val related = buildList {
            val ids = json.optJSONArray("relatedEntityIds") ?: JSONArray()
            for (i in 0 until ids.length()) add(ids.getString(i))
        }
        return when (type) {
            "UPSERT_NPC" -> CanonProposal.UpsertNpc(id, json.optString("name"), json.optString("role").takeIf { it.isNotBlank() }, json.optString("description").takeIf { it.isNotBlank() }, status, source, related)
            "DISCOVER_LOCATION" -> CanonProposal.DiscoverLocation(id, json.optString("name"), json.optString("description"), status, source, related)
            "ADD_IMPORTANT_ITEM" -> CanonProposal.AddImportantItem(id, json.optString("name"), json.optString("description").takeIf { it.isNotBlank() }, status, source, related)
            "CREATE_QUEST" -> CanonProposal.CreateQuest(id, json.optString("name", json.optString("title")), json.optString("description"), status, source, related)
            "ADD_DISCOVERY", "ADD_RUMOR" -> CanonProposal.AddDiscovery(id, json.optString("text"), status, source, related)
            else -> null
        }
    }
}
