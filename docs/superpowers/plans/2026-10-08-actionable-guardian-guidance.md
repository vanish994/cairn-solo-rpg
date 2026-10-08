# Orientação Narrativa e Ações Sugeridas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada resposta narrativa deve deixar claro o objetivo imediato e oferecer ações concretas visíveis, sem limitar a liberdade de escrever outra intenção.

**Architecture:** O backend instrui o Guardian a resumir o próximo objetivo e propor de 1 a 3 ações apoiadas pelo contexto conhecido. O app já recebe `suggestedActions`; a Activity passará a mostrá-las como chips que preenchem o campo de intenção, sem enviar a ação automaticamente.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Gson/JSON Schema e testes Kotlin/JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-08-playtest-feedback.md` — seção “Orientação e ações sugeridas”; contratos atuais em `guardian-server/src/main/kotlin/com/vanish994/cairnsolo/guardian/Server.kt` e `app/src/main/kotlin/com/vanish994/cairnsolo/guardian/GuardianClient.kt`.

## Global Constraints

- Narrativa em português brasileiro; o Rules Engine permanece a autoridade de toda consequência mecânica.
- Não inventar NPCs, locais, missões ou saídas que não estejam no contexto/cânone.
- Manter ações livres: sugestões são opcionais e o campo de texto continua disponível.
- No máximo três sugestões; tocar numa sugestão apenas preenche o campo, não envia nem executa a ação.
- Preservar o fluxo de resposta comum e o fluxo de narração após resolução de regra.
- Primeiro revisar e mesclar o PR de documentação destes planos; depois criar o branch de feature a partir da `main` atualizada para que spec e plano acompanhem o trabalho.
- Entregar via branch/PR; exigir Android Build verde antes de merge em `main`.

## Review Focus

1. **Nenhuma rota conhecida:** o Guardian deve indicar o que já se sabe e oferecer perguntas/ações locais, sem inventar uma saída.
2. **Resposta sem sugestões:** schema e cliente devem rejeitar/normalizar lista vazia ou entradas em branco sem perder a narração.
3. **Sugestões longas ou numerosas:** limitar cada texto a 160 caracteres e a lista a três entradas.
4. **Clique acidental envia a ação:** o chip apenas preenche o campo; envio continua sendo escolha explícita do jogador.
5. **Resposta após combate:** atualizar sugestões também após `CONTINUAR_NARRATIVA`, não apenas após intenção normal.

---

### Task 1: Contrato de orientação e lista de ações

**Files:**
- Modify: `guardian-server/src/main/kotlin/com/vanish994/cairnsolo/guardian/Server.kt`
- Test: `guardian-server/src/test/kotlin/com/vanish994/cairnsolo/guardian/ServerTest.kt`
- Modify/Test: `app/src/main/kotlin/com/vanish994/cairnsolo/guardian/GuardianClient.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianClientTest.kt`

**Interfaces:**
- Produce `internal fun suggestedActionsSchema(): JsonObject` com `type=array`, `minItems=1`, `maxItems=3`, e strings `minLength=1`, `maxLength=160`.
- Produce `internal fun guardianSystemPrompt(): String` para compor a instrução usada pelo request e validar o requisito de grounding.
- `GuardianResponse.suggestedActions` continua `List<String>`; o parser ignora entradas não string, em branco e acima de 160 caracteres, limita o máximo a três e preserva narração/cena mesmo se a lista estiver malformada.
- A instrução específica de sugestões em `guardianSystemPrompt()` usa o cabeçalho estável `AÇÕES SUGERIDAS (suggestedActions):`; o parágrafo desse cabeçalho pede um objetivo imediato, vincula as sugestões a `availableActions`, `scene` e ao campo real `canon`, e proíbe inventar fatos.
- Campo `suggestedActions` ausente, `null`, vazio, não-array ou com membros inválidos vira lista vazia sem descartar narração/cena.

- [ ] Escrever `ServerTest.suggestedActionsSchemaRequiresOneToThreeBoundedStrings`, `ServerTest.promptGroundsActionsInSceneCanonAndAvailableActions`, `GuardianClientTest.parseSuggestedActionsFiltersBlankExcessAndNonStringEntries`, `GuardianClientTest.parseSuggestedActionsEnforces160CharacterBoundary`, `GuardianClientTest.emptySuggestedActionsDoNotDropNarration`, `GuardianClientTest.malformedSuggestionsDoNotDropNarration`, `GuardianClientTest.missingSuggestedActionsDoNotDropNarrationOrScene` e `GuardianClientTest.nullSuggestedActionsDoNotDropNarrationOrScene`; afirmar limite, grounding dentro do bloco da diretiva e preservação da narração/cena.

```kotlin
assertEquals(1, suggestedActionsSchema().get("minItems").asInt)
assertEquals(3, suggestedActionsSchema().get("maxItems").asInt)
assertEquals(160, suggestedActionsSchema().get("items").asJsonObject.get("maxLength").asInt)
assertEquals("A narração permanece.", parsed.narration) // inclusive com lista vazia/entrada não string
assertTrue(parsed.suggestedActions.size <= 3 && parsed.suggestedActions.all { it.isNotBlank() && it.length <= 160 })
```

- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>`; confirmar na Android Build a falha esperada dos novos testes antes da implementação. O Sandbox não possui Gradle/Android SDK local.
- [ ] Extrair a propriedade de schema para `suggestedActionsSchema()` e conectá-la ao schema de resposta; instruir o Guardian a apresentar um objetivo próximo e 1–3 passos plausíveis derivados de `availableActions`, cena e cânone.
- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` após a implementação; confirmar Android Build verde e que resposta inválida de sugestões não descarta a narração.
- [ ] Commit atômico: `fix: make Guardian next steps actionable`.

### Task 2: Exibir e selecionar ações sugeridas

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/MainActivity.kt`
- Test: `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianClientTest.kt` para o contrato consumido pela Activity; validação visual no APK de CI.

