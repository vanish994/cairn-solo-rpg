# Gemini Interactions Schema Complexity — Implementation Plan

> **For agentic workers:** Execute one task at a time, with its tests, validation, and atomic commit before the next task.

**Goal:** Eliminar a falha de produção `Gemini HTTP 400 invalid_request` reduzindo a complexidade do schema enviado à Gemini, preservando a validação semântica de propostas no servidor/app.

**Architecture:** `ruleRequest` será um objeto anulável e raso no wire schema, com enumeração dos tipos permitidos e campos adicionais tolerados. O servidor continuará validando estruturalmente propostas `BEGIN_COMBAT`, e o app/Rules Engine continuará validando e autorizando os efeitos mecânicos; detalhes profundos não serão delegados ao schema Gemini. O stub local ganhará uma guarda de profundidade conservadora para detectar regressões antes do deploy.

**Tech Stack:** Kotlin/JVM 17, Gson, Python 3, Gradle 8.9 e GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-08-playtest-feedback.md` — invariantes de autoridade; diagnóstico confirmado no smoke pós-merge do PR #23 (run `37839495568`).

## Global Constraints

- **Guardian:** narrador. Pode propor cenas, opções, perfis e intenções de oponentes, além de pedidos estruturados; uma ficha/arma em `BEGIN_COMBAT` é apenas um termo proposto para o encontro.
- **Rules Engine / `GameActionResolver`:** única via para alterar HP, atributos, Armor, inventário, ouro, estados de combate ou outras consequências do jogo.
- `BEGIN_COMBAT` permanece uma proposta completa para 1–8 oponentes e nenhum combate começa até o jogador aceitar o encontro inteiro.
- Preservar a separação existente entre narrativa e fatos de regra, os saves antigos e o fluxo de texto livre.
- CI de PR deve testar o Guardian desta branch com stub local; produção continua em workflow manual, separado.

## Review Focus

1. `ruleRequest = null`: deve continuar válido sem campo mecânico inventado.
2. `SAVE`, `DAMAGE`, `FATIGUE`, `REST`, `STABILIZE_CRITICAL` e `RECOVER_SCAR`: seus tipos continuam permitidos; validação semântica segue no app.
3. `BEGIN_COMBAT` válido com 1–8 oponentes: o prompt ainda descreve todos os campos necessários, e o normalizador preserva uma proposta completa apesar do schema raso.
4. `BEGIN_COMBAT` inválido, incompleto ou com stats fracionários: a narração é preservada e a proposta incompleta continua rejeitada.
5. Crescimento do schema além da profundidade conservadora do projeto: o stub deve falhar localmente antes de qualquer requisição Gemini.

---

### Task 1: Especificar o wire contract raso

**Files:**
- Modify: `guardian-server/src/test/kotlin/com/vanish994/cairnsolo/guardian/ServerTest.kt`
- Modify: `guardian-server/scripts/test_mock_gemini.py`

**Interfaces:**
- `guardianResponseSchema()` expõe `ruleRequest` como `type: ["object", "null"]`, exige `type` somente para o ramo objeto, enumera os sete tipos de pedido aceitos e define `additionalProperties: true`.
- `combatEncounterSchema()` continua descrevendo o contrato multi-oponente para testes; não será incorporado no schema enviado à Gemini.

- [x] Substituir a asserção de ramo Gemini profundo por `guardianResponseSchemaKeepsRuleRequestShallowAndNullable`, verificando enum, anulabilidade, `additionalProperties` e ausência de schema aninhado para `encounter`.
- [x] Rodar a suíte de testes do servidor para confirmar a falha RED da nova expectativa.
- [x] Alterar o teste Python do stub para rejeitar schema wire acima de profundidade máxima 5 (política conservadora do projeto; não é apresentada como limite oficial da Gemini) e para aceitar o novo contrato raso.
- [x] Rodar `python3 guardian-server/scripts/test_mock_gemini.py` e confirmar a falha RED do contrato atualizado.

### Task 2: Simplificar o schema enviado sem relaxar a autoridade

**Files:**
- Modify: `guardian-server/src/main/kotlin/com/vanish994/cairnsolo/guardian/Server.kt`
- Modify: `guardian-server/src/test/kotlin/com/vanish994/cairnsolo/guardian/ServerTest.kt`
- Modify: `guardian-server/scripts/mock_gemini.py`

- [x] Fazer `guardianResponseSchema()` enviar para `ruleRequest` somente um objeto anulável raso com `type` no enum `SAVE`, `DAMAGE`, `FATIGUE`, `REST`, `STABILIZE_CRITICAL`, `RECOVER_SCAR`, `BEGIN_COMBAT` e `additionalProperties: true`; remover o acoplamento de `replaceBeginCombatSchema()` ao wire schema.
- [x] Descrever em `guardianSystemPrompt()` todos os campos de `BEGIN_COMBAT`, seus limites mecânicos e a opcionalidade de `moraleLeaderId`; testar os nomes/limites com `promptDefinesCompleteCombatProposalFieldsForShallowWireSchema`.
- [x] Manter `combatEncounterSchema()` e `isCompleteEncounterProposal()` como contrato/validação local; propostas inválidas continuam perdendo apenas `encounter`, nunca a narração.
- [x] Atualizar o stub para validar o enum de `ruleRequest`, a profundidade conservadora do schema final, o envelope HTTP e as keywords documentadas.
- [x] Rodar `:guardian-server:test`, `python3 guardian-server/scripts/test_mock_gemini.py` e o smoke HTTP local via `guardian-server/scripts/local-integration-test.sh`; todos devem passar sem qualquer acesso à produção.

### Task 3: Validar em CI e abrir PR

**Files:**
- Revisar: `.github/workflows/android.yml` e `.github/workflows/guardian-production-smoke.yml`; manter os papéis já separados.

- [ ] Verificar `git diff --check`, scripts e workflows; garantir que o workflow automático não referencia o endpoint Render.
- [ ] Publicar branch curta baseada em `main` e abrir PR; aguardar o Android Build com testes server, stub HTTP local e APK.
- [ ] Não mesclar nem executar outro POST de produção como parte deste plano. Depois do CI verde, solicitar autorização específica para merge/deploy e novo smoke manual, pois o smoke atual já confirmou o erro em produção.
