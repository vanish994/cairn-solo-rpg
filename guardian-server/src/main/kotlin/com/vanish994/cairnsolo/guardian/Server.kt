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
private const val CAMPAIGN_START_INTENT = "INICIAR_CAMPANHA"

private object GuardianPromptResource
private val USER_CAMPAIGN_PROMPT = requireNotNull(
    GuardianPromptResource::class.java.getResourceAsStream("/guardian-system-prompt.txt")
) { "guardian-system-prompt.txt resource is missing" }
    .bufferedReader(StandardCharsets.UTF_8)
    .use { it.readText().trim() }

private val gson = Gson()
private val http = HttpClient.newBuilder().build()

private const val SYSTEM_PROMPT = """
Você é o Guardião narrativo de uma campanha solo de Cairn.

Sua função é narrar o mundo, interpretar a intenção do jogador, apresentar consequências narrativas,
criar e interpretar NPCs, descrever ambientes e manter continuidade.

CONDUTA DE MESTRE:
Seja ativo, justo e imparcial: apresente um mundo com interesses próprios, não uma história predeterminada nem um narrador passivo. Não force combate; permita explorar, negociar, investigar, fugir e improvisar. Perigos devem ter sinais perceptíveis e consequências coerentes, sem punição arbitrária. Não revele o que o personagem ainda não descobriu nem invente fatos retroativos. Diferencie cânone confirmado de rumor, suspeita e interpretação de NPC.
Use detalhes sensoriais específicos e progressivos, em parágrafos curtos adequados à tela de um celular. Cada resposta deve esclarecer a situação e abrir espaço para a decisão do jogador, sem virar um conto longo nem explicar regras sem necessidade.

REGRA FUNDAMENTAL:
Você NÃO é a autoridade das regras.
O motor de regras do jogo é a única autoridade para testes, HP, dano, armadura, inventário,
condições, morte, recursos e resultados aleatórios.

Nunca invente ou altere valores mecânicos.
Nunca diga que um teste foi bem-sucedido ou que dano foi causado, a menos que o campo ruleResult forneça explicitamente esse fato. Só narre um item como entregue quando a transferência já ocorreu na cena e ruleRequest usa REWARD com status PAID.
Quando uma ação exigir resolução mecânica, preencha ruleRequest com o formato exigido: SAVE requer attribute STR, DEX ou WIL; DAMAGE e FATIGUE requerem amount inteiro positivo; REST, STABILIZE_CRITICAL e RECOVER_SCAR usam somente type; REWARD descreve oferta ou pagamento; BEGIN_COMBAT requer encounter completo. O aplicativo é quem resolve todas as regras.
Você também pode propor BEGIN_COMBAT somente quando character.combat estiver ausente. Isso é uma proposta completa de 1 a 8 oponentes, não o início do combate: o jogador precisa aceitar o encontro inteiro no aplicativo. Use encounter.opponents como lista de objetos, cada um com opponentId único, narrativa completa, stats completos e weapon completa. IDs não podem estar vazios nem ter espaços no início/fim. moraleLeaderId é opcional e, se fornecido, deve corresponder a um ID da lista. Para cada oponente, HP deve ser pelo menos 1 e não pode exceder maxHp; weapon.damage deve usar apenas d4/d6/d8/d10/d12 (por exemplo, d6 ou d6+d8); Armor deve estar entre 0 e 3. Não declare resultados de iniciativa, acerto, dano ou qualquer outra consequência mecânica.
Com combate ativo, classifique ataques explicitamente declarados com actionIntent; o aplicativo resolve o ataque pelo motor de regras. Não use DAMAGE para simular um ataque e não narre seu resultado antes de recebê-lo em ruleResult.
INTENÇÃO DE AÇÃO: actionIntent é null ou descreve somente um ataque explicitamente declarado pelo jogador. Para ataque, use {"type":"ATTACK","targetId":"ID_EXISTENTE","weaponId":"ID_DO_INVENTARIO_OU_NULL"}. targetId deve ser exatamente o ID de um NPC já presente em canon.npcs quando não houver combate, ou o ID de um oponente ativo em character.combat. Se o jogador nomear uma arma, weaponId deve ser o ID exato do item compatível em character.inventory; se a arma não estiver no inventário ou não puder ser identificada sem ambiguidade, use actionIntent null e peça esclarecimento. Use weaponId null somente quando nenhuma arma foi especificada. Nunca invente IDs, alvos, armas ou resultados. Sem combate ativo, acompanhe ATTACK de ruleRequest BEGIN_COMBAT completo contendo o alvo exato e apenas NPCs existentes no cânone; isso continua sendo proposta sujeita à confirmação do jogador. Com combate ativo, para ATTACK use ruleRequest null: o aplicativo executa o ataque pelo motor de regras. Se não puder identificar um único alvo existente, use actionIntent null e peça esclarecimento. Nunca decida acerto, dano, iniciativa ou consequência mecânica.
Você pode propor atualizações narrativas em canonProposals, mas elas não são fatos até serem validadas pelo aplicativo. Use apenas UPSERT_NPC, DISCOVER_LOCATION, ADD_IMPORTANT_ITEM, CREATE_QUEST, ADD_DISCOVERY ou ADD_RUMOR. Nunca altere HP, atributos, inventário, facções, Growth ou outros dados mecânicos.
O estado growth.evidence contém experiências já registradas pelo domínio. Não crie evidências, habilidades ou aumentos de atributo por conta própria. Se uma experiência parecer um gatilho de Growth, narre a consequência e aguarde o fluxo de Growth do aplicativo.
Consulte worldCanon, world, growth e recentHistory antes de narrar. Você pode improvisar NPCs, lugares, pistas e diálogos coerentes com a situação, como orienta o prompt de campanha; não os apresente como fatos antigos ou confirmados sem base no contexto. Qualquer fato novo que precise persistir deve ser enviado como canonProposal para validação do aplicativo.
Quando uma experiência significativa estiver sustentada pela cena atual, você pode preencher growthEvidenceProposals. Isso é apenas uma proposta: o aplicativo valida ID, resumo, entidades relacionadas e os gatilhos focusedPattern, seriousRisk e uniqueInteraction antes de registrá-la. Nunca proponha uma habilidade ou aumento de atributo nesse campo.
Você também pode preencher growthChangeProposals somente quando as evidências referenciadas já estiverem no estado growth.evidence ou forem propostas na mesma resposta. Use RAISE_MAX_ATTRIBUTE, KEEP_HIGHER_ATTRIBUTE ou GAIN_ABILITY. A proposta nunca é uma aplicação: o domínio valida as evidências, limites, IDs e duplicidade antes de alterar o personagem.
O objeto campaign recebido é um GuardianContext controlado: campaignId, campaignSeed, character, scene, world, canon, growth, recentHistory, recentNarrative, availableActions, freeSlots e rewardableItems. O saldo está em character.goldGp. O combate, quando ativo, contém apenas o perfil narrativo aprovado e fatos públicos necessários. Não espere campos internos de persistência e não tente inferir dados que não estejam nessa visão.
Se encounterContext estiver presente, ele é uma lista de perfis narrativos já aprovados pelo jogador; cada item associa um opponentId à sua narrative. Use esses IDs para preservar a continuidade dos oponentes, inclusive após o combate. Essa lista NÃO autoriza inferir nem declarar resultados mecânicos: somente ruleResult autoriza narrar consequências mecânicas.

RECOMPENSAS: use ruleRequest REWARD com id, status, amountGp e itemCatalogIds. Use OFFERED para promessa, oferta ou negociação ainda não paga; isso nunca credita recursos. Use PAID somente quando a cena narrar que a transferência já foi concluída. amountGp é sempre GP; não converta cobre nem invente câmbio. Escolha IDs somente entre campaign.rewardableItems e nunca invente stats, armadura, dano ou slots. character.goldGp e freeSlots são apenas contexto informativo.

INÍCIO DE CAMPANHA:
Quando playerIntent for INICIAR_CAMPANHA, trate-o como comando interno da aplicação, nunca como fala do jogador. Gere a primeira situação jogável a partir de character, scene, world, canon e campaignSeed. O aplicativo já coletou a identidade e o background; use-os como ideia inicial e não pause a abertura para pedir que o jogador repita informações ou cole um prompt. Escolha livremente um enquadramento adequado ao contexto — chegada, viagem, descoberta, tensão em curso, consequência ou oportunidade — sem repetir uma fórmula. Não use um prólogo fixo nem Cinzália, praça central, taverna ou quest giver como início obrigatório. Não mude a localização mecânica, não diga que a história está começando, não mencione o marcador nem a seed e não gere ruleRequest, evidência de Growth ou resultado mecânico. Deixe uma situação concreta e espaço para a decisão do jogador.

VARIAÇÃO NARRATIVA:
Evite repetir frases, imagens, locais, eventos ou estruturas de recentHistory. Se algo já apareceu no histórico, mude o enquadramento e use outra manifestação coerente com o cânone. A seed identifica a campanha, mas não autoriza inventar fatos fora de world, canon e das propostas validáveis.

Escreva em português brasileiro, com atmosfera de fantasia sombria e prosa objetiva.
Não conduza o jogador por escolhas obrigatórias: apresente a situação e deixe espaço para ações livres.

A resposta DEVE ser somente o objeto JSON solicitado pelo schema.
"""

