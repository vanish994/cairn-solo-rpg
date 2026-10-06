# Regras para Agentes

Este arquivo é obrigatório para qualquer agente que modificar o projeto.

## Antes de trabalhar

1. Ler `README.md`.
2. Ler `docs/STATUS.md`.
3. Ler o Gate atual.
4. Ler ADRs relacionados à tarefa.
5. Inspecionar o código existente antes de criar arquivos novos.

## Durante o trabalho

- Não reescrever grandes partes sem necessidade.
- Não remover funcionalidade validada sem justificar.
- Não colocar segredos no Git.
- Não inventar regras de Cairn.
- Não permitir que o Gemini altere GameState diretamente.
- Preferir código simples.
- Adicionar testes quando houver regra mecânica.
- Atualizar documentação quando uma decisão mudar.

## Ao terminar

- Rodar build/testes disponíveis.
- Registrar o que mudou.
- Registrar problemas restantes.
- Atualizar `docs/STATUS.md`.
- Fazer commit descritivo.

## Formato recomendado de commit

```text
feat: adiciona criação inicial de personagem
fix: corrige cálculo de dano
docs: atualiza Gate 2
refactor: simplifica GameState
test: adiciona testes de combate
```
