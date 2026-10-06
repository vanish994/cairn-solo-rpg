# Status do Projeto

## Projeto

**Cairn Solo RPG**

## Gate atual

**Gate 1 — Motor de Regras Cairn**

Status: 🔵 Gate 0 concluído; Gate 1 pronto para iniciar

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

Workflow validado:
- Run: `37454905482`
- Check: `112240012489`
- Commit validado: `9cd506ef2a15c9c6744fa4bacc6d6da55bbdb777`
- Resultado: **success**
- Artefato: `cairn-solo-rpg-debug`

## Gate 1 — Próximo

Objetivo: implementar o núcleo determinístico das regras Cairn sem dependência de Android ou Gemini.

Antes de codificar regras específicas, deve ser fixada a fonte/edição de Cairn usada pelo projeto. O repositório oficial informa que existem múltiplas edições compatíveis e que o texto está sob CC-BY-SA 4.0.

A arquitetura adotada para o motor será baseada em:

```text
GameAction
    ↓
Rules Engine
    ↓
GameResult
    ├── newState
    └── events
```

O RNG deverá ser isolável para testes e o GameState só poderá ser alterado por operações válidas do domínio.

## Próximo passo

Definir e registrar a edição/fonte normativa de Cairn e então implementar o primeiro núcleo do Gate 1 com testes automatizados.

## Regras para agentes

- Não implementar o jogo inteiro de uma vez.
- Não adicionar a Gemini API antes do Gate 4.
- Não transformar a IA em autoridade das regras.
- Não adicionar dependências sem justificativa.
- Não quebrar uma etapa já validada sem registrar a mudança.
- Não inventar ou misturar regras de edições diferentes de Cairn.

## Última atualização

Gate 0 validado no CI. Arquitetura de ações/resultados/eventos registrada no ADR-005. Projeto preparado para iniciar o Gate 1.
