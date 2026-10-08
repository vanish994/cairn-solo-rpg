# Recompensas Estruturadas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Creditar automaticamente pagamentos em GP e itens elegíveis quando o Guardian confirmar, em um pedido de regra `REWARD`, que foram entregues; mostrar saldo, itens pendentes e feedback sem conceder autoridade mecânica ao Guardian.

**Architecture:** O contrato `GuardianRuleRequest(type = "REWARD")` traz ID, estado (`OFFERED`/`PAID`), quantia GP e IDs de itens do catálogo Cairn. O `GuardianRuleResolver` encaminha `PAID` como uma `GameAction.GrantReward`; `GameActionResolver`/Rules Engine valida e aplica tudo em um turno. Item sem espaço vira recompensa pendente, nunca causa descarte automático. A UI mostra ouro fora do inventário e permite resgatar itens pendentes depois de liberar espaço.

**Tech Stack:** Kotlin, Gson/JSON Schema, Android SharedPreferences e testes Kotlin/JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-08-playtest-feedback.md` — seção “Recompensas estruturadas”; escolha do usuário em 2026-10-08: “Creditar automaticamente quando o pagamento for estruturado e confirmado na cena”; [Cairn 2e — Marketplace](https://cairnrpg.com/second-edition/players-guide/marketplace/); `docs/CAIRN-2E-RULE-MATRIX.md`.

## Global Constraints

- O Marketplace oficial diz que todos os preços são em **gold pieces (GP)**; manter GP como unidade mecânica e não inventar câmbio de cobre para ouro.
- Cobre/denominações não suportados não podem ser creditados como GP. Se isso for desejado, requer regra de mesa aprovada separadamente.
- Moedas ficam no saldo `CharacterProfile.gold`, não ocupam slots e não são `InventoryItem`.
- Creditar somente se a resposta narrar transferência já concluída e o pedido estruturado indicar `PAID`; uma oferta ou promessa (`OFFERED`) não altera o estado.
- Itens só podem usar IDs existentes em `MarketplaceCatalog` com `item != null`; o Guardian não define slots, Armor, dano ou outros atributos mecânicos.
- Toda mutação passa por `GameActionResolver`; crédito de moeda é validado por `MarketplaceRules.creditGold`, itens/capacidade por `RulesEngine.addItem`; ID de recompensa/componentes impede duplicação em retry.
- Se faltarem slots, manter o item em `pendingRewardItems` e pedir espaço para resgate; nunca remover um item automaticamente.
- Ao preencher exatamente os 10 slots, não reduzir HP; atingir a capacidade não equivale a dano em Cairn 2e.
- Entregar via branch/PR; exigir Android Build verde antes de merge em `main`.

## Review Focus

1. **Estado/idempotência:** `OFFERED` não altera estado; `PAID` aplica uma vez e payload alterado com o mesmo ID não concede novamente.
2. **Item não permitido:** catálogo desconhecido ou sem `InventoryItem` não aceita stats do Guardian; GP válido ainda pode ser aplicado e o item gera aviso.
3. **Capacidade:** item sem espaço fica pendente sem descarte; preencher exatamente o 10º slot não reduz HP.
4. **Payload monetário inválido:** rejeitar valor negativo/acima do limite, ID inválido e denominação não-GP sem converter silenciosamente.
5. **Apresentação:** saldo não aparece no inventário; itens pendentes são visíveis e resgatáveis depois de abrir espaço.

---

### Task 1: Ações de domínio para GP e itens de catálogo

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameState.kt`, `GameAction.kt`, `GameStatePersistenceCodec.kt`
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/rules/RulesEngine.kt`, `app/src/main/kotlin/com/vanish994/cairnsolo/rules/Marketplace.kt`
- Test: `app/src/test/kotlin/com/vanish994/cairnsolo/game/GameActionResolverTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/game/RewardPersistenceTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/rules/RulesEngineTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/rules/MarketplaceTest.kt`

**Interfaces:**
- Produzir `data class PendingRewardItem(val id: String, val rewardId: String, val catalogItemId: String, val itemInstanceId: String)`.
- `CampaignState.appliedRewardIds: Set<String>` persiste IDs de recompensa/componentes processados; `pendingRewardItems: List<PendingRewardItem>` persiste itens entregues que aguardam espaço.
- Produzir `GameAction.AddGold(val rewardId: String, val amountGp: Int)`, `GameAction.GrantReward(val rewardId: String, val amountGp: Int, val itemCatalogIds: List<String>)` e `GameAction.ClaimPendingRewardItem(val pendingId: String)`.
- Produzir `GameEvent.GoldCredited(val rewardId: String, val amountGp: Int, val newBalanceGp: Int)`, `RewardItemAdded(val itemInstanceId: String, val catalogItemId: String)`, `RewardItemPending(val pendingId: String, val catalogItemId: String, val slotsRequired: Int, val freeSlots: Int)`, `RewardItemClaimed(val pendingId: String, val itemInstanceId: String)` e `RewardItemRejected(val catalogItemId: String, val reason: String)`.
- `MarketplaceRules.creditGold(currentGoldGp: Int, amountGp: Int): Int` exige saldo não negativo e crédito positivo; usa `Math.addExact` e retorna o novo saldo ou rejeita overflow.
- `GameActionResolver` também valida diretamente o ID (`^[a-z0-9-]{3,80}$`), `amountGp in 0..1_000_000`, até cinco itens e que pelo menos GP ou um item seja concedido; ação inválida falha sem mutar o estado.
- `GrantReward` aplica ouro e todos os itens em uma única transição de turno. Guardar `"$rewardId:grant"` como chave do pacote e `"$rewardId:gold"`/`"$rewardId:item:$ordinal"` como chaves de componentes; replay ou payload diferente com o mesmo ID não concede novamente. Itens são materializados por `MarketplaceCatalog.find(catalogItemId)?.item`, nunca por stats enviados pelo Guardian.
- Cada instância usa `itemInstanceId = "reward:$rewardId:$ordinal:$catalogItemId"`, copia stats do catálogo e guarda a tag `reward-catalog:$catalogItemId`; claim reutiliza a mesma instância.

- [ ] Escrever `MarketplaceTest.creditGoldAddsGpAndRejectsOverflow`; escrever `GameActionResolverTest.creditGoldUpdatesBalanceAndRecordsEvent`, `creditGoldIsIdempotent`, `invalidGrantRewardRejected`, `grantRewardAppliesGoldAndItemsInOneTurn`, `grantRewardIsIdempotent`, `grantRewardWithChangedPayloadIsNoOp`, `fullInventoryStoresRewardAsPending`, `claimPendingRewardItemAfterSpaceFreed`, `unsupportedItemIdDoesNotAddItem` e `nonItemCatalogEntryCannotBeGranted`; escrever `RulesEngineTest.fillingLastSlotDoesNotSetHpToZero` e `RewardPersistenceTest.pendingAndAppliedRewardIdsRoundTrip`.

```kotlin
assertEquals(12, paid.state.campaign.profile.gold) // começar com 0 GP e aplicar a recompensa de teste
assertEquals(paid.state, replayWithSameRewardId.state) // replay/payload alterado é no-op
assertEquals(hpBefore, fullInventoryResult.state.campaign.rules.hp)
assertEquals(1, fullInventoryResult.state.campaign.pendingRewardItems.size)
```

- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` com os testes novos; confirmar falha esperada antes da implementação. O Sandbox não possui Gradle/Android SDK local.
- [ ] Corrigir `RulesEngine.addItem` para que ocupar exatamente 10 slots não reduza HP; manter a regra de capacidade e não remover itens existentes.
- [ ] Implementar `AddGold` no `GameActionResolver`; chamar `MarketplaceRules.creditGold`, registrar `rewardId:gold` e emitir `GoldCredited` com novo saldo.
- [ ] Implementar `GrantReward`; chamar `MarketplaceRules.creditGold` e materializar itens via `RulesEngine.addItem` em uma única transição/turno. Se não houver espaço, persistir cada item pendente em vez de descartar item ou falhar silenciosamente.
- [ ] Implementar `ClaimPendingRewardItem`; só resgatar quando há slots suficientes, chamar `RulesEngine.addItem` e remover a pendência. Liberar espaço é escolha do jogador por ações normais de inventário.
- [ ] Persistir `appliedRewardIds` e `pendingRewardItems`; saves antigos sem essas chaves decodificam com conjunto/lista vazios. `profileGold` existente permanece compatível.
- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` após a implementação; confirmar Android Build verde.
- [ ] Commit atômico: `feat: resolve structured gold and item rewards`.

### Task 2: Pedido de regra REWARD e confirmação narrativa

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/guardian/GuardianClient.kt`, `GuardianContext.kt`, `GuardianRuleResolver.kt`
- Modify: `guardian-server/src/main/kotlin/com/vanish994/cairnsolo/guardian/Server.kt`
- Test: `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianClientTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianContextTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianRuleResolverTest.kt`, `guardian-server/src/test/kotlin/com/vanish994/cairnsolo/guardian/ServerTest.kt`

