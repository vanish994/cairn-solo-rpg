# Guia de continuidade do desenvolvimento

Este documento é o ponto de entrada para qualquer pessoa ou agente que continue o desenvolvimento do Cairn Solo RPG.

## 1. Visão do produto

O projeto é um RPG solo de fantasia sombria para Android, inspirado em Cairn 2e e em experiências de chat como Tales RPG/AI Dungeon.

A divisão fundamental é:

- **Jogador/UI:** declara uma intenção em linguagem natural ou aciona uma ação explícita.
- **GameActionResolver:** converte a ação em uma transição autorizada.
- **Rules Engine:** resolve toda mecânica determinística.
- **GameState:** guarda o estado atual da campanha.
- **Guardian:** interpreta contexto controlado, narra consequências e propõe intenções; não altera mecânica.
- **Persistência:** salva e restaura o GameState sem depender da IA.

> Se uma mudança de HP, atributo, inventário, ouro, Fadiga, combate, facção, Growth, cânone ou progressão puder ocorrer sem passar pelo domínio, a implementação está incorreta.

## 2. Ordem obrigatória antes de codar

1. Ler `README.md`.
2. Ler `docs/STATUS.md`.
3. Ler este arquivo.
4. Consultar o mapa em `docs/ARCHITECTURE-MAP.md`.
5. Ler o Gate relacionado em `docs/gates/`.
6. Ler os ADRs relacionados em `docs/decisions/`.
7. Inspecionar os arquivos existentes antes de criar novas abstrações.
8. Confirmar qual é o estado atual da branch e do CI.

Não assumir que o roadmap antigo está perfeitamente sincronizado com o código. O código, os testes e o último workflow bem-sucedido devem ser comparados antes de declarar um Gate concluído.

## 3. Regras de arquitetura que não podem ser quebradas

### 3.1 Autoridade mecânica

O fluxo permitido é:

```text
Intent / botão
    ↓
GameAction
    ↓
GameActionResolver
    ↓
RulesEngine ou módulo de domínio
    ↓
GameResult(newState, events)
    ↓
Persistência / feedback / contexto do Guardian
```

A UI não deve chamar `RulesEngine` diretamente. O Guardian não deve retornar um novo `GameState` nem valores mecânicos livres.

### 3.2 IA como narrador controlado

O Guardian pode:

- interpretar a intenção do jogador;
- descrever lugares, NPCs, ameaças e consequências já resolvidas;
- pedir uma rolagem ou propor uma intenção estruturada;
- propor evidências de Growth ou atualizações de cânone para validação.

O Guardian não pode:

- decidir o resultado de uma rolagem;
- definir dano, HP, Armor, atributos ou inventário;
- criar uma habilidade de Growth diretamente;
- inventar que um NPC, facção, item ou local já é canônico;
- mudar o mundo sem uma operação determinística validada.

### 3.3 Estado como fonte da verdade

`GameState` e os objetos persistidos são a fonte da verdade. A narrativa exibida pode ser resumida, regenerada ou perdida sem autorizar uma alteração mecânica.

## 4. Como implementar uma nova regra

Use este checklist:

1. Identifique a regra na matriz `docs/CAIRN-2E-RULE-MATRIX.md`.
2. Registre qualquer ambiguidade normativa antes de escolher o comportamento.
3. Crie ou ajuste uma operação pura no módulo de `rules/`.
4. Receba RNG por injeção; não use aleatoriedade global dentro do domínio.
5. Retorne `GameResult` com novo estado e eventos suficientes para feedback e narrativa.
6. Adicione testes de sucesso, falha, limites e estados inválidos.
7. Exponha a regra por `GameAction` e `GameActionResolver`.
8. Adicione teste do fluxo completo da ação.
9. Atualize persistência quando houver novos campos.
10. Atualize o Guardian Context somente com o DTO controlado necessário.
11. Atualize `STATUS.md`, a matriz e/ou o Gate correspondente.

