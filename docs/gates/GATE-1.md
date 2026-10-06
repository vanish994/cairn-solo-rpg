# Gate 1 — Motor de Regras Cairn

## Objetivo

Criar a autoridade mecânica determinística do jogo.

## Fonte normativa

O motor usa **Cairn Second Edition (2e)** como referência normativa. A página oficial de Core Rules confirma as regras de saves, HP, Armor, inventário, Fatigue/Deprivation, Critical Damage, combate e recuperação. citeturn0search0turn0search1

Não misturar regras de outras edições sem uma decisão arquitetural explícita.

## Implementado

- [x] STR, DEX e WIL.
- [x] Saves d20 com 1 sempre sucesso e 20 sempre falha.
- [x] HP e invariantes do estado.
- [x] Armor com limite de 3.
- [x] Redução de dano por Armor.
- [x] Critical Damage: excesso reduz STR e dispara STR save imediato.
- [x] Estado de Critical Damage.
- [x] Inventário de 10 slots.
- [x] Itens petty sem custo de slot.
- [x] Itens comuns e bulky.
- [x] Fatigue ocupando slots.
- [x] Deprivation impedindo recuperação no descanso seguro.
- [x] Recuperação de HP e Fatigue em descanso seguro.
- [x] Remoção de itens.
- [x] RNG isolável para testes.
- [x] Eventos determinísticos para mudanças relevantes.

## Fora do Gate 1

Estes sistemas pertencem a incrementos posteriores e não impedem o fechamento deste gate:

- geração completa de personagens/backgrounds;
- tabela completa de Scars como conteúdo estruturado;
- catálogo completo de magia, spellbooks, scrolls e relics;
- NPCs/hirelings;
- procedimentos completos de Warden;
- persistência;
- Gemini;
- interface de combate.

O motor fornece a base determinística necessária para esses sistemas sem depender de Android ou Gemini.

## Critérios de aceitação

- [x] Regras essenciais da base do motor implementadas.
- [x] Testes automatizados cobrindo saves, dano, Armor, Critical Damage, inventário, Fatigue e recuperação.
- [x] Engine funciona sem Gemini.
- [x] Estado só é alterado por operações do domínio.
- [x] RNG pode ser controlado em testes.
- [ ] GitHub Actions confirma build/testes verdes no commit final.

## Arquitetura

```text
GameState
   ↑
Rules Engine
   ↑
Domain operation
   ↑
UI / AI suggestion

AI nunca altera GameState diretamente.
```

## Regra de manutenção

Qualquer nova regra deve:

1. ser comparada com a fonte normativa;
2. ser implementada no domínio, não na UI;
3. receber teste automatizado;
4. produzir resultado determinístico quando o RNG for controlado;
5. preservar a independência de Android e Gemini.
