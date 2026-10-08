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
Você também pode propor BEGIN_COMBAT somente quando character.combat estiver ausente. Isso é uma proposta completa de 1 a 8 oponentes, não o início do combate: o jogador precisa aceitar o encontro inteiro no aplicativo. Use encounter.opponents como lista de objetos, cada um com opponentId único, narrativa completa, stats completos e weapon completa. IDs não podem estar vazios nem ter espaços no início/fim. moraleLeaderId é opcional e, se fornecido, deve corresponder a um ID da lista. Para cada oponente, HP deve ser pelo menos 1 e não pode exceder maxHp; weapon.damage deve usar apenas d4/d6/d8/d10/d12 (por exemplo, d6 ou d6+d8); Armor deve estar entre 0 e 3. Não declare iniciativa, ataque, dano ou qualquer consequência.
Com combate ativo, ataques são iniciados pelos controles do aplicativo. Não use DAMAGE para simular um ataque e não narre seu resultado antes de recebê-lo em ruleResult.
Você pode propor atualizações narrativas em canonProposals, mas elas não são fatos até serem validadas pelo aplicativo. Use apenas UPSERT_NPC, DISCOVER_LOCATION, ADD_IMPORTANT_ITEM, CREATE_QUEST, ADD_DISCOVERY ou ADD_RUMOR. Nunca altere HP, atributos, inventário, facções, Growth ou outros dados mecânicos.
O estado growth.evidence contém experiências já registradas pelo domínio. Não crie evidências, habilidades ou aumentos de atributo por conta própria. Se uma experiência parecer um gatilho de Growth, narre a consequência e aguarde o fluxo de Growth do aplicativo.
Use apenas fatos presentes em worldCanon, world, growth e recentHistory. Não invente NPCs, facções, agendas, relações ou experiências passadas; qualquer novo fato deve ser apenas uma proposta de cânone validável.
Quando uma experiência significativa estiver sustentada pela cena atual, você pode preencher growthEvidenceProposals. Isso é apenas uma proposta: o aplicativo valida ID, resumo, entidades relacionadas e os gatilhos focusedPattern, seriousRisk e uniqueInteraction antes de registrá-la. Nunca proponha uma habilidade ou aumento de atributo nesse campo.
Você também pode preencher growthChangeProposals somente quando as evidências referenciadas já estiverem no estado growth.evidence ou forem propostas na mesma resposta. Use RAISE_MAX_ATTRIBUTE, KEEP_HIGHER_ATTRIBUTE ou GAIN_ABILITY. A proposta nunca é uma aplicação: o domínio valida as evidências, limites, IDs e duplicidade antes de alterar o personagem.
O objeto campaign recebido é um GuardianContext controlado: campaignId, campaignSeed, character, scene, world, canon, growth, recentHistory, recentNarrative e availableActions. O combate, quando ativo, contém apenas o perfil narrativo aprovado e fatos públicos necessários. Não espere campos internos de persistência e não tente inferir dados que não estejam nessa visão.
Se encounterContext estiver presente, ele é uma lista de perfis narrativos já aprovados pelo jogador; cada item associa um opponentId à sua narrative. Use esses IDs para preservar a continuidade dos oponentes, inclusive após o combate. Essa lista NÃO autoriza inferir nem declarar resultados mecânicos: somente ruleResult autoriza narrar consequências mecânicas.

INÍCIO DE CAMPANHA:
Quando playerIntent indicar que uma nova campanha está começando, nunca use um prólogo fixo, a frase de exemplo da aplicação ou uma estrutura copiada de outra campanha. Gere uma abertura inédita usando character, scene, world e campaignSeed como sementes narrativas. Apresente imediatamente uma situação concreta que desperte curiosidade e ofereça algo para observar, investigar ou decidir. Não diga que a história está começando e não mencione a seed. Não conceda resultados mecânicos nessa abertura.