### Exemplo de ação correta

```kotlin
data class ApplyDamage(val amount: Int) : GameAction

// GameActionResolver
val result = rules.applyDamage(state.campaign.rules, action.amount)
return GameResult(
    state.withRules(result.newState),
    result.events.toGameEvents(result.newState)
)
```

Evite colocar regra dentro de `MainActivity`, `GuardianClient` ou do servidor HTTP.

## 5. Como implementar uma nova intenção do jogador

1. A UI registra apenas o texto e o histórico visual.
2. O texto é enviado ao Guardian junto com o `GuardianContext` filtrado.
3. A resposta estruturada deve indicar uma intenção, uma necessidade de regra ou uma narrativa.
4. O cliente valida o contrato JSON.
5. Uma `GameAction` é criada somente quando o tipo de ação for conhecido.
6. O resolver executa a mecânica.
7. O resultado é persistido.
8. O Guardian recebe o resultado autorizado para narrar a consequência.
9. A UI acrescenta a fala do jogador e a resposta do Guardian ao histórico persistente.

Uma frase do jogador nunca deve ser concatenada como se fosse uma regra resolvida.

## 6. Como trabalhar no Guardian

### Contexto

O contexto enviado deve ser derivado de `GuardianContext`/`MJContext`, nunca do banco bruto. Incluir apenas:

- identidade e estado relevante do personagem;
- cena atual;
- fatos canônicos conhecidos;
- histórico relevante e limitado;
- quests, NPCs e facções conhecidos;
- ações disponíveis;
- rolagens ou propostas de regra pendentes;
- `campaignSeed` e sementes narrativas quando necessário.

### Contrato

Toda alteração do contrato precisa ser atualizada nos dois lados:

- cliente Android: `GuardianClient`, `GuardianContext` e resolvedor;
- backend: `guardian-server/.../Server.kt`;
- testes de JSON e integração do endpoint `/guardian`.

O servidor deve aplicar schema/validação e nunca aceitar uma mutação mecânica arbitrária como fato.

### Narrativa de abertura

A abertura deve ser gerada a partir do estado, mundo e `campaignSeed`. Não reintroduzir texto fixo como prólogo obrigatório. O seed é uma âncora de diversidade, não uma autorização para contradizer o domínio.

## 7. UI e experiência de chat

A tela principal deve continuar com:

- histórico rolável persistente de jogador e Guardian;
- caixa fixa de entrada;
- botão pequeno de envio ao lado do campo;
- cards mecânicos somente quando houver rolagem, combate ou Growth pendente;
- nenhuma ação narrativa redundante como `Continuar` ou `Investigar` quando o chat já cobre a intenção.

Ações mecânicas explícitas podem permanecer quando têm semântica clara, por exemplo:

- rolar teste solicitado;
- aceitar/recusar Growth validado;
- atacar durante combate;
- descansar de forma segura, com a regra visível.

Não duplicar a regra na UI para "dar sensação" de resposta rápida.

## 8. Ordem recomendada para a próxima fase

### Fase A — fechar o ciclo de campanha

- verificar todos os caminhos de `GameActionResolver`;
- eliminar chamadas diretas do Rules Engine pela UI;
- consolidar `CampaignState` e `CampaignRuntime` se ambos representam a mesma responsabilidade;
- garantir round-trip de todos os módulos no `GameStatePersistenceCodec`;
- garantir que o histórico do Guardian não seja perdido ao salvar/carregar.

### Fase B — combate narrativo-mecânico

- conectar intenção do Guardian a `BeginCombat`;
- exibir card/modo de combate somente quando `campaign.combat != null`;
- resolver ataque, contra-ataque, dano, Critical Damage, Scars, morte e encerramento;
- integrar reação, moral e fuga;
- impedir ações de exploração enquanto o combate estiver ativo;
- narrar apenas o `GameResult` já resolvido.

### Fase C — Growth, cânone e história

