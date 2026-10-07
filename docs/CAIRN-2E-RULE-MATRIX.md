# Matriz normativa — Cairn 2e

Fonte normativa do projeto:

- `Cairn_2e_Players_Guide.md`, versão 1.0.0, abril de 2026.
- `Cairn_2E_Wardens_Guide.md`, versão 1.0.0, março de 2026.

## Convenções

- **Implementado**: há comportamento determinístico e teste no domínio.
- **Parcial**: parte da regra existe, mas falta procedimento, persistência ou integração.
- **Pendente**: ainda não existe no Rules Engine.
- Conteúdo gerador do Warden's Guide só entra como regra de jogo quando houver uma decisão explícita de produto.

## Player's Guide

| Área | Status atual | Próxima unidade de trabalho |
|---|---|---|
| STR, DEX, WIL e saves | Implementado | Integrar saves em combate e procedimentos |
| HP, Armor e inventário básico | Parcial | Inventário cheio reduz HP a 0; mochila/equipamentos |
| Fatigue e Deprivation | Parcial | Deprivation por watches/dias e descanso contextual |
| Dano e Critical Damage | Parcial | Ataque completo, dano fora de combate e estabilização |
| Scars | Parcial | Persistência e recuperação normativa de cada entrada |
| Criação de personagem | Parcial | Bonds, Omens, traits e catálogo completo |
| Backgrounds | Parcial | Completar tabelas e resultados do guia |
| Combate | Parcial | Rounds, DEX inicial, ações, ataques, fuga e moral |
| Reações | Pendente | Tabela 2d6 e estado de reação |
| Moral | Pendente | WIL save e fuga de inimigos |
| Hirelings | Pendente | Modelo, geração e inventário |
| Magia/Spellbooks | Pendente | Conjuração, Fatigue e efeitos |
| Dungeon exploration | Parcial | Ciclo de turnos, ações e eventos |
| Wilderness exploration | Pendente | Watches, pontos, viagem, clima e acampamento |
| Downtime | Pendente | Recuperação, crescimento e atividades |
| Equipamento/Marketplace | Parcial | Catálogo, tags e regras ficcionais |

## Warden's Guide

| Área | Status atual | Próxima unidade de trabalho |
|---|---|---|
| Bestiário | Pendente | Modelo de criatura e dados de combate |
| Criação de monstros | Pendente | Procedimento do Warden separado do núcleo |
| Factions | Pendente | Fações, agendas, vantagens e ações |
| Dungeon seeds | Pendente | Geração de conteúdo opcional |
| Forest seeds | Pendente | Geração de conteúdo opcional |
| Pointcrawls | Pendente | Mapa, pontos e conexões |
| Growth | Pendente | Avanço ficcional, não níveis/classes |
| Spellbooks | Pendente | Catálogo e regras de risco |
| Reliquary | Pendente | Relíquias e efeitos |
| Vald | Pendente | Conteúdo de cenário, separado do motor genérico |

## Ordem de integração

1. Núcleo de combate e condições.
2. Inventário, descanso, deprivation e recuperação normativa.
3. Criaturas, reações, moral e hirelings.
4. Dungeon procedures.
5. Wilderness e downtime.
6. Magia, Spellbooks e relíquias.
7. Crescimento, facções e ferramentas do Warden.

A IA nunca resolve dados, altera HP, inventário, atributos ou condições. Ela pode apenas narrar e propor intenções/eventos que passam pelo domínio.
