# Arquitetura — Cairn Solo RPG

## Princípio central

O projeto separa pensamento narrativo, autoridade mecânica e estado persistido:

```
PLAYER
  ↓
MJ / IA
  • interpreta intenção
  • narra
  • propõe ações
  ↓
GameAction
  ↓
GameActionResolver
  ↓
Rules Engine / domínio
  ↓
GameResult
  ├── GameState
  └── GameEvent
          ↓
    FeedbackMapper
          ↓
    FeedbackEntry
          ↓
         UI
```

**MJ pensa. Engine julga. GameState registra. UI apresenta.**

A IA/MJ pode propor uma ação, mas não pode inventar resultado mecânico nem alterar o estado diretamente.

## Contratos centrais

### GameAction

Representa uma intenção mecânica ou de campanha que pode ser processada pelo domínio.

A UI deve enviar ações ao `GameActionResolver`, em vez de chamar diretamente `RulesEngine`.

### GameResult

```kotlin
data class GameResult(
    val state: GameState,
    val events: List<GameEvent>
)
```

O `state` retornado é o estado autorizado pelo domínio. Os `events` descrevem o que ocorreu para consumo por camadas superiores.

### GameEvent

Eventos comunicam resultados. Eles não executam regras e não devem conter lógica de mutação de estado.

Novos eventos são permitidos quando necessários, desde que tenham finalidade clara, testes e compatibilidade com o fluxo existente.

### FeedbackEntry

```kotlin
data class FeedbackEntry(
    val id: String,
    val message: String,
    val type: FeedbackType,
    val turn: Long
)

enum class FeedbackType {
    INFO,
    SUCCESS,
    WARNING,
    FAILURE,
    DAMAGE,
    CRITICAL,
    INVENTORY
}
```

O `FeedbackMapper` transforma eventos de domínio em apresentação. Ele não decide regras de Cairn.

## Regras de dependência

1. UI não altera `GameState` diretamente.
2. UI não chama `RulesEngine` diretamente.
3. `GameActionResolver` é a entrada central das ações.
4. `RulesEngine` é a autoridade das regras mecânicas.
5. `GameEvent` comunica resultados; não executa regras.
6. `FeedbackMapper` não possui regras de jogo.
7. `GameState` representa o estado autorizado da campanha.
8. Persistência serializa/restaura estado; não decide regras.
9. MJ/IA não altera `GameState` diretamente.
10. Gemini/API não faz parte do núcleo mecânico.
11. Regras normativas devem ser baseadas em Cairn 2e.
12. Comportamentos mecânicos determinísticos devem possuir testes.

## Paralelismo de desenvolvimento

Para reduzir conflitos, o trabalho é dividido por responsabilidade:

### Domínio e ficha

Responsável por:

- `rules/`;
- criação de personagem;
- `CharacterState`;
- inventário;
- testes mecânicos.

Não deve assumir responsabilidades de UI ou narrativa.

### Feedback

Responsável por:

- `feedback/`;
- `ui/feedback/`;
- `FeedbackEntry`;
- `FeedbackMapper`;
- testes de conversão de eventos.

Não altera regras para produzir mensagens.

### Persistência

Responsável por:

- codec;
- repository;
- round-trip;
- compatibilidade de saves;
- versionamento quando necessário.

Não cria uma segunda representação de `GameState`.

### Integração

Responsável por:

- `MainActivity`;
- integração de `GameAction`;
- conexão entre telas e resolver;
- atualização de documentação de status;
- integração final e CI.

A `MainActivity` deve ter um único responsável por alterações durante uma integração para evitar conflitos.

## Evolução dos contratos

`GameAction` e `GameResult` devem permanecer estáveis como contratos internos.

`GameEvent` pode crescer conforme o jogo cresce. Exemplos futuros:

- `EncounterStarted`;
- `AttackResolved`;
- `EnemyDefeated`;
- `CharacterDefeated`;
- `ItemEquipped`;
- `ConditionApplied`.

Cada novo evento deve ser acompanhado por testes e não deve deslocar a autoridade das regras para a UI ou para o MJ.

## Tratamento de falhas

Uma ação inválida do jogador não deve ser confundida com falha de programação.

Exemplos:

- inventário sem espaço;
- item inexistente;
- ação incompatível com o estado atual.

Esses casos devem resultar em uma rejeição controlada ou evento de falha apropriado, permitindo que a UI apresente uma mensagem em PT-BR sem crash.

Exceções de programação continuam sendo erros reais e devem permanecer detectáveis nos testes.

## Persistência e determinismo

IDs e relógio do sistema podem ser refatorados posteriormente para abstrações injetáveis quando isso trouxer benefício real para testes e reprodutibilidade.

Essa melhoria não deve ser usada como justificativa para uma grande refatoração antes do Gate 2.

## O que não entra nesta etapa

- Gemini/API;
- encontros completos;
- combate completo;
- grande refatoração visual;
- conteúdo narrativo infinito.

Esses módulos serão integrados posteriormente sobre os contratos existentes.
