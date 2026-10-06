# Status do Projeto

## Projeto

**Cairn Solo RPG**

## Gate atual

**Gate 1 — Motor de Regras Cairn**

Status: 🟡 Rules Engine base implementado; GameState, criação de personagem e persistência local implementados

## Gate 0 — Concluído

- Projeto Android nativo em Kotlin.
- Jetpack Compose e Material 3.
- Módulo inicial `:app`.
- Manifesto e Activity de entrada.
- Tela inicial mínima do Cairn Solo RPG.
- Configuração de build debug.
- GitHub Actions validando `assembleDebug`.
- Build debug confirmado com sucesso no GitHub Actions.
- Artefato `cairn-solo-rpg-debug` gerado e disponível.
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

## Gate 1 — Implementação

O núcleo deverá ser independente de Android e Gemini.

Arquitetura:

```text
GameAction
    ↓
Rules Engine
    ↓
GameResult
    ├── newState
    └── events
```

O RNG será isolável para testes. O GameState só poderá mudar por operações válidas do domínio.

Primeiro escopo:

- atributos;
- saves;
- HP;
- dano;
- Armor;
- inventário/slots;
- Fatigue/Deprivation;
- condições;
- Critical Damage;
- Scars;
- recuperação;
- regras essenciais de combate e magia.

Cada regra implementada deverá ter referência à seção normativa correspondente e testes automatizados.

## Próximo passo

Completar a persistência com testes de round-trip e avançar para inventário/equipamentos de personagem e fluxo de campanha.

## Regras para agentes

- Não implementar o jogo inteiro de uma vez.
- Não adicionar a Gemini API antes do Gate 4.
- Não transformar a IA em autoridade das regras.
- Não adicionar dependências sem justificativa.
- Não quebrar uma etapa já validada sem registrar a mudança.
- Não misturar regras da 1e com a 2e sem decisão explícita.
- Não inventar regras ausentes na fonte normativa.
- Preferir implementação mecânica a texto hardcoded da fonte.

## Última atualização

Base normativa fixada em Cairn 2e e registrada no ADR-006. Gate 1 pronto para implementação.

## Incremento atual — GameState

- [x] GameState separado do Rules Engine.
- [x] CampaignState e identidade do personagem.
- [x] Criação inicial de personagem.
- [x] Persistência local com SharedPreferences.
- [x] Restauração da campanha ao abrir o aplicativo.
- [x] Operações de dano/descanso atualizando e salvando o GameState.
- [x] Testes básicos do GameState.
