package com.vanish994.cairnsolo.guardian

import android.util.Log
import com.vanish994.cairnsolo.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CanonProposal
import com.vanish994.cairnsolo.game.CanonStatus
import com.vanish994.cairnsolo.game.CanonSource
import com.vanish994.cairnsolo.game.GrowthEvidenceProposal
import com.vanish994.cairnsolo.game.GrowthChangeProposal
import com.vanish994.cairnsolo.game.CombatOpponentNarrative
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.WeaponProfile
import com.vanish994.cairnsolo.rules.isSupportedWeaponDamageExpression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GuardianNarrativeOpponentContext(
    val opponentId: String,
    val narrative: CombatOpponentNarrative
)

data class GuardianRuleRequest(
    val type: String,
    val attribute: String? = null,
    val amount: Int? = null,
    val encounter: GuardianEncounterProposal? = null,
    val reward: GuardianRewardProposal? = null
)

enum class GuardianActionType { ATTACK }

data class GuardianActionIntent(
    val type: GuardianActionType,
    val targetId: String,
    val weaponId: String?,
    val targetName: String = targetId
) {
    init {
        require(targetId.isNotBlank() && targetId == targetId.trim() && targetId.length <= 80)
        require(weaponId == null || (weaponId.isNotBlank() && weaponId == weaponId.trim() && weaponId.length <= 80))
        require(targetName.isNotBlank() && targetName == targetName.trim() && targetName.length <= 160)
    }
}

enum class RewardStatus { OFFERED, PAID }

data class GuardianRewardProposal(
    val id: String,
    val status: RewardStatus,
    val amountGp: Int,
    val itemCatalogIds: List<String>
)

data class GuardianOpponentProposal(
    val opponentId: String,
    val narrative: CombatOpponentNarrative,
    val stats: CharacterState,
    val weapon: WeaponProfile
) {
    init {
        require(opponentId.isNotBlank() && opponentId == opponentId.trim() && opponentId.length <= 80)
        require(narrative.name.isNotBlank())
        require(narrative.appearance.isNotBlank())
        require(narrative.behavior.isNotBlank())
        require(narrative.intent.isNotBlank())
        require(narrative.context.isNotBlank())
        require(stats.maxHp > 0 && stats.hp in 1..stats.maxHp)
        require(stats.armor in 0..3)
        require(weapon.id.isNotBlank() && weapon.id == weapon.id.trim() && weapon.id.length <= 80)
        val damageExpression = weapon.damage?.trim().orEmpty()
        require(damageExpression.isNotBlank() && damageExpression.length <= 32)
        require(isSupportedWeaponDamageExpression(damageExpression))
    }
}

data class GuardianEncounterProposal(
    val opponents: List<GuardianOpponentProposal>,
    val moraleLeaderId: String? = null
) {
    init {
        require(opponents.size in 1..8) { "An encounter must contain between one and eight opponents." }
        val ids = opponents.map { it.opponentId }
        require(ids.distinct().size == ids.size) { "Encounter opponent ids must be unique." }
        require(moraleLeaderId == null || moraleLeaderId in ids) {
            "The morale leader must belong to the encounter."
        }
    }

    /** Temporary UI adapters; Task 3 will render and confirm the whole encounter list. */
    val opponentId: String get() = opponents.first().opponentId
    val narrative: CombatOpponentNarrative get() = opponents.first().narrative
    val stats: CharacterState get() = opponents.first().stats
    val weapon: WeaponProfile get() = opponents.first().weapon
}

data class GuardianResponse(
    val narration: String,
    val sceneTitle: String,
    val sceneDescription: String,
    val ruleRequest: GuardianRuleRequest?,
    val suggestedActions: List<String>,
    val interactionId: String?,
    val canonProposals: List<CanonProposal> = emptyList(),
    val growthEvidenceProposals: List<GrowthEvidenceProposal> = emptyList(),
    val growthChangeProposals: List<GrowthChangeProposal> = emptyList(),
    val actionIntent: GuardianActionIntent? = null
)

