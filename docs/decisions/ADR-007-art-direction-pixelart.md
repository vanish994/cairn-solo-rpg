# ADR-007 — Direção Artística Pixel Art

## Status
Aceito

## Contexto

Cairn Solo RPG será um RPG solo para Android baseado nas regras do Cairn Second Edition. A experiência visual deve reforçar exploração, fantasia sombria, perigo, descoberta e narrativa emergente, sem acoplar a apresentação visual ao motor de regras.

A direção artística do projeto é própria. Ela pode ser inspirada pelo clima de fantasia de Cairn, mas não deve copiar ilustrações, personagens, mapas, interfaces, fontes ou outros assets de terceiros.

## Decisão

O Cairn Solo RPG adotará **pixel art de fantasia sombria, legível e atmosférica** como linguagem visual oficial.

### 1. Estilo

- Pixel art com aparência de 16-bit/32-bit moderna.
- Silhuetas fortes e leitura imediata em telas pequenas.
- Detalhamento moderado; prioridade para composição e legibilidade.
- Fantasia medieval sombria, natureza selvagem, ruínas, cavernas, aldeias e locais misteriosos.
- Clima de aventura perigosa, mas sem gore gráfico.
- Personagens e criaturas com designs originais.
- Efeitos visuais econômicos e claros.

### 2. Resolução e escala

- A arte será criada em uma grade lógica pequena e escalável.
- Sprites principais terão alvo inicial de 32x32 ou 48x48 pixels lógicos.
- Tiles de cenário terão alvo inicial de 16x16 ou 32x32 pixels lógicos.
- A renderização deve usar escala inteira sempre que possível, evitando blur.
- A resolução final da tela não será tratada como resolução artística fixa; o jogo deve adaptar a composição a diferentes telas Android.

### 3. Paleta

A paleta deve privilegiar:
- tons terrosos e frios;
- verdes de floresta;
- cinzas de pedra;
- marrons de madeira e couro;
- tons claros reservados para informação importante;
- acentos de cor para estados, magia, itens e perigos.

Não haverá uma paleta RGB fixa neste ADR. Ela será formalizada quando o primeiro kit visual for criado.

### 4. Interface

A UI deve parecer parte do mundo do jogo, mas continuar funcional.

Princípios:
- painéis escuros e simples;
- bordas pixeladas;
- ícones consistentes;
- tipografia pixel-friendly apenas quando mantiver boa legibilidade;
- textos longos da narrativa devem priorizar legibilidade sobre estética;
- HP, atributos, Armor, inventário, condições e ações devem ser identificáveis sem depender exclusivamente de cor.

### 5. Narrativa e visual

A arte deve complementar a narrativa gerada pelo sistema, não substituir o texto.

O fluxo permanece:

Player input
→ GameAction
→ Rules Engine
→ GameState
→ Context Builder
→ AIProvider
→ Narrative
→ UI / Visual

O Gemini nunca controla diretamente assets ou regras visuais do domínio.

### 6. Arquitetura

Assets visuais e componentes de apresentação não podem virar dependência do domínio.

O domínio não deve importar:
- Android UI;
- Compose;
- classes de sprite;
- carregadores de imagens;
- Gemini;
- APIs gráficas.

A camada de apresentação poderá mapear estados do jogo para sprites, animações, partículas e componentes de UI.

### 7. Assets

Todo asset produzido para o projeto deve ser:
- original ou devidamente licenciado;
- versionado no repositório quando seu tamanho permitir;
- identificado por finalidade;
- acompanhado de regras de escala/uso quando necessário.

Não copiar assets de jogos existentes.

### 8. Fases de produção visual

**Fase A — Visual Foundation**
- paleta inicial;
- UI kit;
- tiles básicos;
- personagem placeholder;
- ícones de estado.

**Fase B — Gameplay**
- ambientes;
- personagens;
- criaturas;
- itens;
- efeitos.

**Fase C — Polish**
- animações;
- transições;
- feedback de combate;
- efeitos ambientais;
- refinamento de UI.

## Consequências

### Positivas
- Identidade visual própria.
- Pixel art combina com exploração e fantasia sombria.
- Assets podem ser produzidos incrementalmente.
- A UI pode funcionar bem em Android sem exigir arte 3D.
- A separação entre domínio e apresentação permite trocar ou evoluir o estilo sem reescrever as regras.

### Negativas
- Será necessário manter consistência de escala, paleta e iluminação.
- Assets pixel art exigem disciplina para evitar estilos misturados.
- A UI precisa equilibrar estética pixelada e acessibilidade/legibilidade.

## Regra futura

Antes de implementar a tela principal definitiva, deve existir um **Visual Foundation Kit** contendo pelo menos:
1. paleta inicial;
2. escala de sprites;
3. regras de tiles;
4. padrão de ícones;
5. componentes básicos de UI;
6. exemplos de cenário e personagem.

Este ADR define a direção, não os assets finais.
