package com.vanish994.cairnsolo.ai

import com.google.genai.Client
import com.vanish994.cairnsolo.game.MJContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiFlashLiteProvider(apiKey: String) : AIProvider {
    private val client: Client = Client.builder().apiKey(apiKey).build()

    override suspend fun narrate(request: WardenRequest): Result<WardenResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client.models.generateContent(
                    MODEL,
                    buildPrompt(request),
                    null
                )
                parseResponse(response.text())
            }
        }

    private fun buildPrompt(request: WardenRequest): String = buildString {
        appendLine(WARDEN_SYSTEM_PROMPT)
        appendLine()
        appendLine("# GAME STATE (somente leitura)")
        appendLine(contextJson(request.context).toString())
        appendLine()
        appendLine("# ÚLTIMO RESULTADO DO RULES ENGINE (autoritativo)")
        appendLine(request.rulesEngineResult.ifBlank { "Nenhum resultado mecânico novo." })
        appendLine()
        appendLine("# PLAYER INPUT")
        appendLine(request.playerInput)
        appendLine()
        appendLine("Responda SOMENTE com JSON válido, sem markdown, neste formato:")
        appendLine("{\"narrative\":\"texto em português\",\"suggested_actions\":[\"ação 1\",\"ação 2\"],\"intent\":null}")
        appendLine("Não inclua HP, dano, rolagens, inventário ou qualquer alteração mecânica inventada.")
    }

    private fun contextJson(context: MJContext): JSONObject = JSONObject().apply {
        put("campaign_id", context.campaignId)
        put("character", JSONObject().apply {
            put("id", context.characterId)
            put("name", context.characterName)
            put("hp", context.rules.hp)
            put("max_hp", context.rules.maxHp)
            put("armor", context.rules.armor)
            put("inventory", JSONArray().apply {
                context.rules.inventory.forEach { put(it.id) }
            })
        })
        put("scene", JSONObject().apply {
            put("id", context.sceneId)
            put("type", context.sceneType.name)
            put("title", context.sceneTitle)
            put("description", context.sceneDescription)
            put("exits", JSONArray(context.exits))
        })
        put("turn", context.turn)
        put("recent_log", JSONArray(context.recentLog))
    }

    private fun parseResponse(raw: String?): WardenResponse {
        require(!raw.isNullOrBlank()) { "Gemini retornou uma resposta vazia" }
        val json = JSONObject(raw.trim())
        val narrative = json.optString("narrative").trim()
        require(narrative.isNotBlank()) { "Resposta do Warden sem narrative" }
        require(narrative.length <= MAX_NARRATIVE_LENGTH) { "Narrativa excede o limite" }

        val actionsJson = json.optJSONArray("suggested_actions") ?: JSONArray()
        val actions = buildList {
            for (index in 0 until minOf(actionsJson.length(), 3)) {
                val action = actionsJson.optString(index).trim()
                if (action.isNotBlank() && action.length <= MAX_ACTION_LENGTH) add(action)
            }
        }
        val intent = json.optString("intent").takeIf { it.isNotBlank() && it != "null" }
        return WardenResponse(narrative, actions, intent)
    }

    private companion object {
        const val MODEL = "gemini-2.5-flash-lite"
        const val MAX_NARRATIVE_LENGTH = 4000
        const val MAX_ACTION_LENGTH = 120
        const val WARDEN_SYSTEM_PROMPT = """
# CAIRN SOLO — WARDEN
Você é o Warden de uma campanha solo de Cairn 2e. Você conduz a ficção, interpreta o mundo e narra consequências. O Rules Engine possui autoridade mecânica e o GameState é a fonte da verdade. Nunca invente ou altere HP, dano, atributos, rolagens, condições, itens ou inventário. Nunca controle decisões importantes do personagem do jogador. Descreva situações, informações e consequências coerentes; deixe o jogador decidir. Seja evocativo, conciso e útil para decisões.
"""
    }
}
