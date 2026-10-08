package com.vanish994.cairnsolo.guardian

import android.util.Log
import com.vanish994.cairnsolo.BuildConfig
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

data class GuardianRuleRequest(
    val type: String,
    val attribute: String? = null,
    val amount: Int? = null,
    val encounter: GuardianEncounterProposal? = null
)

data class GuardianEncounterProposal(
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
        require(weapon.id.isNotBlank() && weapon.id.length <= 80)
        val damageExpression = weapon.damage?.trim().orEmpty()
        require(damageExpression.isNotBlank() && damageExpression.length <= 32)
        require(isSupportedWeaponDamageExpression(damageExpression))
    }
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
    val growthChangeProposals: List<GrowthChangeProposal> = emptyList()
)

interface GuardianClient {
    suspend fun narrate(
        state: GameState,
        playerIntent: String,
        ruleResult: String? = null,
        encounterContext: CombatOpponentNarrative? = null
    ): Result<GuardianResponse>
}

internal fun guardianRequestPayload(
    state: GameState,
    playerIntent: String,
    ruleResult: String? = null,
    encounterContext: CombatOpponentNarrative? = null
): JSONObject = JSONObject().apply {
    put("playerIntent", playerIntent.trim())
    put("campaign", GuardianContextBuilder.from(state).toJson())
    ruleResult?.takeIf { it.isNotBlank() }?.let { put("ruleResult", it.trim()) }
    encounterContext?.let { profile ->
        put("encounterContext", JSONObject().apply {
            put("name", profile.name)
            put("appearance", profile.appearance)
            put("behavior", profile.behavior)
            put("intent", profile.intent)
            put("context", profile.context)
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
        encounterContext: CombatOpponentNarrative?
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
        encounterContext: CombatOpponentNarrative?
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
        val json = JSONObject(body)
        val ruleObject = json.optJSONObject("ruleRequest")
        val ruleRequest = ruleObject?.let(::parseRuleRequest)
        val actions = buildList {
            val array = json.optJSONArray("suggestedActions") ?: JSONArray()
            for (i in 0 until array.length()) add(array.getString(i))
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
            growthChangeProposals = changeProposals
        )
    }

    private fun parseRuleRequest(json: JSONObject): GuardianRuleRequest? {
        val type = json.optString("type").takeIf { it.isNotBlank() } ?: return null
        if (type.equals("BEGIN_COMBAT", ignoreCase = true)) {
            val encounter = runCatching { parseEncounter(json.getJSONObject("encounter")) }.getOrNull()
            return GuardianRuleRequest(type = "BEGIN_COMBAT", encounter = encounter)
        }
        return runCatching {
            GuardianRuleRequest(
                type = type,
                attribute = json.optString("attribute").takeIf { it.isNotBlank() },
                amount = if (json.has("amount") && !json.isNull("amount")) json.getInt("amount") else null
            )
        }.getOrNull()
    }

    private fun parseEncounter(json: JSONObject): GuardianEncounterProposal {
        val narrative = json.getJSONObject("narrative")
        val stats = json.getJSONObject("stats")
        val weapon = json.getJSONObject("weapon")
        return GuardianEncounterProposal(
            opponentId = json.getString("opponentId"),
            narrative = CombatOpponentNarrative(
                name = narrative.getString("name"),
                appearance = narrative.getString("appearance"),
                behavior = narrative.getString("behavior"),
                intent = narrative.getString("intent"),
                context = narrative.getString("context")
            ),
            stats = CharacterState(
                str = stats.getInt("str"), dex = stats.getInt("dex"), wil = stats.getInt("wil"),
                hp = stats.getInt("hp"), maxHp = stats.getInt("maxHp"), armor = stats.getInt("armor")
            ),
            weapon = WeaponProfile(
                id = weapon.getString("id"),
                damage = weapon.getString("damage"),
                blast = weapon.optBoolean("blast", false),
                ranged = weapon.optBoolean("ranged", false)
            )
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
