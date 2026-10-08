# Combate jogável — Plano de implementação

> **Para agentes de implementação:** seguir as tarefas na ordem, com testes primeiro e commits por incremento. Execução nativa aprovada pelo usuário.

**Objetivo:** fechar o próximo marco de combate jogável: o Guardian propõe um encontro, o jogador confirma o oponente, o Rules Engine resolve o procedimento e o Guardian narra somente os fatos autorizados.

**Arquitetura:** o servidor retorna um `BEGIN_COMBAT` estruturado como proposta, nunca como mutação. A UI apresenta aparência, comportamento, intenção, contexto e perfil mecânico do oponente; só o aceite explícito cria `GameAction.BeginCombat`. O resolver produz `GameResult`; um resumo controlado desse resultado e o contexto filtrado do combate voltam ao Guardian para narração.

**Stack:** Kotlin/JVM 17, Android/Jetpack Compose/Material 3, Guardian server Kotlin/Gson, testes Kotlin/JUnit e GitHub Actions.

**Especificação:** `docs/DEVELOPMENT-GUIDE.md` (Fase B); `docs/ARCHITECTURE-MAP.md` (§§ 4–5, 8–10); `docs/ROADMAP.md` (Gate de combate); e o contrato de jogo enviado pelo usuário em `pasted_content.txt`.

## Escopo desta etapa

- A definição de pronto desta etapa é **combate jogável de ponta a ponta**, não a implementação integral de Cairn 2e nem a entrega final do APK.
- O repositório atualmente identifica combate como Gate 6 e APK como Gate 8; o anexo chama combate jogável de Gate 8. Para evitar renumerar gates sem necessidade, este trabalho usa “marco de combate jogável” e registra a divergência na documentação.
- Após este marco, a ordem de produto aprovada é: exploração real → inimigos/NPCs integrados → dungeon jogável → campanha/mundo persistentemente jogáveis → conteúdo adicional de Cairn 2e.

## Contrato de autoridade aprovado

| Papel | Responsabilidade e limite |
|---|---|
| Guardian | Pode propor aparência, comportamento, intenção, contexto e perfil do oponente; não inicia combate nem declara resultados mecânicos. |
| Jogador | Aceita ou recusa a proposta antes de qualquer `BeginCombat`; recusar não altera HP, turno ou estado de combate. |
| Rules Engine | Autoridade única sobre iniciativa, ações, ataques, dano, Armor, HP, condições, morte e demais consequências; emite `GameResult`. |
| Guardian após a resolução | Recebe contexto filtrado e apenas fatos derivados de `GameResult`; transforma esses fatos em narrativa, sem alterar o estado. |
| Integração | PR direcionado a `main` → CI verde → merge em `main`. |

## Restrições globais

- Cairn Second Edition é a referência normativa; não inventar procedimentos que não estejam no escopo decidido.
- Toda mudança mecânica passa por `GameActionResolver`; a UI não chama `RulesEngine` diretamente.
- A proposta do Guardian não é uma ação aceita: só o botão explícito de aceite permite chamar `GameAction.BeginCombat`.
- O Guardian recebe apenas `GuardianContext` filtrado, um resumo de resultado gerado a partir dos eventos de `GameResult` e, quando aplicável, o perfil narrativo do oponente já aprovado pelo jogador; nunca recebe `GameState` bruto nem texto de resultado vindo do modelo/jogador.
- Saves antigos continuam carregáveis; campos narrativos novos têm defaults de migração.
- Sem novas dependências de produção; `org.json` e Kotlin/JUnit 5 são usados apenas nos classpaths de teste. Commits atômicos em branch e merge apenas após CI verde.

## Foco de revisão

1. Proposta `BEGIN_COMBAT` ausente/malformada ou com dado de dano não suportado: preservar a narração, expor erro legível, rejeitar sem iniciar combate nem mutar HP/turno.
2. Jogador recusa o encontro: `campaign.combat` permanece `null` e nenhum procedimento de iniciativa/ataque é executado.
3. Falha no save inicial de DEX: o oponente age exatamente uma vez; dano e estado vêm do resolver; perfil narrativo do oponente permanece ligado ao combate.
4. Combate ativo: bloquear continuação/investigação, descanso, dano genérico (`ApplyDamage`/`DAMAGE`), `EndCombat` sem procedimento de fuga, viagem, dungeon e compra; permitir declarar intenção ao Guardian e ataques válidos.
5. Resultado de vitória, morte ou dano crítico: Guardian recebe o resumo autorizado correspondente, sem poder produzir ou aplicar mecânica.
6. Save/load em combate e migração de save antigo sem perfil narrativo do oponente.
7. Requests comuns incompletos: não exibir como executáveis; o jogador pode descartar qualquer solicitação pendente sem mutação mecânica.

