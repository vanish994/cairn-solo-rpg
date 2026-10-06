# Gate 1 — Catálogo Canônico de Regras Cairn 2e

## Fonte normativa

Cairn Second Edition, principalmente:
- Player's Guide — Core Rules
- Player's Guide — Character Creation
- Warden's Guide — regras de combate quando necessário

Fonte oficial: https://cairnrpg.com/second-edition/

A implementação deve usar a 2e como autoridade. Regras de 1e não devem ser importadas sem uma decisão arquitetural explícita.

## Catálogo inicial

| ID | Regra | Implementação inicial |
|---|---|---|
| ATTR-001 | STR, DEX e WIL são os três atributos | `Attribute` |
| SAVE-001 | Save: d20 <= atributo | `RulesEngine.save` |
| SAVE-002 | 1 sempre sucesso; 20 sempre falha | `RulesEngine.save` |
| HP-001 | HP representa proteção em combate | `CharacterState.hp` |
| HP-002 | HP não pode ficar abaixo de zero | invariantes do domínio |
| ARM-001 | Armor reduz o dano antes de HP | `DamageResolver` |
| ARM-002 | Armor máximo 3 | invariantes do domínio |
| INV-001 | Inventário possui 10 slots | `Inventory` |
| INV-002 | Backpack adiciona 6 slots | `Inventory` |
| INV-003 | Item ocupa 1 slot por padrão; petty 0; bulky 2 | `ItemSize` |
| INV-004 | Inventário cheio reduz HP a 0 | operação de carga |
| FAT-001 | Fatigue ocupa 1 slot | item especial de inventário |
| FAT-002 | Deprivation prolongada adiciona Fatigue | operação futura de sobrevivência |
| DMG-001 | Dano é reduzido pela Armor | `applyDamage` |
| CRIT-001 | Dano que ultrapassa 0 HP reduz STR pelo excesso | `applyDamage` |
| CRIT-002 | Após Critical Damage, exige STR save | resultado explícito da operação |
| REC-001 | Descanso seguro recupera HP conforme ficção/regra | operação futura de recovery |

## Fora deste primeiro incremento

Ainda não serão implementados neste commit:
- geração completa de backgrounds;
- tabela completa de Scars;
- combate completo;
- magia completa;
- NPCs/hirelings;
- procedimentos do Warden;
- persistência;
- Gemini.

Esses itens entram em incrementos separados para manter o Gate verificável.

## Regra de arquitetura

O catálogo descreve comportamento mecânico, não reproduz texto extenso da obra. Cada regra deve ser representada por tipos, funções e testes.
