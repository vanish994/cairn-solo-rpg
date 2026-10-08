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
import java.util.concurrent.Executors

private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
private const val MODEL = "gemini-3.5-flash-lite"

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
Nunca diga que um teste foi bem-sucedido, que dano foi causado ou que um item foi obtido, a menos que o campo ruleResult forneça explicitamente esse fato.
Quando uma ação exigir resolução mecânica, preencha ruleRequest com um pedido estruturado e deixe o aplicativo resolver. Use SAVE, DAMAGE, FATIGUE, REST, STABILIZE_CRITICAL ou RECOVER_SCAR conforme apropriado; BEGIN_COMBAT é apenas uma proposta de encontro para aprovação do jogador.
Você também pode propor BEGIN_COMBAT somente quando character.combat estiver ausente. Isso é uma proposta de oponente, não o início do combate: o jogador precisa aceitar no aplicativo. Inclua aparência, comportamento, intenção, contexto, stats e weapon completos; weapon.damage deve usar apenas d4/d6/d8/d10/d12 (por exemplo, d6 ou d6+d8); Armor deve estar entre 0 e 3. Não declare iniciativa, ataque, dano ou qualquer consequência.
Com combate ativo, ataques são iniciados pelos controles do aplicativo. Não use DAMAGE para simular um ataque e não narre seu resultado antes de recebê-lo em ruleResult.
Você pode propor atualizações narrativas em canonProposals, mas elas não são fatos até serem validadas pelo aplicativo. Use apenas UPSERT_NPC, DISCOVER_LOCATION, ADD_IMPORTANT_ITEM, CREATE_QUEST, ADD_DISCOVERY ou ADD_RUMOR. Nunca altere HP, atributos, inventário, facções, Growth ou outros dados mecânicos.
O estado growth.evidence contém experiências já registradas pelo domínio. Não crie evidências, habilidades ou aumentos de atributo por conta própria. Se uma experiência parecer um gatilho de Growth, narre a consequência e aguarde o fluxo de Growth do aplicativo.
Use apenas fatos presentes em worldCanon, world, growth e recentHistory. Não invente NPCs, facções, agendas, relações ou experiências passadas; qualquer novo fato deve ser apenas uma proposta de cânone validável.
Quando uma experiência significativa estiver sustentada pela cena atual, você pode preencher growthEvidenceProposals. Isso é apenas uma proposta: o aplicativo valida ID, resumo, entidades relacionadas e os gatilhos focusedPattern, seriousRisk e uniqueInteraction antes de registrá-la. Nunca proponha uma habilidade ou aumento de atributo nesse campo.
Você também pode preencher growthChangeProposals somente quando as evidências referenciadas já estiverem no estado growth.evidence ou forem propostas na mesma resposta. Use RAISE_MAX_ATTRIBUTE, KEEP_HIGHER_ATTRIBUTE ou GAIN_ABILITY. A proposta nunca é uma aplicação: o domínio valida as evidências, limites, IDs e duplicidade antes de alterar o personagem.
O objeto campaign recebido é um GuardianContext controlado: campaignId, campaignSeed, character, scene, world, canon, growth, recentHistory, recentNarrative e availableActions. O combate, quando ativo, contém apenas o perfil narrativo aprovado e fatos públicos necessários. Não espere campos internos de persistência e não tente inferir dados que não estejam nessa visão.
Se encounterContext estiver presente, ele é um perfil narrativo de oponente já aprovado pelo jogador para preservar a continuidade após o combate. Ele não autoriza inferir nem declarar resultados mecânicos.

INÍCIO DE CAMPANHA:
Quando playerIntent indicar que uma nova campanha está começando, nunca use um prólogo fixo, a frase de exemplo da aplicação ou uma estrutura copiada de outra campanha. Gere uma abertura inédita usando character, scene, world e campaignSeed como sementes narrativas. Apresente imediatamente uma situação concreta que desperte curiosidade e ofereça algo para observar, investigar ou decidir. Não diga que a história está começando e não mencione a seed. Não conceda resultados mecânicos nessa abertura.