---

### Tarefa 1: Proposta estruturada e contexto filtrado do Guardian

**Arquivos:**
- Modificar: `guardian-server/src/main/kotlin/com/vanish994/cairnsolo/guardian/Server.kt`
- Modificar: `app/src/main/kotlin/com/vanish994/cairnsolo/guardian/GuardianClient.kt`
- Modificar: `app/src/main/kotlin/com/vanish994/cairnsolo/guardian/GuardianContext.kt`
- Testar/criar: `app/src/test/kotlin/com/vanish994/cairnsolo/guardian/GuardianClientTest.kt` e `GuardianCombatContextTest.kt`
- Testar/criar: `guardian-server/src/test/kotlin/com/vanish994/cairnsolo/guardian/ServerTest.kt`
- Modificar: `guardian-server/build.gradle.kts` para runner JUnit 5 somente em testes

**Interfaces produzidas:**
- Em `game/GameState.kt`: `CombatOpponentNarrative(name, appearance, behavior, intent, context)`.
- Em `guardian/GuardianClient.kt`: `GuardianEncounterProposal(opponentId, narrative, stats: CharacterState, weapon: WeaponProfile)` e `GuardianRuleRequest.encounter: GuardianEncounterProposal?`.
- O JSON de `ruleRequest` usa `type=BEGIN_COMBAT` e `encounter={opponentId,narrative,stats,weapon}`. `narrative` contém os cinco campos da interface; `stats` contém STR/DEX/WIL, HP máximo/atual e Armor; `weapon` contém id, dado de dano, `blast` e `ranged`.
- `GuardianClient.narrate(state, playerIntent, ruleResult: String? = null, encounterContext: CombatOpponentNarrative? = null)` transporta `ruleResult` somente quando produzido pelo app a partir de eventos do `GameResult`; o perfil aprovado vai em campo separado para continuidade narrativa.
- `GuardianCombatContext` expõe identidade/narrativa, HP/HP máximo, Armor, arma, rodada e `playerCanAct`; não expõe campos internos de persistência.

- [ ] **Passo 1: escrever testes de contrato que falham primeiro**
  - `parseBeginCombatProposalRetainsNarrativeAndStats` verifica os campos narrativos, atributos, HP, Armor e arma.
  - `invalidBeginCombatProposalIsRetainedForVisibleRejection` e `unsupportedWeaponDamageIsRetainedForVisibleRejection` verificam narração preservada, request não executável e erro observável pela validação.
  - `ServerTest.kt` verifica patterns de campos nonblank, que HP acima de maxHp vira request incompleta sem apagar a narração e que HP válido mantém a proposta.
  - `guardianReceivesOpponentNarrativeAndOnlyRequiredMechanicalFacts` verifica a serialização do oponente ativo e ausência de atributos ocultos.
  - `authorizedRuleResultIsSerializedSeparatelyFromPlayerIntent` verifica que `ruleResult` e `encounterContext` são campos separados da intenção.
- [ ] **Passo 2: executar testes no workflow Android**
  - Antes da implementação, os novos testes devem falhar por contrato ausente, não por sintaxe.
- [ ] **Passo 3: implementar DTO/parser e schema/prompt do servidor**
  - O prompt permite propor perfil e contexto do adversário, mas proíbe iniciar o combate, narrar resultados ou inventar consequências.
  - Preservar pedidos e campos existentes; schema/parser exigem os campos condicionais de cada request, strings do oponente não podem ser vazias/só espaços, e HP acima de maxHp vira request incompleta para erro visível sem apagar a narração.
- [ ] **Passo 4: ampliar o contexto público filtrado**
  - Serializar perfil e estado atual do oponente para o Guardian depois do início.
  - Marcar o resumo mecânico como autoritativo somente quando vier do app; não construir `ruleResult` a partir de texto livre.
- [ ] **Passo 5: rodar testes e commit**
  - Esperado: testes Android unitários e `:guardian-server:test` passam.
  - Commit: `feat: model player-confirmed combat encounters`.

---

### Tarefa 2: Estado de combate e resolução central

