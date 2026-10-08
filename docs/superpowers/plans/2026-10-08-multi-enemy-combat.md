# Combate com Múltiplos Oponentes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Representar cada adversário de um encontro separadamente e resolver a rodada do lado inimigo conforme Cairn 2e, para que dois cultistas não virem um único inimigo fictício.

**Architecture:** Substituir o oponente singular de `CombatState` por uma lista de combatentes com IDs, ficha, arma e narrativa próprios. Proposta Guardian, resolver, contexto, UI e persistência passam a transportar a lista; o codec migra saves antigos com um oponente para uma lista de um elemento.

**Tech Stack:** Kotlin, Jetpack Compose, Gson/JSON Schema, Android SharedPreferences e testes Kotlin/JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-08-playtest-feedback.md` — seção “Combate com vários oponentes”; `docs/CAIRN-2E-RULE-MATRIX.md`; [Cairn 2e — Warden's Guide: Combat](https://cairnrpg.com/second-edition/wardens-guide/combat/) e [Player's Guide: Core Rules](https://cairnrpg.com/second-edition/players-guide/core-rules/).

## Global Constraints

- Seguir Cairn 2e, sem inventar ataques cumulativos: ações do lado ocorrem simultaneamente; ataques acertam automaticamente; vários atacantes contra o mesmo alvo rolam seus dados e aplicam somente o maior resultado.
- Na primeira rodada, o jogador faz DEX save; o lado adversário age em seguida. Ações declaradas pelo lado resolvem-se simultaneamente.
- Inimigos fazem WIL saves de moral na primeira baixa e novamente quando `2 * defeatedCount >= initialCount`; cada limiar é processado uma vez, em ordem FIRST_CASUALTY e depois HALF_GROUP, parando se todos já fugiram. Se ambos coincidirem, executar ambos como testes distintos no mesmo evento (leitura literal dos dois gatilhos; em um grupo de dois, o sobrevivente pode rolar duas vezes).
- A moral de inimigos usa save d20 de WIL conforme Cairn 2e; não reutilizar `MoraleRules.check` de `WardenRules.kt`, que hoje é 2d6 para hirelings.
- NPC/monstro a exatamente 0 HP não ganha Scar e não é automaticamente derrotado. Dano excedente abaixo de 0 reduz STR e exige STR save; falha derrota o NPC, sucesso o mantém no combate. A resolução de dano precisa distinguir PC e NPC sem mudar saves legados de personagem.
- Um inimigo solitário faz WIL save ao chegar a 0 HP para evitar fugir. Grupo faz testes ao sofrer a primeira baixa e quando `2 * defeatedCount >= initialCount` (primeiro inteiro que cruza metade do tamanho inicial). Se ambos ocorrerem juntos, resolvê-los como testes distintos na ordem acima, sem repetir um gatilho já processado.
- Cada sobrevivente faz seu próprio WIL save; quando `moraleLeaderId` está ativo, usar a WIL do líder em lugar da própria. Se o líder já estiver derrotado/fugido, voltar aos saves individuais. Registrar fuga/derrota por combatente.
- Preservar migração dos saves de combate singular; validar IDs únicos, HP, Armor 0–3 e dados de arma suportados.
- BEGIN_COMBAT continua sendo uma proposta: o jogador confirma ou recusa o grupo inteiro antes de qualquer `CombatState` ser criado.
- Não declarar o Gate de combate fechado: fuga do jogador, bestiário e outras lacunas seguem o roadmap.
- Primeiro revisar e mesclar o PR de documentação destes planos; depois criar o branch de feature a partir da `main` atualizada para que spec e plano acompanhem o trabalho.
- Entregar via branch/PR; exigir Android Build verde antes de merge em `main`.

## Review Focus

1. **IDs de oponente duplicados:** rejeitar a proposta inteira sem iniciar parcialmente o combate.
2. **Fase e simultaneidade por lado:** inimigos derrotados antes da fase adversária não atuam; inimigos ativos na fase atacam simultaneamente.
3. **Ataque de grupo contra o jogador solo:** rolar cada dano e aplicar somente o maior, com Armor aplicada uma única vez.
4. **NPC a 0 HP e moral de grupo:** não aplicar Scar/derrota automática a 0 HP; processar save solitário, crítico e limiares FIRST_CASUALTY/HALF_GROUP com líder válido.
5. **Save legado ou corrompido:** migrar oponente singular e não deixar arma inválida derrubar a campanha inteira.

---

### Task 1: Modelos, contrato Guardian e conversão no resolver

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameState.kt`
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameAction.kt` — rota `BeginCombat` plural e construção inicial do estado; a resolução completa por grupos permanece na Task 2.
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/guardian/GuardianClient.kt`, `GuardianContext.kt`, `GuardianRuleResolver.kt`
- Modify: `guardian-server/src/main/kotlin/com/vanish994/cairnsolo/guardian/Server.kt`
- Test: `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianClientTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianCombatContextTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianRuleResolverTest.kt`, `guardian-server/src/test/kotlin/com/vanish994/cairnsolo/guardian/ServerTest.kt`