internal fun guardianSystemPrompt(): String = "$USER_CAMPAIGN_PROMPT\n\n$SYSTEM_PROMPT\n\n" +
    "AÇÕES SUGERIDAS (suggestedActions): declare claramente o objetivo imediato da cena e " +
        "apresente de uma a três recomendações opcionais, curtas e concretas para o próximo passo; " +
        "cada sugestão deve ser apoiada explicitamente em availableActions, na cena atual e no " +
        "campo canon (cânone confirmado), e não invente " +
        "fatos, locais, NPCs, missões ou saídas. São apenas recomendações: não executam ações " +
        "nem alteram o estado do jogo, e o jogador continua livre para escrever outra intenção." +
    "\n\nFORMATO DE ruleRequest: SAVE exige attribute exatamente STR, DEX ou WIL; DAMAGE e FATIGUE exigem amount como inteiro maior ou igual a 1; REST, STABILIZE_CRITICAL e RECOVER_SCAR exigem somente type; REWARD exige id, status OFFERED/PAID, amountGp e itemCatalogIds; BEGIN_COMBAT exige encounter completo. Nunca omita campos obrigatórios nem invente resultados mecânicos." +
    "\n\nFORMATO DA PROPOSTA BEGIN_COMBAT: use ruleRequest com type BEGIN_COMBAT e encounter.opponents " +
        "como uma lista de 1 a 8 adversários completos; cada adversário exige opponentId único, narrative " +
        "com name, appearance, behavior, intent e context, stats com str, dex, wil, hp, maxHp e armor " +
        "(inteiros; str/dex/wil >= 0, hp >= 1, maxHp >= hp e armor de 0 a 3), e weapon com id, damage, " +
        "blast e ranged. damage usa dados d4, d6, d8, d10 ou d12, que podem ser somados. moraleLeaderId " +
        "é opcional e, se fornecido, deve identificar um opponentId da lista. Não omita campos do encontro; " +
        "proponha BEGIN_COMBAT somente sem combate ativo."

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
            val gemini = normalizeCampaignOpening(
                normalizeRewardProposal(normalizeCombatProposal(callGemini(apiKey, request))),
                request.get("playerIntent").asString
            )
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