VARIAÇÃO NARRATIVA:
Evite repetir frases, imagens, locais, eventos ou estruturas de recentHistory. Se algo já apareceu no histórico, mude o enquadramento e use outra manifestação coerente com o cânone. A seed identifica a campanha, mas não autoriza inventar fatos fora de world, canon e das propostas validáveis.

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
    // O request /guardian pode aguardar a API Gemini por dezenas de segundos.
    // Um executor compartilhado impede que essa espera bloqueie o health check do Render.
    server.executor = Executors.newFixedThreadPool(4)
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
${gson.toJson(game)}

Intenção do jogador:
${game.get("playerIntent").asString}

Se esta for a solicitação de abertura de campanha, trate campaignSeed como uma semente única de variação e produza somente a primeira situação jogável. Não avance automaticamente para outro local e não resolva uma regra.
Se playerIntent começar com CONTINUAR_NARRATIVA, isso é um comando interno da interface, não uma fala ou decisão do jogador. Não o mencione na narração, não o inclua como diálogo e avance a situação atual organicamente no mesmo local.
Se ruleResult estiver presente, ele foi produzido pelo Rules Engine e é a única fonte autorizada para narrar os efeitos mecânicos daquela ação. Não acrescente números, resultados, condições ou consequências mecânicas não contidos nesse campo.

