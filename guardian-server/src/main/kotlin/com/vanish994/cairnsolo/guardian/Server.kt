package com.vanish994.cairnsolo.guardian

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
private const val MODEL = "gemini-3.6-flash"

private val gson = Gson()
private val http = HttpClient.newBuilder().build()

private const val SYSTEM_PROMPT = """
Você é o Guardião narrativo de uma campanha solo de Cairn.

Sua função é narrar o mundo, interpretar a intenção do jogador, apresentar consequências narrativas,
criar e interpretar NPCs, descrever ambientes e manter continuidade.

REGRA FUNDAMENTAL:
Você NÃO é a autoridade das regras.
O motor de regras do jogo é a única autoridade para testes, HP, dano, armadura, inventário,
condições, morte, recursos e resultados aleatórios.

Nunca invente ou altere valores mecânicos.
Nunca diga que um teste foi bem-sucedido, que dano foi causado ou que um item foi obtido.
Quando uma ação exigir resolução mecânica, preencha ruleRequest com um pedido estruturado e deixe o aplicativo resolver. Use apenas SAVE, DAMAGE, FATIGUE, REST, STABILIZE_CRITICAL ou RECOVER_SCAR.

Escreva em português brasileiro, com atmosfera de fantasia sombria e prosa objetiva.
Não conduza o jogador por escolhas obrigatórias: apresente a situação e deixe espaço para ações livres.

A resposta DEVE ser somente o objeto JSON solicitado pelo schema.
"""

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
    server.createContext("/health") { exchange ->
        respond(exchange, 200, """{"status":"ok","service":"cairn-guardian"}""")
    }
    server.createContext("/guardian") { exchange ->
        if (exchange.requestMethod != "POST") {
            respond(exchange, 405, """{"error":"method_not_allowed"}""")
            return@createContext
        }
        try {
            val apiKey = System.getenv("GEMINI_API_KEY")
                ?: error("GEMINI_API_KEY is not configured")
            val body = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val request = JsonParser.parseString(body).asJsonObject
            validateRequest(request)
            val gemini = callGemini(apiKey, request)
            respond(exchange, 200, gemini.toString())
        } catch (e: Exception) {
            respond(exchange, 500, gson.toJson(mapOf("error" to (e.message ?: "guardian_error"))))
        }
    }
    server.executor = null
    server.start()
    println("Cairn Guardian server listening on :$port")
}

private fun validateRequest(request: JsonObject) {
    require(request.has("playerIntent")) { "playerIntent is required" }
    require(request.get("playerIntent").asString.trim().isNotEmpty()) { "playerIntent is empty" }
    require(request.has("campaign")) { "campaign is required" }
}

private fun callGemini(apiKey: String, game: JsonObject): JsonObject {
    val input = """
Estado atual da campanha:
\${gson.toJson(game)}

Intenção do jogador:
\${game.get("playerIntent").asString}

Continue a cena de forma coerente. Se a intenção exigir uma resolução mecânica, preencha ruleRequest
como objeto com type e os campos necessários. Use apenas SAVE (attribute STR/DEX/WIL), DAMAGE (amount),
FATIGUE (amount), REST, STABILIZE_CRITICAL ou RECOVER_SCAR. Não informe resultados; o aplicativo resolve.
Se não houver resolução mecânica, use null.
Se houver um ruleResult no estado, trate-o como resultado autoritativo do motor e narre suas consequências.
""".trimIndent()

    val schema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonParser.parseString("""
            {
              "narration": {"type":"string"},
              "sceneTitle": {"type":"string"},
              "sceneDescription": {"type":"string"},
              "ruleRequest": {"anyOf":[{"type":"null"},{"type":"object","properties":{"type":{"type":"string","enum":["SAVE","DAMAGE","FATIGUE","REST","STABILIZE_CRITICAL","RECOVER_SCAR"]},"attribute":{"type":"string","enum":["STR","DEX","WIL"]},"amount":{"type":"integer","minimum":1}},"required":["type"],"additionalProperties":false]},
              "suggestedActions": {"type":"array","items":{"type":"string"}}
            }
        """).asJsonObject)
        add("required", JsonParser.parseString(
            """["narration","sceneTitle","sceneDescription","ruleRequest","suggestedActions"]"""
        ).asJsonArray)
        addProperty("additionalProperties", false)
    }

    val requestBody = JsonObject().apply {
        addProperty("model", MODEL)
        addProperty("input", input)
        game.get("campaign")?.asJsonObject?.get("guardianInteractionId")?.asString?.takeIf { it.isNotBlank() }?.let {
            addProperty("previous_interaction_id", it)
        }
        addProperty("system_instruction", SYSTEM_PROMPT)
        add("generation_config", JsonParser.parseString(
            """{"max_output_tokens":700,"thinking_level":"low"}"""
        ))
        add("response_format", JsonParser.parseString(
            gson.toJson(mapOf(
                "type" to "text",
                "mime_type" to "application/json",
                "schema" to schema
            ))
        ))
        addProperty("store", true)
    }

    val request = HttpRequest.newBuilder(URI(GEMINI_URL))
        .header("Content-Type", "application/json")
        .header("x-goog-api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
        .build()

    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) {
        error("Gemini HTTP \${response.statusCode()}: \${response.body().take(500)}")
    }

    val root = JsonParser.parseString(response.body()).asJsonObject
    val steps = root.getAsJsonArray("steps") ?: error("Gemini response has no steps")
    for (i in steps.size() - 1 downTo 0) {
        val step = steps[i].asJsonObject
        if (step.get("type")?.asString == "model_output") {
            val content = step.getAsJsonArray("content") ?: continue
            for (j in content.size() - 1 downTo 0) {
                val block = content[j].asJsonObject
                if (block.get("type")?.asString == "text") {
                    val narrative = JsonParser.parseString(block.get("text").asString).asJsonObject
                    narrative.addProperty("interactionId", root.get("id").asString)
                    return narrative
                }
            }
        }
    }
    error("Gemini response did not contain model text")
}

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
