package com.vanish994.cairnsolo.guardian

import android.util.Log
import com.vanish994.cairnsolo.BuildConfig
import com.vanish994.cairnsolo.game.GameState
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
    val interactionId: String?
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
        return GuardianResponse(
            narration = json.getString("narration"),
            sceneTitle = json.getString("sceneTitle"),
            sceneDescription = json.getString("sceneDescription"),
            ruleRequest = ruleRequest,
            suggestedActions = actions,
            interactionId = json.optString("interactionId").takeIf { it.isNotBlank() }
        )
    }
}