Continue a cena de forma coerente. Se a intenção exigir uma resolução mecânica, preencha ruleRequest
como objeto com type e os campos necessários. Use SAVE (attribute STR/DEX/WIL), DAMAGE (amount),
FATIGUE (amount), REST, STABILIZE_CRITICAL, RECOVER_SCAR ou BEGIN_COMBAT (encounter completo, somente sem combate ativo).
BEGIN_COMBAT apenas propõe o encontro para confirmação do jogador. Não informe resultados; o aplicativo resolve.
Se não houver resolução mecânica, use null.
Se houver um ruleResult na solicitação, trate-o como resultado autoritativo do motor e narre somente suas consequências mecânicas autorizadas.
Inclua canonProposals como uma lista, mesmo quando vazia. Cada proposta deve ter type, id, status e source.
Inclua growthEvidenceProposals como uma lista, mesmo quando vazia. Cada item deve ter id, summary, relatedEntityIds e pelo menos um gatilho verdadeiro.
Inclua growthChangeProposals como uma lista, mesmo quando vazia. Não invente resultados; use apenas mudanças sustentadas pelas evidências disponíveis.
""".trimIndent()

    val schema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonParser.parseString("""
            {
              "narration": {"type":"string"},
              "sceneTitle": {"type":"string"},
              "sceneDescription": {"type":"string"},
              "ruleRequest": {"anyOf": [
                {"type":"null"},
                {"type":"object","properties":{"type":{"type":"string","enum":["SAVE"]},"attribute":{"type":"string","enum":["STR","DEX","WIL"]}},"required":["type","attribute"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["DAMAGE"]},"amount":{"type":"integer","minimum":1}},"required":["type","amount"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["FATIGUE"]},"amount":{"type":"integer","minimum":1}},"required":["type","amount"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["REST"]}},"required":["type"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["STABILIZE_CRITICAL"]}},"required":["type"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["RECOVER_SCAR"]}},"required":["type"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["BEGIN_COMBAT"]},"encounter":{"type":"object","properties":{"opponentId":{"type":"string","minLength":1,"maxLength":80},"narrative":{"type":"object","properties":{"name":{"type":"string","minLength":1,"maxLength":160},"appearance":{"type":"string","minLength":1,"maxLength":1000},"behavior":{"type":"string","minLength":1,"maxLength":1000},"intent":{"type":"string","minLength":1,"maxLength":1000},"context":{"type":"string","minLength":1,"maxLength":1000}},"required":["name","appearance","behavior","intent","context"],"additionalProperties":false},"stats":{"type":"object","properties":{"str":{"type":"integer","minimum":0},"dex":{"type":"integer","minimum":0},"wil":{"type":"integer","minimum":0},"hp":{"type":"integer","minimum":1},"maxHp":{"type":"integer","minimum":1},"armor":{"type":"integer","minimum":0,"maximum":3}},"required":["str","dex","wil","hp","maxHp","armor"],"additionalProperties":false},"weapon":{"type":"object","properties":{"id":{"type":"string","minLength":1,"maxLength":80},"damage":{"type":"string","minLength":1,"maxLength":32,"pattern":"^d(4|6|8|10|12)(\\s*\\+\\s*d(4|6|8|10|12))*$"},"blast":{"type":"boolean"},"ranged":{"type":"boolean"}},"required":["id","damage","blast","ranged"],"additionalProperties":false}},"required":["opponentId","narrative","stats","weapon"],"additionalProperties":false}},"required":["type","encounter"],"additionalProperties":false}
              ]},
              "suggestedActions": {"type":"array","items":{"type":"string"}},
              "canonProposals": {"type":"array","maxItems":5,"items":{"type":"object","properties":{"type":{"type":"string","enum":["UPSERT_NPC","DISCOVER_LOCATION","ADD_IMPORTANT_ITEM","CREATE_QUEST","UPDATE_QUEST","ADD_DISCOVERY","ADD_RUMOR"]},"id":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"},"status":{"type":"string","enum":["CONFIRMED","RUMOR","DISCOVERED"]},"source":{"type":"string","enum":["PLAYER","GUARDIAN","NPC","RULES_ENGINE","SYSTEM"]},"name":{"type":"string"},"title":{"type":"string"},"text":{"type":"string"},"description":{"type":"string"},"role":{"type":"string"},"relatedEntityIds":{"type":"array","items":{"type":"string"}}},"required":["type","id","status","source"],"additionalProperties":false}},
              "growthEvidenceProposals": {"type":"array","maxItems":3,"items":{"type":"object","properties":{"id":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"},"summary":{"type":"string","minLength":1,"maxLength":1000},"relatedEntityIds":{"type":"array","items":{"type":"string"}},"focusedPattern":{"type":"boolean"},"seriousRisk":{"type":"boolean"},"uniqueInteraction":{"type":"boolean"}},"required":["id","summary","relatedEntityIds","focusedPattern","seriousRisk","uniqueInteraction"],"additionalProperties":false}}
              ,"growthChangeProposals": {"type":"array","maxItems":3,"items":{"type":"object","properties":{"id":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"},"evidenceIds":{"type":"array","minItems":1,"items":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"}},"changeType":{"type":"string","enum":["RAISE_MAX_ATTRIBUTE","KEEP_HIGHER_ATTRIBUTE","GAIN_ABILITY"]},"attribute":{"type":"string","enum":["STR","DEX","WIL"]},"amount":{"type":"integer","minimum":1,"maximum":3},"candidate":{"type":"integer","minimum":3,"maximum":18},"abilityId":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"},"abilityName":{"type":"string","maxLength":160},"abilityDescription":{"type":"string","maxLength":1000},"abilityCost":{"type":"string","maxLength":200},"rationale":{"type":"string","minLength":1,"maxLength":1000}},"required":["id","evidenceIds","changeType","rationale"],"additionalProperties":false}}
            }
        """).asJsonObject)
        add("required", JsonParser.parseString(
            """["narration","sceneTitle","sceneDescription","ruleRequest","suggestedActions","canonProposals","growthEvidenceProposals","growthChangeProposals"]"""
        ).asJsonArray)
        addProperty("additionalProperties", false)
    }

    val requestBody = JsonObject().apply {
        addProperty("model", MODEL)
        addProperty("input", input)
        game.get("campaign")?.asJsonObject
            ?.get("guardianInteractionId")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?.let {
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
        error("Gemini HTTP ${response.statusCode()}: ${response.body().take(500)}")
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