**Interfaces:**
- Produzir `data class CombatOpponentState(val id: String, val narrative: CombatOpponentNarrative, val stats: CharacterState, val weapon: WeaponProfile, val status: CombatOpponentStatus = CombatOpponentStatus.ACTIVE)` e `enum class CombatOpponentStatus { ACTIVE, DEFEATED, FLED }`.
- `data class CombatState(val opponents: List<CombatOpponentState>, val moraleLeaderId: String? = null, val resolvedMoraleTriggers: Set<CombatMoraleTrigger> = emptySet(), val round: Int = 1, val playerCanAct: Boolean = true)`; exigir ao menos um oponente ativo e IDs únicos.
- Produzir `enum class CombatMoraleTrigger { FIRST_CASUALTY, HALF_GROUP }`.
- Produzir `enum class CombatEndReason { OPPONENTS_DEFEATED, OPPONENTS_FLED, OPPONENTS_DEFEATED_AND_FLED, PLAYER_DEFEATED }`.
- Produzir `data class GuardianOpponentProposal(val opponentId: String, val narrative: CombatOpponentNarrative, val stats: CharacterState, val weapon: WeaponProfile)` e `data class GuardianEncounterProposal(val opponents: List<GuardianOpponentProposal>, val moraleLeaderId: String? = null)`; exigir 1–8 elementos, IDs únicos e líder nulo ou pertencente à lista.
- `GuardianClient.narrate(..., encounterContext: List<CombatOpponentNarrative>? = null)` transporta perfis aprovados distintos após o combate.
- `GuardianRuleResolution.encounterNarratives: List<CombatOpponentNarrative>` preserva todos os perfis ao retornar a narrativa.
- `GameAction.BeginCombat(val opponents: List<CombatOpponentState>, val moraleLeaderId: String? = null)`.
- Produzir `internal fun combatEncounterSchema(): JsonObject` para testar a validação estrutural da lista de encontros no contrato do servidor.
- Para manter os commits intermediários compiláveis, preservar adapters internos de oponente singular para consumidores atuais de UI/persistência até a Task 3; o novo fluxo Guardian e o estado canônico usam a lista completa. Migrar esses consumidores e remover os adapters na Task 3.

- [ ] Escrever `GuardianClientTest.beginCombatParsesTwoDistinctOpponents`, `GuardianClientTest.rejectsDuplicateOpponentIds`, `GuardianRuleResolverTest.unacceptedEncounterDoesNotStartCombat`, `GuardianRuleResolverTest.acceptedEncounterBeginsCombatWithEveryOpponent` e `ServerTest.encounterSchemaRequiresCompleteOpponentList`; afirmar IDs, dados, armas e narrativas independentes.

```kotlin
assertEquals(setOf("cultist-a", "cultist-b"), proposal.opponents.map { it.opponentId }.toSet())
assertNull(unacceptedResult.state.campaign.combat) // nenhum combate antes da confirmação
assertFailsWith<IllegalArgumentException> { GuardianEncounterProposal(listOf(firstOpponent, firstOpponent.copy(opponentId = firstOpponent.opponentId))) }
```