**Interfaces:**
- Produzir `data class GuardianRewardProposal(val id: String, val status: RewardStatus, val amountGp: Int, val itemCatalogIds: List<String>)` e `enum class RewardStatus { OFFERED, PAID }`.
- `GuardianRuleRequest` ganha `reward: GuardianRewardProposal?`; `type="REWARD"` aceita objeto estrito `{id,status,amountGp,itemCatalogIds}`. ID deve casar `^[a-z0-9-]{3,80}$`, `amountGp` fica em 0–1.000.000 e há no máximo cinco IDs de item. `PAID` requer GP positivo ou ao menos um item; `OFFERED` nunca altera estado.
- Produzir `data class RewardableItemContext(val catalogId: String, val name: String, val slotCost: Int)`; `GuardianContext` inclui `goldGp`, `freeSlots` e `rewardableItems` somente para entradas de `MarketplaceCatalog` com item.
- `GuardianRuleResolver.validationError(state, request)` valida ID/status/quantia/lista; `resolve(state, request)` encaminha `PAID` como uma única `GameAction.GrantReward`, sem alterar estado diretamente.
- `GuardianRuleResolver.resolve(state, request): GuardianRuleResolution` resolve `OFFERED` sem mutação e despacha `PAID` por uma única chamada a `GameActionResolver.resolve(state, GameAction.GrantReward(...))`, para a recompensa consumir um único turno; IDs desconhecidos geram `RewardItemRejected` sem impedir crédito de GP válido.