**Arquivos:**
- Modificar: `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameState.kt`
- Modificar: `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameAction.kt`
- Modificar: `app/src/main/kotlin/com/vanish994/cairnsolo/rules/CombatRules.kt`
- Modificar: `app/src/main/kotlin/com/vanish994/cairnsolo/guardian/GuardianRuleResolver.kt`
- Modificar: `app/src/test/kotlin/com/vanish994/cairnsolo/game/GameActionResolverTest.kt`

**Interfaces produzidas:**
- `CombatState` armazena `opponentNarrative: CombatOpponentNarrative`, com default compatível para chamadas antigas.
- `GameAction.BeginCombat(opponentId, opponent, opponentWeapon, opponentNarrative)` mantém defaults para fixtures e chamadas existentes.
- `GuardianRuleResolver.resolve(state, request)` converte a proposta validada em `BeginCombat`; nenhuma conversão ocorre antes do aceite do jogador.
- `GuardianRuleResolver.validationError(state, request)` recusa SAVE/DAMAGE/FATIGUE incompletos e DAMAGE/REST/BEGIN_COMBAT incompatíveis com combate ativo antes de criar um card executável.
- `isSupportedWeaponDamageExpression` define uma gramática compartilhada para as armas do combate; dano textual de armadilha não é exibido como arma e não pode ser rolado pelo motor.
- `GameActionResolver` reconstrói armas do jogador a partir do inventário e valida a arma adversária antes da iniciativa; o codec migra dano inválido persistido para `d4`.
- `GuardianRuleResolver.resolve(state, action)` retorna `GuardianRuleResolution(state, resultText, gameResult, encounterNarrative)`; o campo narrativo conserva o perfil confirmado mesmo quando vitória/morte encerra o combate.

- [ ] **Passo 1: escrever testes de resolver**
  - `beginCombatStoresConfirmedOpponentNarrative` verifica o perfil após iniciativa bem-sucedida.
  - `failedDexInitiativePreservesProfileAndAppliesExactlyOneOpponentAttack` verifica perfil e um único contra-ataque.
  - `activeCombatBlocksExplorationAndRestActions` verifica também `ApplyDamage` e `EndCombat`, além de `ExploreContinue`, `ExploreInvestigate`, `ExploreRest`, `Rest`, `AddItem`, `RemoveItem`, `StartTravel`, `DungeonAct` e `Purchase`, afirmando a mensagem exata do guard.
  - `activeCombatAllowsGuardianIntentAndCombatAttack` protege agência narrativa e ataque válido.
- [ ] **Passo 2: executar os testes para confirmar falha**
  - `gradle testDebugUnitTest`; esperado: falha de compilação/assertion nos contratos ainda não implementados.
- [ ] **Passo 3: persistir narrativa no domínio sem alterar regras de ataque**
  - Copiar `opponentNarrative` para os dois caminhos de iniciativa e preservá-lo em `current.copy` nas rodadas.
  - A iniciativa e os ataques continuam exclusivamente em `CombatRules`/`GameActionResolver`; apenas arma possuída ou ataque desarmado é aceito, sem modo de ataque escolhido pelo chamador.
- [ ] **Passo 4: bloquear ações incompatíveis no resolver**
  - Rejeitar as ações listadas nos testes quando `campaign.combat != null`; não tocar em resultados mecânicos.
- [ ] **Passo 5: resumir eventos de combate com fatos exatos**
  - `GuardianRuleResolver` transforma `CombatStarted`, `CombatAttackResolved` e `CombatEnded` em um resumo estável dos valores retornados, separando `damageDealtByPlayer` e `damageDealtByOpponent` e incluindo dano bruto, Armor absorvida, HP perdido, crítico, morte e cicatriz somente se presentes nos eventos.
- [ ] **Passo 6: validar testes e commit**
  - Esperado: os testes novos e `combatFlowUsesDexInitiativeThenResolvesAttackAndEndsOnOpponentZeroHp` / `failedFirstDexSaveLetsEnemyActAndAdvancesToRoundTwo` passam.
  - Commit: `feat: resolve confirmed combat through the rules engine`.

---

### Tarefa 3: Confirmação do jogador, combate na UI e narração

**Arquivos:**
- Modificar: `app/src/main/kotlin/com/vanish994/cairnsolo/MainActivity.kt`
- Usar os testes da Tarefa 2; não adicionar dependência de teste Compose.

