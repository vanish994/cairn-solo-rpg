# Abertura de Campanha pelo Guardião — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer cada campanha começar com uma abertura inédita narrada pelo Guardião, sem exigir que o jogador cole um prompt e sem fixar o início em Cinzália.

**Architecture:** O texto do jogador será incorporado às instruções internas do backend, sem aparecer como mensagem de chat. Ao criar a campanha, o app enviará uma intenção interna `INICIAR_CAMPANHA` com o GuardianContext e a seed; salvará narração, interação e sugestões retornadas. A seed também selecionará o assentamento inicial entre opções variadas; se a chamada falhar, a campanha permanece jogável com a abertura local de contingência.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose, Gemini Interactions API com JSON schema, Gradle e testes locais com stub Gemini.

**Spec:** Prompt fornecido pelo usuário em `/home/ubuntu/upload/pasted_content.txt` e requisito confirmado na conversa em 2026-10-09: instruções permanentes do Guardião, campanha iniciada automaticamente por ele, inícios variados e manutenção de **Próximas Ações**.

## Global Constraints

- Rules Engine continua como autoridade exclusiva para testes, iniciativa, dano, HP, inventário, condições, combate, recompensas e demais efeitos mecânicos.
- A abertura inicial não executa nem credita solicitações mecânicas; fatos narrativos novos continuam sujeitos a `canonProposals` e validação do aplicativo.
- O prompt de sistema é interno; a interface mostra a narração e as sugestões, não o texto integral das instruções.
- `suggestedActions`/**Próximas Ações** e seu card atual não serão removidos nem rebaixados; a abertura também deve preencher as sugestões normalmente.
- Respostas continuam em português brasileiro, concisas para celular, sem trilho obrigatório ou prólogo fixo.
- Não tocar em `release-download/`; CI usa stub local e não depende de produção.

## Review Focus

- Mesmo `campaignSeed` e sequência aleatória produzem o mesmo mundo; seeds distintas conseguem produzir pontos de partida distintos. Testar no módulo `app`.
- A intenção interna `INICIAR_CAMPANHA` não aparece como fala do jogador nem como narração. Testar payload do `GuardianClient` e contrato do backend.
- Se o modelo devolver uma solicitação mecânica no início, ela não pode ser resolvida/creditada automaticamente. Testar normalização no `ServerTest`.
- Se a chamada inicial falhar, salvar e manter a campanha local jogável e exibir o erro existente; não perder personagem nem repetir uma transferência. Cobrir o tratamento da resposta/falha no ponto mais testável da UI/cliente.
- A resposta inicial deve manter de uma a três sugestões e elas precisam chegar ao estado que alimenta o card **Próximas Ações**. Testar payload/parse/integração local e validar a UI no APK.

---

### Task 1: Variar o ponto de partida usando a seed

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/game/WorldGenerator.kt`
- Modify: `app/src/test/kotlin/com/vanish994/cairnsolo/game/WorldGeneratorTest.kt`
- Test: `app/src/test/kotlin/com/vanish994/cairnsolo/game/WorldIntegrationTest.kt`

**Interfaces:** Mantém `WorldSeed.narrativeSeed`, `WorldState.currentLocationId` e a estrutura atual de assentamentos. O nome inicial será escolhido de forma determinística pela seed, com uma lista ampliada de nomes; não muda regras, localização mecânica nem o ID inicial.

- [x] **Step 1: Escrever testes de regressão** `generationIsDeterministicForSameSeedAndRandomSequence` e `differentNarrativeSeedsCanSelectDifferentStartingSettlements`; verificar que a abertura usa o nome do assentamento efetivamente gerado.
- [x] **Step 2: Executar os testes e confirmar que o cenário atual falha** porque o primeiro assentamento sempre recebe `names[0]` (Cinzália).
- [x] **Step 3: Implementar seleção estável de nome baseada em `WorldSeed.narrativeSeed`**, ampliando as opções sem mudar a estrutura do mundo.
- [ ] **Step 4: Executar os testes do módulo `app`** e confirmar determinismo para a mesma seed e variedade entre as seeds de teste.
- [ ] **Step 5: Commitar** `feat: vary seeded campaign starting settlements`.

### Task 2: Tornar o prompt uma instrução interna e definir o contrato de abertura

**Files:**
- Create: `guardian-server/src/main/resources/guardian-system-prompt.txt` com o prompt integral fornecido pelo jogador.
- Modify: `guardian-server/src/main/kotlin/com/vanish994/cairnsolo/guardian/Server.kt`
- Modify: `guardian-server/src/test/kotlin/com/vanish994/cairnsolo/guardian/ServerTest.kt`
- Modify: `guardian-server/scripts/mock_gemini.py` e testes/scripts de integração somente se necessário para exercitar o novo marcador.

**Interfaces:** Define o marcador interno `INICIAR_CAMPANHA`; o backend recebe o marcador via `playerIntent`, trata-o como controle da aplicação (nunca fala do jogador) e usa `character`, `scene`, `world`, `campaignSeed`, `recentHistory` e cânone para criar uma abertura variada. O schema Gemini permanece no subset já validado.

- [x] **Step 1: Escrever testes** para verificar que o resource completo (13 seções) é carregado, preservar autoridade mecânica e variedade, e normalizar a abertura inicial para `ruleRequest: null` e Growth vazio sem perder sugestões.
- [x] **Step 2: Executar `gradle :guardian-server:test` e confirmar as regressões.**
- [x] **Step 3: Carregar o resource integral e combinar o contrato técnico** para `INICIAR_CAMPANHA`: não repetir Cinzália/taverna/quest giver como fórmula; variar enquadramento conforme seed e contexto; não pedir que o jogador repita o prompt; não avançar de local nem resolver mecânica na primeira resposta.
- [x] **Step 4: Validar backend e integração local** com stub, mantendo `suggestedActions` no contrato e sem chamar Render.
- [x] **Step 5: Commitar** `feat: add internal campaign-opening guidance`.

### Task 3: Solicitar a abertura ao Guardião ao criar campanha

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/MainActivity.kt`
- Create: `app/src/main/kotlin/com/vanish994/cairnsolo/guardian/CampaignOpening.kt`
- Modify: `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianClientTest.kt`
- Create: `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/CampaignOpeningTest.kt`
- Modify: `docs/DEVELOPMENT-GUIDE.md` para documentar o fluxo automático e sua contingência.

**Interfaces:** Depois de `GameAction.CreateCharacter`, enviar `guardianClient.narrate(createdState com guardianMessage vazio, playerIntent = "INICIAR_CAMPANHA")` em coroutine. `resolveCampaignOpening(initialState, result, actionResolver)` retorna estado, sugestões e erro; aplica narração/cânone validado em sucesso, nunca resolve ruleRequest/Growth/reward no início, e preserva o estado inicial jogável em falha. Atribuir as sugestões ao mesmo estado Compose que alimenta o card existente. Não exibir o marcador ou o prompt interno.

- [x] **Step 1: Escrever teste do payload inicial** confirmando `INICIAR_CAMPANHA`, `campaignSeed` e ausência de ID de interação herdado de outra campanha.
- [ ] **Step 2: Executar esse teste e confirmar que o fluxo atual não dispara uma abertura automática.**
- [x] **Step 3: Implementar a chamada no fluxo de criação**, usando o loading/erro existentes; em sucesso salvar também `suggestedActions`, sem remover ou alterar o card; em falha conservar estado e fallback locais.
- [ ] **Step 4: Rodar testes Android, testes Guardian e integração local; montar APK release** conforme `.github/workflows/android.yml` (`gradle testDebugUnitTest`, `gradle :guardian-server:test`, `bash guardian-server/scripts/local-integration-test.sh`, `gradle :app:assembleRelease`).
- [ ] **Step 5: Revisar diff para confirmar** que o prompt é interno, o início não executa regra, Próximas Ações continua no fluxo e `release-download/` ficou intocado; commitar a integração.

### Task 4: PR e validação

- [ ] Abrir PR para `main` com os três commits e critérios acima.
- [ ] Aguardar CI verde (Android, Guardian e stub Gemini); corrigir qualquer regressão antes do merge.
- [ ] Após deploy confirmado, executar somente o workflow manual de smoke de produção. Não acoplar produção ao CI.