- [ ] Escrever testes para `PAID`, `OFFERED`, valor inválido/fora do limite, ID inválido, catálogo desconhecido e unidade não-GP; afirmar que saldo, slots livres e IDs/nome/custo de catálogo estão no contexto.
- [ ] Escrever `GuardianRuleResolverTest.offeredRewardDoesNotChangeGameState` e `paidRewardDispatchesGoldAndItemsThroughResolver`.
- [ ] Escrever `GuardianRuleResolverTest.offeredRewardCanBecomePaidWithSameId`; `OFFERED` não reserva o ID, e a confirmação posterior aplica uma vez.

```kotlin
assertEquals(initialState, offeredResult.state)
assertEquals(12, paidResult.state.campaign.profile.gold)
assertEquals(paidResult.state, paidReplayWithSameId.state)
```

- [ ] Escrever `ServerTest` para `REWARD` aceitar GP/IDs válidos e rejeitar campos extras, status inválido, ID malformado, valores negativos/acima do limite e lista de itens acima de cinco; permitir IDs de catálogo repetidos para conceder instâncias distintas.
- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` com os testes novos; confirmar falha esperada antes da implementação.
- [ ] Atualizar prompt/schema: emitir `REWARD` com `PAID` somente quando a cena narra transferência já concluída; `OFFERED` para promessa/negociação; usar GP (sem câmbio de cobre), selecionar apenas `rewardableItems` e nunca inventar stats. O schema permite até cinco IDs de item; IDs repetidos representam cópias e recebem instâncias únicas por ordinal.
- [ ] Implementar parsing e validação no `GuardianRuleResolver`; `PAID` usa uma única `GameAction.GrantReward`, item sem espaço vira pending via `GameActionResolver`, oferta não altera estado, ID fora do catálogo não concede item.
- [ ] Nos dois caminhos de resposta da `MainActivity` (intenção comum e continuação pós-regra), auto-resolver `REWARD` sem botão de rolagem/confirmação; todos os efeitos passam por `GameActionResolver`.
- [ ] Em proposta inconsistente, preservar a narração e mostrar aviso de recompensa não aplicada em vez de ignorar silenciosamente.
- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` após a implementação; confirmar unit tests, Guardian server tests, integração HTTP e APK release verdes.
- [ ] Commit atômico: `feat: resolve confirmed Guardian reward requests`.