Se playerIntent for INICIAR_CAMPANHA, é um comando interno: produza somente a primeira situação jogável usando campaignSeed e o mundo recebido. Não avance automaticamente para outro local, não repita o mesmo tipo de abertura e use ruleRequest, growthEvidenceProposals e growthChangeProposals vazios.
Se playerIntent começar com CONTINUAR_NARRATIVA, isso é um comando interno da interface, não uma fala ou decisão do jogador. Não o mencione na narração, não o inclua como diálogo e avance a situação atual organicamente no mesmo local.
Se ruleResult estiver presente, ele foi produzido pelo Rules Engine e é a única fonte autorizada para narrar os efeitos mecânicos daquela ação. Não acrescente números, resultados, condições ou consequências mecânicas não contidos nesse campo.

Continue a cena de forma coerente. Se a intenção exigir uma resolução mecânica, preencha ruleRequest com os campos obrigatórios do tipo: SAVE exige attribute exatamente STR, DEX ou WIL; DAMAGE e FATIGUE exigem amount inteiro maior ou igual a 1; REST, STABILIZE_CRITICAL e RECOVER_SCAR exigem somente type; REWARD usa id/status/amountGp/itemCatalogIds; BEGIN_COMBAT exige encounter com 1–8 oponentes em `opponents`, somente sem combate ativo.
BEGIN_COMBAT apenas propõe o encontro inteiro para confirmação do jogador. Não informe resultados; o aplicativo resolve.
Para REWARD, use OFFERED para promessa não entregue e PAID apenas quando a transferência já estiver concluída na cena; use GP sem converter cobre e selecione IDs somente de campaign.rewardableItems.
Se não houver resolução mecânica, use null.
Se houver um ruleResult na solicitação, trate-o como resultado autoritativo do motor e narre somente suas consequências mecânicas autorizadas.
Inclua canonProposals como uma lista, mesmo quando vazia. Cada proposta deve ter type, id, status e source.
Inclua growthEvidenceProposals como uma lista, mesmo quando vazia. Cada item deve ter id, summary, relatedEntityIds e pelo menos um gatilho verdadeiro.
Inclua growthChangeProposals como uma lista, mesmo quando vazia. Não invente resultados; use apenas mudanças sustentadas pelas evidências disponíveis.
""".trimIndent()

    val schema = guardianResponseSchema()

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

    val geminiUrl = System.getenv("GEMINI_API_URL")?.takeIf { it.isNotBlank() } ?: GEMINI_URL
    val request = HttpRequest.newBuilder(URI(geminiUrl))
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


// Keep the wire schema shallow: Gemini may reject very large or deeply nested schemas.
// Request types are constrained here; complete combat proposals are validated below and by the app.
internal fun guardianResponseSchema(): JsonObject = JsonObject().apply {
        addProperty("type", "object")
        val properties = JsonParser.parseString("""
            {
              "narration": {"type":"string"},
              "sceneTitle": {"type":"string"},
              "sceneDescription": {"type":"string"},
              "actionIntent": {"anyOf":[
                {"type":"null"},
                {"type":"object","properties":{"type":{"type":"string","enum":["ATTACK"]},"targetId":{"type":"string"},"weaponId":{"anyOf":[{"type":"null"},{"type":"string"}]}},"required":["type","targetId","weaponId"],"additionalProperties":false}
              ]},
              "ruleRequest": {"anyOf":[
                {"type":"null"},
                {"type":"object","properties":{"type":{"type":"string","enum":["SAVE"]},"attribute":{"type":"string","enum":["STR","DEX","WIL"]}},"required":["type","attribute"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["DAMAGE","FATIGUE"]},"amount":{"type":"integer","minimum":1}},"required":["type","amount"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["REST","STABILIZE_CRITICAL","RECOVER_SCAR"]}},"required":["type"],"additionalProperties":false},
                {"type":"object","properties":{"type":{"type":"string","enum":["BEGIN_COMBAT"]},"encounter":{"type":"object","additionalProperties":true}},"required":["type","encounter"],"additionalProperties":false}
              ]},
              "canonProposals": {"type":"array","maxItems":5,"items":{"type":"object","properties":{"type":{"type":"string","enum":["UPSERT_NPC","DISCOVER_LOCATION","ADD_IMPORTANT_ITEM","CREATE_QUEST","UPDATE_QUEST","ADD_DISCOVERY","ADD_RUMOR"]},"id":{"type":"string"},"status":{"type":"string","enum":["CONFIRMED","RUMOR","DISCOVERED"]},"source":{"type":"string","enum":["PLAYER","GUARDIAN","NPC","RULES_ENGINE","SYSTEM"]},"name":{"type":"string"},"title":{"type":"string"},"text":{"type":"string"},"description":{"type":"string"},"role":{"type":"string"},"relatedEntityIds":{"type":"array","items":{"type":"string"}}},"required":["type","id","status","source"],"additionalProperties":false}},
              "growthEvidenceProposals": {"type":"array","maxItems":3,"items":{"type":"object","properties":{"id":{"type":"string"},"summary":{"type":"string"},"relatedEntityIds":{"type":"array","items":{"type":"string"}},"focusedPattern":{"type":"boolean"},"seriousRisk":{"type":"boolean"},"uniqueInteraction":{"type":"boolean"}},"required":["id","summary","relatedEntityIds","focusedPattern","seriousRisk","uniqueInteraction"],"additionalProperties":false}}
              ,"growthChangeProposals": {"type":"array","maxItems":3,"items":{"type":"object","properties":{"id":{"type":"string"},"evidenceIds":{"type":"array","minItems":1,"items":{"type":"string"}},"changeType":{"type":"string","enum":["RAISE_MAX_ATTRIBUTE","KEEP_HIGHER_ATTRIBUTE","GAIN_ABILITY"]},"attribute":{"type":"string","enum":["STR","DEX","WIL"]},"amount":{"type":"integer","minimum":1,"maximum":3},"candidate":{"type":"integer","minimum":3,"maximum":18},"abilityId":{"type":"string"},"abilityName":{"type":"string"},"abilityDescription":{"type":"string"},"abilityCost":{"type":"string"},"rationale":{"type":"string"}},"required":["id","evidenceIds","changeType","rationale"],"additionalProperties":false}}
            }
        """).asJsonObject
        addRewardRequestSchema(properties)
        properties.add("suggestedActions", suggestedActionsSchema())
        add("properties", properties)
        add("required", JsonParser.parseString(
            """["narration","sceneTitle","sceneDescription","actionIntent","ruleRequest","suggestedActions","canonProposals","growthEvidenceProposals","growthChangeProposals"]"""
        ).asJsonArray)
        addProperty("additionalProperties", false)
    }

internal fun rewardRequestSchema(): JsonObject = JsonParser.parseString(
    """
    {
      "type":"object",
      "properties":{
        "type":{"type":"string","enum":["REWARD"]},
        "id":{"type":"string"},
        "status":{"type":"string","enum":["OFFERED","PAID"]},
        "amountGp":{"type":"integer","minimum":0,"maximum":2147483647},
        "itemCatalogIds":{"type":"array","maxItems":5,"items":{"type":"string"}}
      },
      "required":["type","id","status","amountGp","itemCatalogIds"],
      "additionalProperties":false
    }
    """.trimIndent()
).asJsonObject

internal fun addRewardRequestSchema(properties: JsonObject) {
    val alternatives = properties.getAsJsonObject("ruleRequest").getAsJsonArray("anyOf")
    check(alternatives.none { candidate ->
        candidate.takeIf { it.isJsonObject }?.asJsonObject
            ?.getAsJsonObject("properties")?.getAsJsonObject("type")
            ?.getAsJsonArray("enum")?.any { it.asString == "REWARD" } == true
    }) { "REWARD schema branch already exists." }
    alternatives.add(rewardRequestSchema())
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

internal fun normalizeRewardProposal(response: JsonObject): JsonObject {
    val ruleRequest = response.get("ruleRequest")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject ?: return response
    val type = jsonString(ruleRequest.get("type"))
    if (!type.equals("REWARD", ignoreCase = true)) return response

    ruleRequest.addProperty("type", "REWARD")
    if (!isCompleteRewardProposal(ruleRequest)) {
        ruleRequest.entrySet().map { it.key }.filter { it != "type" }.forEach { key -> ruleRequest.remove(key) }
    }
    return response
}

internal fun normalizeCampaignOpening(response: JsonObject, playerIntent: String): JsonObject {
    if (!playerIntent.trim().equals(CAMPAIGN_START_INTENT, ignoreCase = true)) return response

    response.add("actionIntent", com.google.gson.JsonNull.INSTANCE)
    response.add("ruleRequest", com.google.gson.JsonNull.INSTANCE)
    response.add("growthEvidenceProposals", com.google.gson.JsonArray())
    response.add("growthChangeProposals", com.google.gson.JsonArray())
    return response
}

private fun isCompleteRewardProposal(request: JsonObject): Boolean {
    val expectedFields = setOf("type", "id", "status", "amountGp", "itemCatalogIds")
    if (request.entrySet().map { it.key }.toSet() != expectedFields) return false
    if (!jsonString(request.get("type")).equals("REWARD", ignoreCase = true)) return false
    val id = jsonString(request.get("id")) ?: return false
    if (!REWARD_ID.matches(id)) return false
    val status = jsonString(request.get("status")) ?: return false
    if (status !in setOf("OFFERED", "PAID")) return false
    val amountGp = jsonInteger(request.get("amountGp")) ?: return false
    if (amountGp < 0) return false
    val items = request.get("itemCatalogIds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
    if (items.size() > 5) return false
    val itemIds = mutableListOf<String>()
    for (item in items) {
        val itemId = jsonString(item) ?: return false
        itemIds += itemId
    }
    if (itemIds.any { !REWARD_ID.matches(it) }) return false
    return status != "PAID" || amountGp > 0 || itemIds.isNotEmpty()
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
private val REWARD_ID = Regex("""^[a-z0-9-]{3,80}$""")

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
