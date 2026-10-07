# Status do Projeto

## Projeto

**Cairn Solo RPG**

## Gate atual

**Integração de campanha, Guardian e UI de chat**

Status: 🟡 núcleo de regras e módulos de campanha integrados; UI em transição para chat; combate/Growth precisam fechar o ciclo E2E

## Gate 0 — Concluído

- Projeto Android nativo em Kotlin.
- Jetpack Compose e Material 3.
- Módulo inicial `:app`.
- Manifesto e Activity de entrada.
- Tela inicial mínima do Cairn Solo RPG.
- Configuração de build debug.
- GitHub Actions validando `testDebugUnitTest` e `assembleDebug`.
- Build debug confirmado com sucesso no GitHub Actions.
- Artefato `cairn-solo-rpg-debug` configurado para upload.
- Correção aplicada para alinhar Java/Kotlin em JVM 17.

## Base normativa definida

**Cairn Second Edition (2ª Edição)** será a referência normativa do Rules Engine.

Documentação oficial consultada:
- Player's Guide da 2e.
- Warden's Guide da 2e.
- Materiais oficiais da 2e.

A Cairn Barebones Edition poderá ser usada posteriormente como referência de apresentação genérica, mas não será uma fonte normativa separada: suas regras e procedimentos correspondem à 2e.

Decisão registrada em:
`docs/decisions/ADR-006-cairn-2e.md`

## Arquitetura

O núcleo deverá ser independente de Android e Gemini.

```text
GameAction
    ↓
GameActionResolver
    ↓
Rules Engine / domínio
    ↓
GameResult
    ├── state
    └── events
```

O RNG será isolável para testes. O GameState só poderá mudar por operações válidas do domínio.

## Gate 1 — Motor de regras

Implementado como base determinística:

- atributos;
- saves;
- HP;
- dano;
- Armor;
- inventário/slots;
- Fatigue/Deprivation;
- Critical Damage;
- Scars;
- recuperação;
- persistência do estado;
- RNG injetável.

A criação de personagem possui a base de rolagens 3d6/1d6, Backgrounds, Traits e Age, mas ainda será expandida e validada como fluxo completo.

Pendências do Gate 1:
- persistência completa de efeitos individuais de Cicatrizes;
- validação normativa detalhada de todas as recuperações;
- catálogo de equipamento/backgrounds;
- regras avançadas de combate e magia.

## Gate 2 — exploração inicial

- [x] Tipos de cena.
- [x] Estado de cena persistente.
- [x] Diário de campanha persistente.
- [x] Motor determinístico de exploração.
- [x] Ação CONTINUE com RNG injetável.
- [x] Ação INVESTIGATE com RNG injetável.
- [x] Testes unitários do motor de exploração.
- [x] Tela própria de exploração.
- [x] `GameAction` independente da UI.
- [x] `GameActionResolver` como entrada central da campanha.
- [x] `GameResult` com estado + eventos.
- [x] Eventos de exploração traduzidos para contrato estável.
- [x] Descanso da exploração resolvido pelo `RulesEngine`, em vez de apenas emitir um pedido.
- [x] Testes do descanso através do contrato de ações.
- [x] CI executado com sucesso após a correção do teste de CharacterGenerator.

### Decisão arquitetural atual

A tela de exploração não modifica regras diretamente. Ela envia `GameAction`; o `GameActionResolver` decide qual operação de domínio executar; o resultado persistido é o estado autorizado pelo domínio.

O descanso de exploração agora usa `RulesEngine.safeRest()`. Isso evita criar uma segunda implementação de descanso dentro do motor de exploração.

## Estado atual da integração

- [x] `GameActionResolver` como porta central para regras de personagem, inventário, combate, Growth, facções e módulos Warden.
- [x] Mundo inicial gerado por `campaignSeed` e abertura narrativa derivada da campanha.
- [x] `GuardianContext` filtrado para impedir exposição do estado bruto ao modelo.
- [x] Histórico persistente de narrativa e falas do jogador na tela de exploração.
- [x] Entrada principal em formato de chat, com envio compacto ao lado do campo.
- [x] Botões narrativos redundantes `Continuar` e `Investigar` removidos da tela principal.
- [x] Descanso conectado ao `RulesEngine.safeRest()` e explicado como descanso seguro na UI.
- [x] Combate, magia, dungeon, wilderness, downtime, marketplace, reações, moral, hirelings, Growth e cânone possuem módulos de domínio e ações de integração.
- [x] Persistência inclui os módulos novos e possui testes de round-trip em partes relevantes.
- [x] Backend Guardian usa fluxo estruturado de propostas e modelo Flash-Lite.

## Próximo incremento

- conectar `BEGIN_COMBAT`/`BeginCombat` ao fluxo do Guardian e à UI de combate;
- bloquear ações de exploração enquanto `campaign.combat` estiver ativo;
- criar revisão aceitar/recusar para Growth validado;
- diferenciar descanso seguro, descanso breve e acampamento inseguro por contexto de cena;
- concluir o teste E2E: criação → abertura única → exploração → encontro → combate → Growth → save/load;
- sincronizar a matriz normativa com o código real e atualizar os Gates antigos;
- decidir e registrar a consolidação entre `CampaignState` e `CampaignRuntime`.

## Regras para agentes

- Não implementar o jogo inteiro de uma vez.
- Não adicionar a Gemini API antes do Gate 4.
- Não transformar a IA em autoridade das regras.
- Não adicionar dependências sem justificativa.
- Não quebrar uma etapa já validada sem registrar a mudança.
- Não misturar regras da 1e com a 2e sem decisão explícita.
- Não inventar regras ausentes na fonte normativa.
- Preferir implementação mecânica a texto hardcoded da fonte.
- Toda alteração relevante deve ter testes automatizados quando houver comportamento determinístico verificável.
- Uma etapa só é considerada validada após execução real do CI.

## Última validação

Workflow GitHub Actions **Android Build #68**:
- `testDebugUnitTest`: sucesso.
- 26 testes concluídos.
- 0 falhas.
- Build do projeto: sucesso.

## Documentos para continuidade

- `docs/DEVELOPMENT-GUIDE.md` — procedimento completo para novos agentes e mudanças.
- `docs/ARCHITECTURE-MAP.md` — mapa técnico detalhado dos fluxos e dependências.