### Task 3: Exibir saldo, itens e feedback de resgate

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/MainActivity.kt`, `app/src/main/kotlin/com/vanish994/cairnsolo/feedback/Feedback.kt`
- Test: `app/src/test/kotlin/com/vanish994/cairnsolo/feedback/FeedbackMapperTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/game/GameActionResolverTest.kt`

**Interfaces:**
- A ficha mostra `Ouro: <saldo> po` em seção própria, separada de `INVENTÁRIO`.
- A seção de recompensas pendentes mostra nome, slots necessários e botão `Resgatar` habilitado somente quando há espaço suficiente.
- `inventoryItemLabel(item: InventoryItem): String` resolve `reward-catalog:<catalogId>` para `MarketplaceCatalog.find(catalogId)?.name`; itens antigos mantêm o rótulo atual.
- `GoldCredited`, `RewardItemAdded`, `RewardItemPending`, `RewardItemClaimed` e `RewardItemRejected` produzem feedback localizado com resultado preciso.

- [ ] Escrever `FeedbackMapperTest.goldCreditedShowsAmountAndNewBalance`, `rewardItemAddedShowsCatalogName`, `rewardItemPendingShowsSlotsNeeded` e `rewardItemRejectedExplainsUnsupportedCatalogId`.

```kotlin
assertEquals("Você recebeu 12 po. Saldo: 12 po.", FeedbackMapper.map(GameEvent.GoldCredited("quest-pay", 12, 12), turn = 1).message)
```

- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` com os testes de feedback/ficha; confirmar falha esperada antes da implementação.
- [ ] Mostrar `CharacterProfile.gold` na `CharacterSheet`; manter GP fora da lista de itens e fora do cálculo de slots.
- [ ] Mostrar os itens pendentes com nome vindo de `MarketplaceCatalog`; ao resgatar, chamar `GameAction.ClaimPendingRewardItem` via `GameActionResolver`.
- [ ] Se não houver espaço, explicar quantos slots liberar e preservar todos os itens; nunca chamar `RemoveItem` sem ação explícita do jogador.
- [ ] Mapear eventos em português brasileiro; não exibir crédito para `OFFERED`.
- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` e conferir manualmente pagamento de 12 po, item de catálogo no inventário, item pendente quando cheio, resgate depois de abrir espaço e save/reload.
- [ ] Commit atômico: `feat: show balance and claim reward items`.

### Task 4: Integração

**Branch:** `feature/structured-rewards` (criar a partir de `main`; um PR focado).

- [ ] Abrir PR para `main`; exigir Android Build verde (unit tests, Guardian server tests, integração HTTP e APK release) no HEAD atual.
- [ ] Aceitação manual: “O taverneiro entrega 12 po” com `PAID` aumenta saldo exatamente em 12 e persiste; `OFFERED` não altera estado; item válido de catálogo vai para o inventário, item sem espaço fica pendente sem apagar outro item; nenhuma moeda aparece como item.
- [ ] Atualizar `docs/STATUS.md` e `docs/ARCHITECTURE-MAP.md` após CI; merge somente com checks verdes.