**Interfaces:**
- `ExplorationScreen` recebe `suggestedActions: List<String>` e `onSuggestionSelected: (String) -> Unit`.
- Selecionar um chip preenche o `intent` local do composable; não chama `onGuardianIntent`.

- [ ] Confirmar que o contrato de parser preserva exatamente as ações não vazias selecionadas no teste da Task 1.
- [ ] Em `MainActivity`, armazenar as sugestões da última resposta e atualizá-las nos dois callbacks Guardian: intenção do jogador e continuação pós-regra.
- [ ] Em `ExplorationScreen`, exibir de 1 a 3 chips abaixo de `GuardianCard`; tocar num chip preenche o campo de texto, que pode ser editado antes do envio.
- [ ] Limpar sugestões ao iniciar campanha nova ou enviar uma intenção; manter o texto narrativo salvo mesmo após reinício (chips são affordances transitórias, não novo dado persistido).
- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` e conferir manualmente no APK de CI que os chips não enviam automaticamente e que texto livre continua funcionando.

```kotlin
assertEquals(suggestion, intentFieldText) // após tocar no chip
assertFalse(guardianIntentWasSent)        // enviar continua sendo ação explícita
```
- [ ] Commit atômico: `feat: show Guardian suggested actions`.

### Task 3: Integração e aceitação

**Files:**
- Verificar `.github/workflows/android.yml`.
- Atualizar `docs/STATUS.md` e `docs/ARCHITECTURE-MAP.md` somente após CI e verificação manual.

**Branch:** `feature/actionable-guardian-guidance` (criar a partir da `main` após mesclar o PR de documentação; um PR focado).

- [ ] Abrir PR para `main` com um exemplo de resposta que indique objetivo imediato e ações sugeridas fundamentadas.
- [ ] Exigir Android Build verde, incluindo unit tests, testes do Guardian server, integração HTTP e APK release.
- [ ] Aceitação manual: ao chegar à taverna e após continuação pós-combate, o jogador vê próximos passos concretos; tocar uma sugestão apenas a coloca no campo; digitar ação própria continua possível.
- [ ] Atualizar status sem fechar gates não relacionados; merge somente após aprovação verde no HEAD atual do PR.