VARIAÇÃO NARRATIVA:
Evite repetir frases, imagens, locais, eventos ou estruturas de recentHistory. Se algo já apareceu no histórico, mude o enquadramento e use outra manifestação coerente com o cânone. A seed identifica a campanha, mas não autoriza inventar fatos fora de world, canon e das propostas validáveis.

Escreva em português brasileiro, com atmosfera de fantasia sombria e prosa objetiva.
Não conduza o jogador por escolhas obrigatórias: apresente a situação e deixe espaço para ações livres.

A resposta DEVE ser somente o objeto JSON solicitado pelo schema.
"""

internal fun guardianSystemPrompt(): String = "$SYSTEM_PROMPT\n\n" +
    "AÇÕES SUGERIDAS (suggestedActions): declare claramente o objetivo imediato da cena e " +
        "apresente de uma a três recomendações opcionais, curtas e concretas para o próximo passo; " +
        "cada sugestão deve ser apoiada explicitamente em availableActions, na cena atual e no " +
        "campo canon (cânone confirmado), e não invente " +
        "fatos, locais, NPCs, missões ou saídas. São apenas recomendações: não executam ações " +
        "nem alteram o estado do jogo, e o jogador continua livre para escrever outra intenção."

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
            val gemini = normalizeCombatProposal(callGemini(apiKey, request))
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
FATIGUE (amount), REST, STABILIZE_CRITICAL, RECOVER_SCAR ou BEGIN_COMBAT (encounter com 1–8 oponentes em `opponents`, somente sem combate ativo).
BEGIN_COMBAT apenas propõe o encontro inteiro para confirmação do jogador. Não informe resultados; o aplicativo resolve.
Se não houver resolução mecânica, use null.
Se houver um ruleResult na solicitação, trate-o como resultado autoritativo do motor e narre somente suas consequências mecânicas autorizadas.
Inclua canonProposals como uma lista, mesmo quando vazia. Cada proposta deve ter type, id, status e source.
Inclua growthEvidenceProposals como uma lista, mesmo quando vazia. Cada item deve ter id, summary, relatedEntityIds e pelo menos um gatilho verdadeiro.
Inclua growthChangeProposals como uma lista, mesmo quando vazia. Não invente resultados; use apenas mudanças sustentadas pelas evidências disponíveis.
""".trimIndent()

    val schema = JsonObject().apply {
        addProperty("type", "object")
        val properties = JsonParser.parseString("""
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
              "canonProposals": {"type":"array","maxItems":5,"items":{"type":"object","properties":{"type":{"type":"string","enum":["UPSERT_NPC","DISCOVER_LOCATION","ADD_IMPORTANT_ITEM","CREATE_QUEST","UPDATE_QUEST","ADD_DISCOVERY","ADD_RUMOR"]},"id":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"},"status":{"type":"string","enum":["CONFIRMED","RUMOR","DISCOVERED"]},"source":{"type":"string","enum":["PLAYER","GUARDIAN","NPC","RULES_ENGINE","SYSTEM"]},"name":{"type":"string"},"title":{"type":"string"},"text":{"type":"string"},"description":{"type":"string"},"role":{"type":"string"},"relatedEntityIds":{"type":"array","items":{"type":"string"}}},"required":["type","id","status","source"],"additionalProperties":false}},
              "growthEvidenceProposals": {"type":"array","maxItems":3,"items":{"type":"object","properties":{"id":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"},"summary":{"type":"string","minLength":1,"maxLength":1000},"relatedEntityIds":{"type":"array","items":{"type":"string"}},"focusedPattern":{"type":"boolean"},"seriousRisk":{"type":"boolean"},"uniqueInteraction":{"type":"boolean"}},"required":["id","summary","relatedEntityIds","focusedPattern","seriousRisk","uniqueInteraction"],"additionalProperties":false}}
              ,"growthChangeProposals": {"type":"array","maxItems":3,"items":{"type":"object","properties":{"id":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"},"evidenceIds":{"type":"array","minItems":1,"items":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"}},"changeType":{"type":"string","enum":["RAISE_MAX_ATTRIBUTE","KEEP_HIGHER_ATTRIBUTE","GAIN_ABILITY"]},"attribute":{"type":"string","enum":["STR","DEX","WIL"]},"amount":{"type":"integer","minimum":1,"maximum":3},"candidate":{"type":"integer","minimum":3,"maximum":18},"abilityId":{"type":"string","pattern":"^[a-z0-9-]{3,80}$"},"abilityName":{"type":"string","maxLength":160},"abilityDescription":{"type":"string","maxLength":1000},"abilityCost":{"type":"string","maxLength":200},"rationale":{"type":"string","minLength":1,"maxLength":1000}},"required":["id","evidenceIds","changeType","rationale"],"additionalProperties":false}}
            }
        """).asJsonObject
        properties.add("suggestedActions", suggestedActionsSchema())
        replaceBeginCombatSchema(properties)
        add("properties", properties)
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
        addProperty("system_instruction", guardianSystemPrompt())
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

private fun replaceBeginCombatSchema(properties: JsonObject) {
    val alternatives = properties.getAsJsonObject("ruleRequest").getAsJsonArray("anyOf")
    val index = alternatives.indexOfFirst { candidate ->
        val types = candidate.asJsonObject
            .getAsJsonObject("properties")
            ?.getAsJsonObject("type")
            ?.getAsJsonArray("enum")
        types?.any { it.asString == "BEGIN_COMBAT" } == true
    }
    check(index >= 0) { "BEGIN_COMBAT schema branch is missing." }
    alternatives.set(index, JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("type", JsonObject().apply {
                addProperty("type", "string")
                add("enum", JsonParser.parseString("""["BEGIN_COMBAT"]""").asJsonArray)
            })
            add("encounter", combatEncounterSchema())
        })
        add("required", JsonParser.parseString("""["type","encounter"]""").asJsonArray)
        addProperty("additionalProperties", false)
    })
}

