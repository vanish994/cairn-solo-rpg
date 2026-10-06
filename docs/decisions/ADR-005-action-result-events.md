# ADR-005 — Ações, resultados e eventos determinísticos

## Decisão

As operações do jogo serão modeladas como ações de domínio que passam pelo Rules Engine e produzem um resultado determinístico.

Fluxo:

```text
UI / futura IA
    ↓
GameAction
    ↓
Rules Engine
    ↓
GameResult
    ├── newState
    └── events
```

O Rules Engine continua sendo a única autoridade capaz de produzir mudanças mecânicas válidas no estado.

## Motivo

Referências de arquitetura para RPGs orientados por IA mostram valor em separar narrativa de adjudicação determinística. Em um aplicativo Android, essa separação também facilita testes, histórico, depuração e evolução da UI.

## Regras

- A UI envia intenções convertidas em ações de domínio.
- A IA pode sugerir ou interpretar ações, mas não pode aplicar mutações diretamente.
- O Rules Engine recebe o estado atual, a ação e uma fonte de aleatoriedade controlável.
- O resultado informa o novo estado e os eventos mecânicos relevantes.
- Eventos não substituem o GameState; servem para histórico, narrativa e efeitos de apresentação.
- Testes podem usar RNG determinístico/fake.
- Nenhuma dependência de Android, Compose ou Gemini entra no núcleo de regras.

## Consequência

O Gate 1 deverá priorizar um núcleo de domínio puro e testável. A implementação pode começar simples e crescer apenas quando uma regra exigir.
