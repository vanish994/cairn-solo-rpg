# Gate 4 — Integração Gemini

## Objetivo

Adicionar o Gemini Flash-Lite como provedor de IA.

## Arquitetura

```text
AIProvider
├── MockAIProvider
└── GeminiFlashLiteProvider
```

## Regras

- API key nunca vai para Git.
- Falha de rede não pode corromper GameState.
- Resposta da IA deve ser validada.
- IA não é autoridade mecânica.

## Critérios de aceitação

- [ ] Mock provider funciona.
- [ ] Gemini provider funciona.
- [ ] Chave fica fora do código versionado.
- [ ] Erros são tratados.
- [ ] Resposta inválida não altera regras.
