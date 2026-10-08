# Especificação — Correções do playtest do Cairn Solo RPG

**Data:** 2026-10-08 — **Status:** Requisitos aprovados para planejamento; implementação depende da revisão do plano e da escolha do método de execução.

## Objetivo

Fechar três lacunas encontradas no playtest de 30 minutos: falta de orientação sobre próximos passos, encontros com vários oponentes tratados como um só e pagamentos narrados sem crédito mecânico.

## Invariantes de arquitetura

- **Guardian:** narrador. Pode propor cenas, opções, perfis e intenções de oponentes, além de pedidos estruturados. Em `BEGIN_COMBAT`, uma ficha/arma é apenas um termo proposto para o encontro, validado pelo app e sujeito à confirmação do jogador; não determina resultados de ações nem altera a ficha do jogador.
- **Rules Engine / `GameActionResolver`:** única via para alterar HP, atributos, Armor, inventário, ouro, estados de combate ou outras consequências do jogo. `GameActionResolver` delega crédito de moeda a `MarketplaceRules` e capacidade/inserção de itens a `RulesEngine`.
- **Encontros:** `BEGIN_COMBAT` é uma proposta completa; nenhum combate começa até o jogador aceitar o encontro inteiro.
- O app mantém os fluxos de texto livre, saves antigos e a separação existente entre narrativa e fatos de regra.

## Orientação e ações sugeridas

- Toda resposta relevante do Guardian deve apresentar um objetivo imediato compreensível e de uma a três ações opcionais, cada uma com até 160 caracteres.
- As sugestões devem derivar da cena, do cânone e das ações disponíveis no contexto. Não podem inventar local, NPC, missão, saída ou fato histórico.
- A lista é acessória: o jogador continua livre para digitar qualquer intenção.
- Tocar em uma sugestão apenas preenche o campo de intenção; não envia nem executa a ação.
- A orientação e as sugestões também são atualizadas na continuação narrativa após uma resolução mecânica, inclusive combate.

**Aceitação:** na taverna, o app deixa claro qual pista/objetivo já conhecido pode ser seguido e oferece ações concretas ancoradas nessa informação. Após o combate, a continuação também apresenta próximos passos úteis. Em ambos os casos, texto livre continua disponível.

## Combate com vários oponentes

- Um encontro contém de 1 a 8 oponentes, cada qual com ID único, perfil narrativo, ficha, arma e status próprios. A proposta é aceita ou recusada como um todo.
- Preservar Cairn 2e: iniciativa por lados; na primeira rodada o personagem faz DEX save; as ações de um lado são simultâneas. Oponentes derrotados antes da fase inimiga não agem.
- Quando vários atacantes do mesmo lado atacam o mesmo alvo, rolam seus dados e somente o resultado mais alto é aplicado; Armor do alvo é aplicada uma vez.
- NPC/monstro exatamente a 0 HP não recebe Scar e não é automaticamente derrotado. Dano que leve abaixo de 0 reduz STR pelo excedente e exige STR save; falha marca o oponente como derrotado. A mecânica não determina se a descrição ficcional é morte ou incapacitação.
- Oponente solitário faz WIL save ao chegar a 0 HP para evitar fugir. Em grupo, moral é verificada na primeira baixa e novamente ao perder metade do tamanho inicial; cada limiar ocorre uma vez. Cada sobrevivente testa com seu WIL, salvo quando um líder ativo fornece o valor substituto. Se os dois limiares coincidirem, resolver FIRST_CASUALTY antes de HALF_GROUP e interromper quando não houver sobreviventes ativos.
- Contabilizar baixa somente quando o oponente for derrotado, não apenas ao chegar a 0 HP. Oponentes fugitivos e derrotados permanecem individualmente identificáveis para a narrativa pós-combate.
- Saves existentes com um oponente devem carregar como encontro de um oponente; dado de arma corrompido não pode descartar toda a campanha.

**Aceitação:** dois cultistas aparecem e podem ser escolhidos como alvos distintos; o combate aplica resolução por lado, maior dano entre atacantes, moral individual e migração do save antigo sem fundir fichas.

## Recompensas estruturadas

- O Guardian pode emitir `REWARD` com ID estável, estado `OFFERED` ou `PAID`, valor inteiro `amountGp` entre 0 e 1.000.000 e até cinco `itemCatalogIds`.
- `PAID` só é usado quando a cena afirma que a transferência já ocorreu. `OFFERED` representa promessa/negociação e não muda estado.
- `PAID` é aplicado automaticamente por uma única `GameAction.GrantReward` via `GameActionResolver`; IDs repetidos não duplicam crédito, mesmo em retry ou payload alterado.
- Ouro é saldo de GP (`CharacterProfile.gold`), fora do inventário e dos slots. O Marketplace de Cairn 2e expressa preços em GP; nenhuma conversão de cobre para GP é introduzida.
- Itens só podem ser instâncias de entradas de `MarketplaceCatalog` que contenham um `InventoryItem`; o Guardian não fornece stats, slots, Armor ou dano. IDs de catálogo repetidos significam cópias separadas.
- Se não houver espaço, o item fica em uma lista de recompensas pendentes para resgate após o jogador liberar slots. Nunca descartar equipamento nem perder silenciosamente a recompensa. ID inválido/não elegível gera aviso sem impedir o crédito de GP válido.
- Saves antigos preservam `profileGold`; as novas chaves de idempotência e pendências têm defaults vazios.

**Aceitação:** após o taverneiro entregar 12 GP e a cena emitir `PAID`, o saldo aumenta exatamente em 12 e persiste após recarregar. `OFFERED` não altera o saldo. Um item elegível entra no inventário ou fica pendente quando não há espaço. GP nunca aparece como item. Quantias em cobre não são convertidas.

## Fora de escopo

- Conversão entre cobre e GP, itens personalizados fora do catálogo, fuga do personagem jogador, conclusão de todos os gates de combate e fechamento do Gate 8.
- A UI não envia ações sugeridas nem recompensa automaticamente itens com estatísticas fornecidas pelo Guardian.

## Referências

- Relato de playtest e decisão de crédito automático fornecidos pelo usuário em 2026-10-08.
- [Cairn 2e — Warden's Guide: Combat](https://cairnrpg.com/second-edition/wardens-guide/combat/)
- [Cairn 2e — Player's Guide: Core Rules](https://cairnrpg.com/second-edition/players-guide/core-rules/)
- [Cairn 2e — Player's Guide: Marketplace](https://cairnrpg.com/second-edition/players-guide/marketplace/)
- `docs/DEVELOPMENT-GUIDE.md` e `docs/CAIRN-2E-RULE-MATRIX.md` do repositório.