- [ ] Rodar a Android Build por `workflow_dispatch` na branch de feature para confirmar a falha dos testes antes da mudança de contrato; o ambiente local não tem Android SDK/Gradle.
- [ ] Implementar os tipos, schema e conversão GuardianRuleResolver; proposta com ID duplicado, lista vazia ou ficha inválida vira pedido inválido sem iniciar combate e sem perder a narração.
- [ ] Atualizar `GuardianContextBuilder` para informar todos os oponentes ativos/derrotados com perfil narrativo e fatos públicos necessários, sem expor atributos ocultos não autorizados.
- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` após a implementação; confirmar Android Build verde.
- [ ] Commit atômico: `feat: model combat opponents individually`.

### Task 2: Resolver de rodadas Cairn 2e

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameAction.kt`
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/rules/CombatRules.kt`, `RulesEngine.kt`
- Test: `app/src/test/kotlin/com/vanish994/cairnsolo/game/CombatAuthorityContractTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/game/GameActionResolverTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/rules/CombatRulesTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/rules/RulesEngineTest.kt`

**Interfaces:**
- Produzir `enum class DamageRecipient { PLAYER_CHARACTER, OPPONENT }`; `RulesEngine.applyDamage(state: CharacterState, rawDamage: Int, recipient: DamageRecipient = DamageRecipient.PLAYER_CHARACTER): GameResult` mantém comportamento legado para PCs e aplica a regra NPC sem Scar a 0 HP.
- `CombatRules.attack(attacker: CharacterState, target: CharacterState, weapon: WeaponProfile? = null, mode: AttackMode = AttackMode.NORMAL, recipient: DamageRecipient = DamageRecipient.PLAYER_CHARACTER): AttackResult`; `GameActionResolver` usa `DamageRecipient.OPPONENT` quando o jogador ataca um inimigo.
- `GameAction.CombatAttack(val targetOpponentId: String, val weapon: WeaponProfile? = null)`; rejeitar alvo inexistente ou derrotado.
- Produzir `data class CombatAttackSource(val id: String, val weapon: WeaponProfile)` e `data class CombatGroupAttackResult(val target: CharacterState, val damageRolls: Map<String, Int>, val events: List<RuleEvent>)`.
- `CombatRules.attackGroup(target: CharacterState, attackers: List<CombatAttackSource>): CombatGroupAttackResult` rola cada dado, usa somente o maior resultado e aplica Armor uma vez.
- Produzir `data class EnemyAttackRoll(val opponentId: String, val damageRolled: Int)` e `data class EnemyMoraleOutcome(val opponentId: String, val trigger: CombatMoraleTrigger, val roll: Int, val attributeValue: Int, val fled: Boolean)`; atualizar `GameEvent.CombatAttackResolved` para conter `targetOpponentId: String?`, `damageDealtByPlayer: DamageResolved?`, `enemyAttackRolls: List<EnemyAttackRoll>`, `damageDealtByEnemies: DamageResolved?`, `moraleOutcomes: List<EnemyMoraleOutcome>`, `defeatedOpponentIds: List<String>`, `fledOpponentIds: List<String>` e `playerCanAct: Boolean`.
- Atualizar `GameEvent.CombatStarted(opponentIds: List<String>, round: Int, playerCanAct: Boolean)` e `GameEvent.CombatEnded(opponentIds: List<String>, reason: CombatEndReason)` para distinguir inimigos derrotados, fuga do grupo e derrota do jogador.

- [ ] Escrever `CombatRulesTest.groupAttackRollsEveryEnemyAndAppliesOnlyHighestDamage` e testes de Armor; escrever `RulesEngineTest.opponentAtZeroHpGetsNoScar` e testes de dano excedente/STR save para oponente.
- [ ] Escrever `GameActionResolverTest.multiOpponentCombatStillUsesDexSaveOnFirstRound`, `defeatedOpponentBeforeEnemyPhaseDoesNotAct`, `twoEnemyGroupChecksBothMoraleThresholdsInOrder`, `threeOpponentGroupChecksHalfAtTwoDefeats`, `leaderDefeatedUsesSurvivorWil` e testes para alvo escolhido, WIL de inimigo solitário a 0 HP e fim somente após todos derrotados/fugitivos.

```kotlin
assertEquals(listOf(4, 5), groupAttack.damageRolls.values.sorted())
assertEquals(3, groupAttack.events.filterIsInstance<RuleEvent.DamageApplied>().single().hpDamage) // d6=4, d8=5; Armor 2
assertEquals(listOf(FIRST_CASUALTY, HALF_GROUP), moraleOutcomes.map { it.trigger })
assertEquals(CombatOpponentStatus.FLED, survivingOpponent.status) // WIL 10: roll 4 passa e roll 18 falha
```

- [ ] Rodar a Android Build por `workflow_dispatch` na branch de feature; confirmar falha dos testes antes da implementação.
- [ ] Implementar a fase inimiga somente com oponentes ativos ao início da fase. Seus ataques são simultâneos; um inimigo derrotado antes dessa fase não age.
- [ ] Implementar dano NPC: a 0 HP não há Scar nem derrota automática; dano excedente reduz STR e exige save; falha marca `CombatOpponentStatus.DEFEATED` sem converter a decisão narrativa “morto ou incapacitado” em atributo PC `dead`. Manter Scars e morte de PCs inalteradas.
- [ ] Implementar WIL d20: inimigo solitário testa ao chegar a 0 HP; grupo testa na primeira baixa e na metade do tamanho inicial. Cada oponente sobrevivente rola seu WIL, exceto quando um líder ativo fornece o atributo substituto; se o líder não estiver ativo, voltar aos saves individuais.
- [ ] Processar limiares em ordem FIRST_CASUALTY depois HALF_GROUP; se a primeira checagem fizer todos fugirem, não há sobreviventes para a próxima. Marcar cada limiar uma vez e considerar baixa somente após derrota, não ao tocar 0 HP.
- [ ] Testar morte do jogador, crítico de NPC, fuga, sucesso/falha de WIL e dano agrupado; confirmar PASS na Android Build por `workflow_dispatch`.
- [ ] Commit atômico: `feat: resolve multi-opponent Cairn combat`.

### Task 3: Persistência, UI e compatibilidade de saves

**Files:**
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameStatePersistenceCodec.kt`
- Modify: `app/src/main/kotlin/com/vanish994/cairnsolo/MainActivity.kt`, `GuardianRuleResolver.kt`
- Test: `app/src/test/kotlin/com/vanish994/cairnsolo/game/CombatPersistenceTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/game/CombatAuthorityContractTest.kt`, `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianCombatContextTest.kt`

