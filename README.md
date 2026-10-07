# Cairn Solo RPG

RPG solo de fantasia para Android, baseado nas regras de Cairn, com um GM narrativo apoiado por IA.

## Objetivo

Criar um APK Android jogável em que:

- o jogador controla um único personagem;
- o motor determinístico controla regras e estado do jogo;
- o Gemini Flash-Lite atua como GM narrativo;
- a campanha pode ser salva e retomada;
- o projeto pode ser desenvolvido por diferentes agentes sem depender de um único ambiente.

## Princípios

1. **GameState é a fonte da verdade.**
2. **O Rules Engine é a autoridade mecânica.**
3. **A IA não altera regras diretamente.**
4. **A UI não altera o estado diretamente.**
5. **O projeto deve permanecer compilável ao final de cada Gate.**
6. **Nenhuma chave de API deve entrar no Git.**
7. **O projeto deve continuar utilizável mesmo sem a IA, através de MockAIProvider.**
8. **Preferir soluções simples e adequadas ao desenvolvimento pelo Android.**
9. **Ações mecânicas passam pelo Rules Engine e produzem resultados/eventos verificáveis.**
10. **A edição/fonte normativa de Cairn deve ser registrada antes da implementação das regras específicas.**

## Stack planejada

- Android
- Kotlin
- Jetpack Compose
- Material 3
- Gradle Kotlin DSL
- Persistência local
- Kotlin Serialization
- Gemini Flash-Lite para narrativa
- Git + GitHub

## Direção artística

A apresentação oficial do jogo seguirá **pixel art de fantasia sombria**, conforme ADR-007. A direção visual é independente do motor de regras.

## Arquitetura inicial

```text
app/
└── src/main/
    ├── kotlin/.../
    │   ├── game/
    │   ├── rules/
    │   ├── character/
    │   ├── campaign/
    │   ├── data/
    │   ├── ai/
    │   └── ui/
    └── AndroidManifest.xml
```

A estrutura pode evoluir conforme o projeto crescer. Não criar abstrações ou módulos apenas por antecipação.

## Fluxo do jogo

```text
Jogador
   ↓
UI / Intent
   ↓
GameAction
   ↓
Rules Engine
   ↓
GameResult
   ├── newState
   └── events
   ↓
Context Builder
   ↓
Gemini / MockAI
   ↓
Narrativa
   ↓
UI
```

O Gemini nunca é autoridade sobre HP, dano, inventário, rolagens ou outras mudanças mecânicas.

## Desenvolvimento por Gates

| Gate | Objetivo | Estado |
|---|---|---|
| 0 | Arquitetura Android e repositório | ✅ |
| 1 | Motor de regras Cairn | 🟡 |
| 2 | Criação de personagem | ⚪ |
| 3 | Campanha e GameState | ⚪ |
| 4 | Integração Gemini | ⚪ |
| 5 | GM narrativo | ⚪ |
| 6 | Combate | ⚪ |
| 7 | Save/Load | ⚪ |
| 8 | APK jogável/final | ⚪ |

Detalhes e critérios estão em `docs/gates/`.

## Como trabalhar como agente

Antes de alterar código:

1. Leia este README.
2. Leia `docs/STATUS.md`.
3. Leia o Gate atual em `docs/gates/`.
4. Preserve decisões registradas em `docs/decisions/`.
5. Não avance Gates sem cumprir os critérios de conclusão.
6. Faça alterações pequenas e verificáveis.
7. Atualize documentação quando uma decisão arquitetural mudar.
8. Inspecione o código existente antes de criar novas estruturas.

### Documentação de continuidade

- `docs/DEVELOPMENT-GUIDE.md`: guia operacional para continuar o desenvolvimento, validar mudanças e manter a fronteira entre IA e regras.
- `docs/ARCHITECTURE-MAP.md`: mapa detalhado de camadas, módulos, contratos, estado, persistência e fluxos de combate/Growth/cânone.
- `docs/STATUS.md`: fotografia do estado atual e pendências conhecidas.
- `docs/CAIRN-2E-RULE-MATRIX.md`: cobertura normativa e lacunas do Cairn 2e.

## Referências arquiteturais

O projeto usa ideias de referência, não cópia de implementação:

- `kylep/claude-ttrpg`: separação forte entre GM/narrativa e engine determinística.
- `yochaigal/cairn`: fonte oficial para consulta das regras de Cairn; a edição adotada pelo projeto deve ser registrada.
- Projetos Android Kotlin/Compose com Clean Architecture: separação entre domínio, dados e apresentação.
- Projetos de jogos com state machine/MVI: fluxo unidirecional e transições explícitas quando isso simplificar testes e depuração.

## Segurança

Nunca faça commit de:

- API keys;
- tokens;
- senhas;
- arquivos `.env` reais;
- credenciais;
- dados pessoais.

Use `.env.example` ou configuração equivalente apenas com nomes de variáveis.

## Estado atual

**Gate 0 concluído. Gate 1 pronto para iniciar.**

O build debug foi validado no GitHub Actions e o artefato foi gerado. O próximo passo é fixar a fonte/edição normativa de Cairn e implementar o núcleo determinístico das regras com testes.
