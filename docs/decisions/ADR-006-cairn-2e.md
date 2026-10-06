# ADR-006 — Cairn 2ª Edição como base normativa

## Decisão

O Cairn Solo RPG usará **Cairn Second Edition (2ª Edição)** como sua referência normativa principal.

Fontes normativas:

- Cairn 2e — Player's Guide.
- Cairn 2e — Warden's Guide.
- Materiais oficiais da 2ª edição publicados pelo projeto Cairn.

A implementação deve acompanhar a 2ª edição atual e não misturar regras da 1ª edição sem uma decisão explícita.

## Escopo

O Rules Engine deverá implementar as regras da 2e necessárias ao aplicativo, priorizando:

- atributos e saves;
- HP e dano;
- Armor;
- inventário e slots;
- Fatigue/Deprivation;
- condições e efeitos;
- Critical Damage;
- Scars;
- combate;
- magia;
- recuperação;
- procedimentos relevantes para o jogo solo.

Regras narrativas ou procedimentos do Warden que não precisem de automação mecânica permanecerão fora do núcleo até serem necessários.

## Fonte e atualização

O repositório oficial de Cairn mantém materiais de múltiplas edições. A 2e possui Player's Guide e Warden's Guide próprios. O projeto deve registrar futuras mudanças relevantes da fonte normativa antes de alterar o comportamento do Rules Engine.

## Princípio de implementação

O código não deve reproduzir textos longos dos livros. Deve representar as regras como dados, tipos, funções e testes necessários para executar a mecânica.

Toda regra implementada deve ter:

1. referência à seção da fonte normativa;
2. comportamento determinístico quando o RNG é controlado;
3. testes automatizados cobrindo casos normais e limites.

## Compatibilidade

A Cairn Barebones Edition não será a base normativa principal. Ela poderá ser usada posteriormente como referência de apresentação genérica, pois a documentação oficial informa que suas regras e procedimentos correspondem ao Player's Guide da 2e.

## Consequência

O Gate 1 passa a ser implementado especificamente contra Cairn 2e. Não serão aceitas decisões baseadas apenas em memória, resumos de terceiros ou regras da 1e quando houver regra correspondente na 2e.
