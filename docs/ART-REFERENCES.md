# Referências de Desenvolvimento Visual

Este documento registra projetos externos usados como **referência técnica e visual** para o desenvolvimento do Cairn Solo RPG.

Eles não são dependências do aplicativo e seus assets não entram no projeto automaticamente.

## Referências principais

### PixelArtWeatherML

Uso:
- referência de UI pixel art em Android;
- organização de telas;
- Jetpack Compose;
- adaptação a diferentes tamanhos de tela.

Repositório:
https://github.com/ArtemZarubin/PixelArtWeatherML

Regra: usar como referência de implementação e composição. Não copiar código ou assets sem verificar a licença aplicável.

### Pixel-Warrior-Monsters

Uso:
- referência de estrutura de RPG mobile;
- HUD;
- inventário;
- navegação;
- telas de combate;
- persistência.

Repositório:
https://github.com/Gameaday/Pixel-Warrior-Monsters

Regra: referência arquitetural/visual, não fonte de assets do Cairn.

### DotKit

Uso potencial:
- desenho pixel a pixel;
- grid e zoom;
- ferramentas internas de criação.

Repositório:
https://github.com/kez-lab/DotKit

Regra: só avaliar integração se surgir necessidade real de editor ou ferramenta de criação dentro do aplicativo.

## Ferramenta de produção

### Pixelorama

Uso:
- criação dos sprites próprios;
- tiles;
- animações;
- paletas;
- spritesheets;
- exportação de PNG.

Repositório:
https://github.com/Orama-Interactive/Pixelorama

O projeto é open source sob MIT conforme o repositório oficial, mas isso se refere ao software Pixelorama, não automaticamente aos assets produzidos por terceiros usando a ferramenta.

## Recursos de assets

### Intersect-Assets

Pode ser consultado para prototipagem e estudo de composição, mas **não é uma fonte automática de assets para o Cairn**.

Cada asset deverá ser verificado individualmente quanto à licença e atribuição antes de ser incorporado.

## Regra de licenciamento

Antes de adicionar qualquer recurso externo ao APK:

1. identificar o autor/origem;
2. identificar a licença do recurso específico;
3. verificar se a licença permite o uso pretendido;
4. registrar atribuição quando exigida;
5. registrar o recurso em um inventário de créditos;
6. nunca assumir que a licença do repositório vale para todos os arquivos/artefatos nele contidos.

## Regra de identidade

O Cairn Solo RPG terá **identidade visual própria**.

Projetos externos servem para estudar:
- composição;
- navegação;
- escalas;
- padrões de UI;
- organização técnica;
- ferramentas.

Não servem para copiar:
- personagens;
- criaturas;
- mapas;
- sprites;
- ícones;
- interfaces inteiras;
- ilustrações.

A direção oficial continua definida pelo ADR-007.