**Interfaces produzidas:**
- `EncounterProposalCard(proposal, enabled, onAccept, onReject)` mostra identidade, aparência, comportamento, intenção, contexto, estatísticas e arma.
- `CombatCard(combat, weapons, enabled, onAttack: (WeaponProfile?) -> Unit)` mostra o oponente confirmado, estado atual e ações válidas.
- `RollRequestCard` oferece descarte sem efeitos, como fallback para qualquer solicitação que não possa ser resolvida.

- [ ] **Passo 1: apresentar proposta sem iniciar combate**
  - Para `BEGIN_COMBAT`, renderizar `EncounterProposalCard` no lugar de `RollRequestCard`.
  - “Aceitar encontro” chama o resolver; “Recusar” limpa o pedido pendente sem chamar `GameActionResolver` nem salvar mudança mecânica.
- [ ] **Passo 2: conectar o combate ativo**
  - Mostrar `CombatCard` somente enquanto `campaign.combat != null`; desabilitar controles de exploração/descanso nesse estado.
  - Ofertar apenas armas cujo dano use dados suportados; armadilhas com dano textual não entram na lista de ataques.
  - Ações de ataque usam `GameAction.CombatAttack` via `GuardianRuleResolver.resolve(state, action)` e armas são verificadas no inventário; não expor modo de ataque escolhido pelo caller nem botão de fuga/encerramento sem regra normativa implementada.
- [ ] **Passo 3: devolver apenas o resultado autorizado ao Guardian**
  - Após qualquer ação mecânica, apresentar os eventos retornados pelo resolver; ao continuar, chamar `narrate` com o resumo `ruleResult` derivado daquele `GameResult`, o perfil narrativo aprovado em `encounterContext` e o comando interno de continuação.
  - O Guardian atualiza a narrativa; não altera HP, Armor, condições, inventário ou estado de combate.
- [ ] **Passo 4: validar visualmente**
  - No APK do workflow, verificar proposta → recusa sem combate; proposta → aceite → iniciativa; ataque → consequência do engine → narração do Guardian; término autoritativo remove o card.
  - Esperado: valores apresentados coincidem com `GameState` e eventos.
- [ ] **Passo 5: commit**
  - Commit: `feat: add player-confirmed combat flow to exploration`.

---

### Tarefa 4: Persistência, bloqueios, documentação e CI

**Arquivos:**
- Modificar: `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameStatePersistenceCodec.kt`
- Criar/modificar: `app/src/test/kotlin/com/vanish994/cairnsolo/game/CombatPersistenceTest.kt`
- Modificar: `docs/gates/GATE-6.md`, `docs/STATUS.md`, `docs/ROADMAP.md` e, se necessário, `docs/ARCHITECTURE-MAP.md`

**Interfaces produzidas:**
- Saves guardam os cinco campos de `CombatOpponentNarrative` e as flags `blast`/`ranged` da arma; saves antigos sem esses campos carregam um perfil default com `name=opponentId`, detalhes vazios e flags falsas.

- [ ] **Passo 1: escrever `activeCombatRoundTripsOpponentNarrativeAndMechanicalState`**
  - Round-trip mantém identidade, narrativa, stats, HP, Armor, arma, rodada e `playerCanAct`.
  - `legacySaveWithoutOpponentNarrativeUsesSafeDefault` valida migração de saves antigos.
- [ ] **Passo 2: executar testes para confirmar round-trip e bloqueios**
  - `gradle testDebugUnitTest`; esperado: casos novos e testes existentes passam.
- [ ] **Passo 3: atualizar documentação de produto**
  - Marcar apenas critérios de combate comprovados; registrar que o projeto ainda não representa Cairn 2e completo.
  - Documentar a sequência posterior aprovada: exploração real → inimigos/NPCs → dungeon → campanha/mundo persistente → conteúdo adicional.
  - Não alterar a numeração dos Gates existentes; explicar a divergência Gate 6/Gate 8 para sincronização posterior.
- [ ] **Passo 4: validar PR para `main`**
  - Abrir PR; exigir Android Build verde. Verificar testes unitários, Guardian server, integração HTTP e APK release conforme o workflow. Job pulado não conta como validação.
  - Mesclar por squash somente após CI verde; remover a branch remota depois do merge.

## Fora deste PR

Completar todos os procedimentos de Cairn 2e, exploração real, catálogo de inimigos/NPCs, dungeon, campanha/mundo persistente e distribuição final do APK são marcos posteriores independentes. Nenhum deles será declarado concluído por este incremento de combate.