**Interfaces:**
- `GameAction.CombatAttack(targetOpponentId, weapon)` chega à UI por `onCombatAttack: (String, WeaponProfile?) -> Unit`.
- `CombatCard` recebe `CombatState` e `onAttack(targetOpponentId: String, weapon: WeaponProfile?)`.
- O codec grava contador e campos indexados por oponente, mas decodifica os campos antigos `combatOpponent*` como lista de um.

- [ ] Escrever `CombatPersistenceTest.multipleOpponentsRoundTrip`, `legacySingleOpponentSaveLoadsAsSingleEntry` e `corruptOpponentWeaponDoesNotDiscardWholeCampaign`; afirmar HP, STR, Armor, arma, narrativa, status, rodada e moral.

```kotlin
assertEquals(1, legacyLoaded.campaign.combat?.opponents?.size)
assertEquals(setOf("cultist-a", "cultist-b"), pluralLoaded.campaign.combat?.opponents?.map { it.id }?.toSet())
assertNotNull(campaignLoadedWithOneCorruptOpponentWeapon) // restante da campanha continua carregável
```

- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` com os testes de persistência; confirmar a falha esperada no round-trip plural antes da implementação.
- [ ] Implementar encoding/decoding plural com defaults seguros e sem perder adversários por erro parcial de um campo.
- [ ] Atualizar `CombatCard` para listar nome/HP/Armor/status de cada cultista, permitir selecionar um alvo e desabilitar nova ação até continuar a narração da rodada.
- [ ] Atualizar `GuardianRuleResolver.summarize` e o resumo enviado ao Guardian para usar IDs/narrativas/resultados separados por oponente, sem delegar resultado mecânico.
- [ ] Rodar `gh workflow run android.yml --ref <feature-branch>` e conferir manualmente APK com dois adversários e carregar save legado.
- [ ] Commit atômico: `feat: persist and display multi-enemy encounters`.

### Task 4: Integração

**Files:** `docs/STATUS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE-MAP.md`, `docs/CAIRN-2E-RULE-MATRIX.md` — atualizar somente após validação.

**Branch:** `feature/multi-enemy-combat` (criar a partir da `main` após mesclar o PR de documentação; um PR focado).

- [ ] Abrir PR para `main`; exigir Android Build verde (unit tests, Guardian server tests, integração HTTP e APK release) no HEAD atual.
- [ ] Aceitação manual: proposta de dois cultistas mostra dois perfis; o jogador escolhe alvo; ataque inimigo aplica regra de grupo; moral/fuga seguem Cairn 2e; save/reload mantém ambos.
- [ ] Documentar limitações remanescentes sem fechar gates não concluídos; merge somente após CI verde.