interface GuardianClient {
    suspend fun narrate(
        state: GameState,
        playerIntent: String,
        ruleResult: String? = null,
        encounterContext: List<GuardianNarrativeOpponentContext>? = null
    ): Result<GuardianResponse>

    /** Gives explicit null-context calls a unique most-specific overload. */
    suspend fun narrate(
        state: GameState,
        playerIntent: String,
        ruleResult: String?,
        encounterContext: Nothing?
    ): Result<GuardianResponse> = narrate(
        state,
        playerIntent,
        ruleResult,
        null as List<GuardianNarrativeOpponentContext>?
    )
}

internal fun guardianRequestPayload(
    state: GameState,
    playerIntent: String,
    ruleResult: String? = null,
    encounterContext: List<GuardianNarrativeOpponentContext>? = null
): JSONObject = JSONObject().apply {
    put("playerIntent", playerIntent.trim())
    put("campaign", GuardianContextBuilder.from(state).toJson())
    ruleResult?.takeIf { it.isNotBlank() }?.let { put("ruleResult", it.trim()) }
    encounterContext?.let { profiles ->
        put("encounterContext", JSONArray().apply {
            profiles.forEach { profile ->
                put(JSONObject().apply {
                    put("opponentId", profile.opponentId)
                    put("narrative", JSONObject().apply {
                        put("name", profile.narrative.name)
                        put("appearance", profile.narrative.appearance)
                        put("behavior", profile.narrative.behavior)
                        put("intent", profile.narrative.intent)
                        put("context", profile.narrative.context)
                    })
                })
            }
        })
    }
}