// Keep string bounds and patterns in isCompleteEncounterProposal(): Gemini structured output
// supports a smaller JSON Schema subset, and validating them here can make the whole request fail.
internal fun combatEncounterSchema(): JsonObject = JsonParser.parseString(
    """
    {
      "type":"object",
      "properties":{
        "opponents":{
          "type":"array","minItems":1,"maxItems":8,
          "items":{
            "type":"object",
            "properties":{
              "opponentId":{"type":"string"},
              "narrative":{
                "type":"object",
                "properties":{
                  "name":{"type":"string"},
                  "appearance":{"type":"string"},
                  "behavior":{"type":"string"},
                  "intent":{"type":"string"},
                  "context":{"type":"string"}
                },
                "required":["name","appearance","behavior","intent","context"],"additionalProperties":false
              },
              "stats":{
                "type":"object",
                "properties":{
                  "str":{"type":"integer","minimum":0},"dex":{"type":"integer","minimum":0},"wil":{"type":"integer","minimum":0},
                  "hp":{"type":"integer","minimum":1},"maxHp":{"type":"integer","minimum":1},
                  "armor":{"type":"integer","minimum":0,"maximum":3}
                },
                "required":["str","dex","wil","hp","maxHp","armor"],"additionalProperties":false
              },
              "weapon":{
                "type":"object",
                "properties":{
                  "id":{"type":"string"},
                  "damage":{"type":"string"},
                  "blast":{"type":"boolean"},"ranged":{"type":"boolean"}
                },
                "required":["id","damage","blast","ranged"],"additionalProperties":false
              }
            },
            "required":["opponentId","narrative","stats","weapon"],"additionalProperties":false
          }
        },
        "moraleLeaderId":{"type":"string"}
      },
      "required":["opponents"],"additionalProperties":false
    }
    """.trimIndent()
).asJsonObject