- mostrar propostas de Growth somente depois da validação do domínio;
- permitir aceitar/recusar pelo jogador;
- persistir evidência, proposta, decisão e efeito aplicado;
- separar fato confirmado, proposta pendente e detalhe narrativo não canônico.

### Fase D — E2E e release

Executar o cenário completo:

```text
criar personagem
→ gerar campanha com seed único
→ abertura inédita
→ intenção de exploração
→ NPC/rumor
→ viagem
→ acampamento/descanso
→ dungeon
→ reação
→ combate
→ moral/fuga
→ hireling/pagamento
→ consequência
→ evidência de Growth
→ proposta validada
→ aceitar Growth
→ salvar
→ fechar
→ reabrir
→ continuar
```

## 9. Validação local e CI

O workflow oficial está em `.github/workflows/android.yml` e executa:

1. `gradle testDebugUnitTest`;
2. `gradle :guardian-server:test`;
3. health check do backend Render;
4. teste de integração HTTP `/guardian`;
5. `gradle :app:assembleRelease`;
6. verificação e upload do APK.

Neste ambiente, confirme primeiro se o Gradle wrapper ou o comando `gradle` existe. Se não existir, não declare os testes locais como executados; use o GitHub Actions como validação oficial.

Antes do commit:

```bash
git status --short --branch
git diff --check
git diff --stat
```

Commits recomendados:

```text
feat: adiciona fluxo de combate
fix: impede descanso durante combate
test: cobre round-trip de magia
refactor: centraliza transição de campanha
docs: atualiza mapa de arquitetura
```

Depois do push:

```bash
git push origin <branch>
gh run list --limit 5
gh run view <run-id> --log-failed
```

## 10. Antes de considerar uma tarefa concluída

- [ ] regra normativa identificada;
- [ ] domínio determinístico implementado;
- [ ] RNG controlável nos testes;
- [ ] ação exposta por `GameActionResolver`;
- [ ] eventos e feedback atualizados;
- [ ] persistência atualizada;
- [ ] Guardian Context filtrado;
- [ ] UI não duplica regra;
- [ ] testes adicionados ou atualizados;
- [ ] CI executado com sucesso;
- [ ] `STATUS.md`, matriz ou ADR atualizados;
- [ ] commit e branch registrados.

## 11. Arquivos de referência rápida

| Responsabilidade | Arquivo principal |
|---|---|
| Entrada visual Android | `app/src/main/kotlin/com/vanish994/cairnsolo/MainActivity.kt` |
| Ações e eventos | `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameAction.kt` |
| Estado persistível | `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameState.kt` |
| Codec de save/load | `app/src/main/kotlin/com/vanish994/cairnsolo/game/GameStatePersistenceCodec.kt` |
| Regras base | `app/src/main/kotlin/com/vanish994/cairnsolo/rules/RulesEngine.kt` |
| Combate | `app/src/main/kotlin/com/vanish994/cairnsolo/rules/CombatRules.kt` |
| Dungeon | `app/src/main/kotlin/com/vanish994/cairnsolo/rules/DungeonRules.kt` |
| Wilderness | `app/src/main/kotlin/com/vanish994/cairnsolo/rules/WildernessRules.kt` |
| Magia | `app/src/main/kotlin/com/vanish994/cairnsolo/rules/MagicRules.kt` |
| Warden | `app/src/main/kotlin/com/vanish994/cairnsolo/rules/WardenRules.kt` |
| Growth/histórico/cânone | `game/Growth.kt`, `game/CanonResolver.kt`, `game/WorldCanon.kt` |
| Contexto controlado | `guardian/GuardianContext.kt`, `game/MJContext.kt` |
| Cliente Guardian | `guardian/GuardianClient.kt` |
| Backend Guardian | `guardian-server/src/main/kotlin/com/vanish994/cairnsolo/guardian/Server.kt` |
| Feedback | `feedback/Feedback.kt` |
| Testes de fluxo | `app/src/test/.../game/GameActionResolverTest.kt` |
