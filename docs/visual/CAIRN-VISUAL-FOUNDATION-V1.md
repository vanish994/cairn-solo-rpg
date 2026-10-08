# Cairn Visual Foundation Kit v1

## Objetivo

Definir o primeiro pacote visual oficial do Cairn Solo RPG. O repositório já possui referências e alguns PNGs gerados, mas este kit passa a ser a especificação visual para a nova tela principal. Nenhum asset existente é considerado automaticamente parte do layout definitivo.

## Direção

- Android vertical, tela fixa/adaptativa.
- Dark fantasy pixel art.
- Cena compacta: 15% da altura útil.
- Guardião: aproximadamente 59%.
- Ações: aproximadamente 20%.
- Cabeçalho: aproximadamente 6%.
- Pixel art nítido, sem blur e sem esticar sprites.
- Texto narrativo é prioridade sobre decoração.
- Sem gore gráfico.
- Não copiar interface ou personagens de terceiros.

## Asset sources

### Base de cenário
**DungeonTileset II / 0x72**
- Usar como referência/base para chão, paredes, portas, escadas e decoração de dungeon.
- Selecionar somente tiles necessários.
- Não importar o pack inteiro para o APK.

### Complementos
**Kenney Roguelike/RPG / Caves & Dungeons**
- Usar somente props e tiles que mantenham a mesma leitura visual.
- Priorizar objetos de dungeon e ambiente.

### Criaturas
**DawnLike**
- Usar seletivamente apenas se a escala, silhueta e paleta forem compatíveis.
- Registrar licença/atribuição no manifesto antes de empacotar.

### UI
A UI principal é **Cairn própria**:
- painel do Guardião;
- caixa de intenção;
- botões de ação;
- ficha;
- inventário;
- indicadores de HP/condições;
- cabeçalho.

## Pacote lógico

assets/source/cairn-visual-v1/
- palette/
- tiles/
- props/
- characters/
- creatures/
- items/
- effects/
- ui/
- manifest/

app/src/main/res/drawable-nodpi/
- somente assets realmente usados no APK.

## Primeiro conjunto mínimo

### Tiles
- floor_stone
- wall_stone
- wall_corner
- doorway
- door_closed
- door_open
- stairs_down
- stairs_up
- cave_floor
- rock
- pillar
- torch

### Props
- chest
- barrel
- table
- shelf
- altar
- campfire
- bones
- corpse_placeholder
- well
- trap
- chain

### Player
- 1 sprite base
- idle
- walk
- attack
- hurt
- death
- 2 variações visuais de equipamento

### Inimigos iniciais
- goblin
- bandit
- cultist
- skeleton
- zombie
- spider
- rat
- wolf
- aberration
- elite

### Itens
- sword
- axe
- dagger
- bow
- shield
- helmet
- armor
- potion
- food
- torch
- coin
- key
- scroll

### FX
- hit
- critical
- miss
- damage
- heal
- fire
- smoke
- death

## Layout visual

```
┌──────────────────────────────┐
│ CAIRN                 LOCAL  │  6%
├──────────────────────────────┤
│                              │
│       CENA / DUNGEON         │
│                              │ 15%
├──────────────────────────────┤
│          GUARDIÃO            │
│                              │
│  narrativa com área ampla    │
│  e leitura confortável       │
│                              │
│  ┌────────────────────────┐  │
│  │ Descreva sua intenção… │  │
│  └────────────────────────┘  │
│                              │ 59%
├──────────────────────────────┤
│ [ CONTINUAR ] [ INVESTIGAR ] │
│ [ DESCANSAR ] [     FICHA ]  │ 20%
└──────────────────────────────┘
```

## Integração Android

A camada visual permanece em Compose. O domínio não recebe dependência de assets, Android ou Compose.

Fluxo:

GameState
→ Presentation Mapper
→ SceneModel / GuardianModel / ActionModel
→ Compose UI
→ drawable-nodpi / vector / Canvas quando apropriado

O Rules Engine continua sem conhecimento visual.

O Guardian continua retornando narrativa/contexto, nunca referências diretas a arquivos de imagem.

## Regra de tamanho

Não adicionar spritesheets ou packs completos ao APK.

Cada asset deve ser:
1. necessário para uma tela ou mecânica;
2. reduzido ao tamanho realmente utilizado;
3. registrado no manifest;
4. testado no APK release/debug;
5. removido se não houver uso.

Assets grandes de geração/source devem permanecer fora de drawable-nodpi quando não forem necessários em runtime.

## Compatibilidade com o repositório atual

O projeto já possui:
- Kotlin + Jetpack Compose;
- MainActivity como entrada visual;
- GuardianClient/GuardianContext;
- GameState e Rules Engine;
- manifesto de assets;
- pastas de source/generated e drawable-nodpi.

Portanto o kit não exige alteração no domínio nem no backend. A principal implementação será uma nova camada de apresentação visual e a organização/seleção dos assets.

## Critério de aceite

O Visual Foundation v1 será considerado integrado quando:
- a tela principal usar a proporção 6/15/59/20;
- a cena ocupar somente ~15%;
- o Guardião tiver a maior área;
- textos não vazarem de painéis;
- a UI funcionar em portrait;
- pelo menos um cenário, um personagem, um inimigo e os componentes básicos de UI estiverem renderizados;
- o APK não receber packs não utilizados;
- licenças/proveniência estiverem registradas;
- build debug continuar verde.