internal fun suggestedActionsSchema(): JsonObject = JsonObject().apply {
    addProperty("type", "array")
    addProperty("minItems", 1)
    addProperty("maxItems", 3)
    add("items", JsonObject().apply {
        addProperty("type", "string")
        addProperty("minLength", 1)
        addProperty("maxLength", 160)
    })
}

internal fun normalizeCombatProposal(response: JsonObject): JsonObject {
    val ruleRequest = response.get("ruleRequest")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject ?: return response
    val type = runCatching { ruleRequest.get("type")?.asString }.getOrNull()
    if (!type.equals("BEGIN_COMBAT", ignoreCase = true)) return response

    val encounter = ruleRequest.get("encounter")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
    if (!isCompleteEncounterProposal(encounter)) {
        // Preserve the scene; the client turns this incomplete proposal into a visible validation error.
        ruleRequest.remove("encounter")
    }
    return response
}

private fun isCompleteEncounterProposal(encounter: JsonObject?): Boolean {
    if (encounter == null) return false
    val opponents = encounter.get("opponents")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
    if (opponents.size() !in 1..8) return false

    val opponentIds = mutableSetOf<String>()
    for (element in opponents) {
        if (!element.isJsonObject) return false
        val opponent = element.asJsonObject
        val id = jsonString(opponent.get("opponentId")) ?: return false
        if (id.isBlank() || id != id.trim() || id.length > 80 || !opponentIds.add(id)) return false

        val narrative = opponent.get("narrative")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        if (!hasBoundedText(narrative.get("name"), 160) ||
            listOf("appearance", "behavior", "intent", "context").any { !hasBoundedText(narrative.get(it), 1000) }
        ) return false

        val stats = opponent.get("stats")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        val attributes = listOf("str", "dex", "wil").map { jsonInteger(stats.get(it)) ?: return false }
        if (attributes.any { it < 0 }) return false
        val hp = jsonInteger(stats.get("hp")) ?: return false
        val maxHp = jsonInteger(stats.get("maxHp")) ?: return false
        val armor = jsonInteger(stats.get("armor")) ?: return false
        if (hp < 1 || maxHp < hp || armor !in 0..3) return false

        val weapon = opponent.get("weapon")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        val weaponId = jsonString(weapon.get("id")) ?: return false
        if (!hasBoundedText(weapon.get("id"), 80, requireTrimmed = true) || weaponId != weaponId.trim()) return false
        val damage = jsonString(weapon.get("damage")) ?: return false
        if (damage.isBlank() || damage.length > 32 || !SUPPORTED_COMBAT_DAMAGE.matches(damage)) return false
        if (jsonBoolean(weapon.get("blast")) == null || jsonBoolean(weapon.get("ranged")) == null) return false
    }

    if (encounter.has("moraleLeaderId")) {
        val leaderId = jsonString(encounter.get("moraleLeaderId")) ?: return false
        if (leaderId.isBlank() || leaderId != leaderId.trim() || leaderId !in opponentIds) return false
    }
    return true
}

private fun jsonString(value: com.google.gson.JsonElement?): String? =
    value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun hasBoundedText(value: com.google.gson.JsonElement?, maxLength: Int, requireTrimmed: Boolean = false): Boolean {
    val text = jsonString(value) ?: return false
    return text.isNotBlank() && text.length <= maxLength && (!requireTrimmed || text == text.trim())
}

private fun jsonInteger(value: com.google.gson.JsonElement?): Int? =
    value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.let { runCatching { java.math.BigDecimal(it.asString).intValueExact() }.getOrNull() }

private fun jsonBoolean(value: com.google.gson.JsonElement?): Boolean? =
    value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

private val SUPPORTED_COMBAT_DAMAGE = Regex("""^d(4|6|8|10|12)(\s*\+\s*d(4|6|8|10|12))*$""")

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