class HttpGuardianClient(
    private val baseUrl: String = BuildConfig.GUARDIAN_API_URL
) : GuardianClient {

    override suspend fun narrate(
        state: GameState,
        playerIntent: String,
        ruleResult: String?,
        encounterContext: List<GuardianNarrativeOpponentContext>?
    ): Result<GuardianResponse> = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "Guardião online não configurado." }
        require(playerIntent.isNotBlank()) { "A intenção do jogador está vazia." }

        var lastFailure: Throwable? = null
        repeat(2) { attempt ->
            runCatching { narrateOnce(state, playerIntent, ruleResult, encounterContext) }
                .onSuccess { return@withContext Result.success(it) }
                .onFailure {
                    lastFailure = it
                    if (attempt == 0) delay(1_500)
                }
        }
        Result.failure(lastFailure ?: IllegalStateException("Falha desconhecida do Guardião"))
    }

    private fun narrateOnce(
        state: GameState,
        playerIntent: String,
        ruleResult: String?,
        encounterContext: List<GuardianNarrativeOpponentContext>?
    ): GuardianResponse {
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
            val payload = guardianRequestPayload(state, playerIntent, ruleResult, encounterContext)
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

    internal fun parseResponse(body: String): GuardianResponse {
        val exactJson = JsonParser.parseString(body).asJsonObject
        val json = JSONObject(body)
        val ruleObject = json.optJSONObject("ruleRequest")
        val exactRuleRequest = exactJson.get("ruleRequest")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
        val ruleRequest = ruleObject?.let { parseRuleRequest(it, exactRuleRequest) }
        val actionIntent = parseActionIntent(
            json.optJSONObject("actionIntent"),
            exactJson.get("actionIntent")?.takeIf { it.isJsonObject }?.asJsonObject
        )
        val actions = buildList {
            val array = json.optJSONArray("suggestedActions") ?: JSONArray()
            for (i in 0 until array.length()) {
                if (size >= 3) break
                val action = array.opt(i) as? String ?: continue
                if (action.isNotBlank() && action.length <= 160) add(action)
            }
        }
        val proposals = buildList {
            val array = json.optJSONArray("canonProposals") ?: JSONArray()
            for (i in 0 until array.length()) parseCanonProposal(array.getJSONObject(i))?.let(::add)
        }
        val growthProposals = buildList {
            val array = json.optJSONArray("growthEvidenceProposals") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(GrowthEvidenceProposal(
                    id = item.getString("id"),
                    summary = item.getString("summary"),
                    relatedEntityIds = item.optJSONArray("relatedEntityIds")?.let { ids -> (0 until ids.length()).map { ids.getString(it) } } ?: emptyList(),
                    focusedPattern = item.optBoolean("focusedPattern", false),
                    seriousRisk = item.optBoolean("seriousRisk", false),
                    uniqueInteraction = item.optBoolean("uniqueInteraction", false)
                ))
            }
        }
        val changeProposals = buildList {
            val array = json.optJSONArray("growthChangeProposals") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(GrowthChangeProposal(
                    id = item.getString("id"),
                    evidenceIds = item.optJSONArray("evidenceIds")?.let { ids -> (0 until ids.length()).map { ids.getString(it) } } ?: emptyList(),
                    changeType = item.getString("changeType"),
                    attribute = item.optString("attribute").takeIf { it.isNotBlank() },
                    amount = item.optInt("amount").takeIf { item.has("amount") && !item.isNull("amount") },
                    candidate = item.optInt("candidate").takeIf { item.has("candidate") && !item.isNull("candidate") },
                    abilityId = item.optString("abilityId").takeIf { it.isNotBlank() },
                    abilityName = item.optString("abilityName").takeIf { it.isNotBlank() },
                    abilityDescription = item.optString("abilityDescription").takeIf { it.isNotBlank() },
                    abilityCost = item.optString("abilityCost").takeIf { it.isNotBlank() },
                    rationale = item.getString("rationale")
                ))
            }
        }
        return GuardianResponse(
            narration = json.getString("narration"),
            sceneTitle = json.getString("sceneTitle"),
            sceneDescription = json.getString("sceneDescription"),
            ruleRequest = ruleRequest,
            suggestedActions = actions,
            interactionId = json.optString("interactionId").takeIf { it.isNotBlank() },
            canonProposals = proposals,
            growthEvidenceProposals = growthProposals,
            growthChangeProposals = changeProposals,
            actionIntent = actionIntent
        )
    }

    private fun parseActionIntent(json: JSONObject?, exactJson: JsonObject?): GuardianActionIntent? {
        if (json == null || exactJson == null) return null
        return runCatching {
            require(exactJson.keySet() == setOf("type", "targetId", "targetName", "weaponId")) {
                "actionIntent contains missing or unsupported fields"
            }
            val type = GuardianActionType.valueOf(exactJson.getExactString("type"))
            val targetId = exactJson.getExactString("targetId")
            val targetName = exactJson.getExactString("targetName")
            val weaponIdElement = exactJson.get("weaponId")
            val weaponId = when {
                weaponIdElement == null || weaponIdElement.isJsonNull -> null
                weaponIdElement.isJsonPrimitive && weaponIdElement.asJsonPrimitive.isString -> weaponIdElement.asString
                else -> throw IllegalArgumentException("weaponId must be a string or null")
            }
            GuardianActionIntent(type, targetId, weaponId, targetName)
        }.getOrNull()
    }

    private fun parseRuleRequest(json: JSONObject, exactJson: JsonObject?): GuardianRuleRequest? {
        val type = json.optString("type").takeIf { it.isNotBlank() } ?: return null
        if (type.equals("BEGIN_COMBAT", ignoreCase = true)) {
            val encounter = runCatching {
                val exactEncounter = exactJson?.get("encounter")
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?: throw IllegalArgumentException("BEGIN_COMBAT encounter is missing or invalid")
                parseEncounter(json.getJSONObject("encounter"), exactEncounter)
            }.getOrNull()
            return GuardianRuleRequest(type = "BEGIN_COMBAT", encounter = encounter)
        }
        if (type.equals("REWARD", ignoreCase = true)) {
            val reward = runCatching {
                parseRewardProposal(exactJson ?: throw IllegalArgumentException("REWARD payload is missing"))
            }.getOrNull()
            return GuardianRuleRequest(type = "REWARD", reward = reward)
        }
        return runCatching {
            GuardianRuleRequest(
                type = type,
                attribute = json.optString("attribute").takeIf { it.isNotBlank() },
                amount = if (json.has("amount") && !json.isNull("amount")) json.getInt("amount") else null
            )
        }.getOrNull()
    }

    private fun parseRewardProposal(exactJson: JsonObject): GuardianRewardProposal {
        require(exactJson.keySet() == setOf("type", "id", "status", "amountGp", "itemCatalogIds")) {
            "REWARD payload contains missing or unsupported fields"
        }
        require(exactJson.getExactString("type").equals("REWARD", ignoreCase = true)) { "REWARD type is invalid" }
        return GuardianRewardProposal(
            id = exactJson.getExactString("id"),
            status = RewardStatus.valueOf(exactJson.getExactString("status")),
            amountGp = exactJson.getExactInt("amountGp"),
            itemCatalogIds = exactJson.getExactStringList("itemCatalogIds")
        )
    }

    private fun parseEncounter(json: JSONObject, exactJson: JsonObject): GuardianEncounterProposal {
        val opponents = json.getJSONArray("opponents")
        val exactOpponents = exactJson.get("opponents")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: throw IllegalArgumentException("Encounter opponents must be an array")
        require(exactOpponents.size() == opponents.length()) { "Encounter opponent data does not match the raw payload" }
        val proposals = (0 until opponents.length()).map { index ->
            val opponent = opponents.getJSONObject(index)
            val exactOpponent = exactOpponents[index]
                .takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: throw IllegalArgumentException("Encounter opponent must be an object")
            val narrative = opponent.getJSONObject("narrative")
            val exactStats = exactOpponent.get("stats")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: throw IllegalArgumentException("Encounter opponent stats must be an object")
            val weapon = opponent.getJSONObject("weapon")
            GuardianOpponentProposal(
                opponentId = opponent.getString("opponentId"),
                narrative = CombatOpponentNarrative(
                    name = narrative.getString("name"),
                    appearance = narrative.getString("appearance"),
                    behavior = narrative.getString("behavior"),
                    intent = narrative.getString("intent"),
                    context = narrative.getString("context")
                ),
                stats = CharacterState(
                    str = exactStats.getExactInt("str"), dex = exactStats.getExactInt("dex"), wil = exactStats.getExactInt("wil"),
                    hp = exactStats.getExactInt("hp"), maxHp = exactStats.getExactInt("maxHp"), armor = exactStats.getExactInt("armor")
                ),
                weapon = WeaponProfile(
                    id = weapon.getString("id"),
                    damage = weapon.getString("damage"),
                    blast = weapon.optBoolean("blast", false),
                    ranged = weapon.optBoolean("ranged", false)
                )
            )
        }
        val moraleLeaderId = when {
            !json.has("moraleLeaderId") || json.isNull("moraleLeaderId") -> null
            else -> json.get("moraleLeaderId") as? String
                ?: throw IllegalArgumentException("moraleLeaderId must be a string")
        }
        return GuardianEncounterProposal(
            opponents = proposals,
            moraleLeaderId = moraleLeaderId
        )
    }

    private fun JsonObject.getExactInt(key: String): Int {
        val value = get(key)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?: throw IllegalArgumentException("$key must be an integer")
        return java.math.BigDecimal(value.asJsonPrimitive.asNumber.toString()).intValueExact()
    }

    private fun JsonObject.getExactString(key: String): String {
        val value = get(key)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?: throw IllegalArgumentException("$key must be a string")
        return value.asString
    }

    private fun JsonObject.getExactStringList(key: String): List<String> {
        val values = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw IllegalArgumentException("$key must be an array")
        return values.map { value ->
            value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: throw IllegalArgumentException("$key entries must be strings")
        }
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
