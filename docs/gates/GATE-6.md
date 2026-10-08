# Gate 6 — Combate

## Objetivo

Adicionar combate determinístico e integrado ao GameState.

## Estado em 2026-10-08

**Parcial — primeira fatia vertical integrada no PR #16 e validada pelo Android Build em `main`. O Gate 6 completo permanece aberto.**

Entregue: proposta estruturada de encontro, aceite/recusa explícitos, iniciativa e ataques no `GameActionResolver`/`CombatRules`, dano/HP/Armor autoritativos, bloqueio de ações incompatíveis, narração condicionada aos eventos e save/load do oponente com migração segura.

## Requisitos

- participantes;
- iniciativa/ordem conforme regras adotadas;
- ações;
- testes/ataques;
- dano;
- HP;
- condições;
- encerramento.

## Critérios de aceitação

- [x] O núcleo de combate pode ser exercitado sem IA por meio do resolver e testes determinísticos.
- [x] Estado mecânico é atualizado pelo domínio e preservado na persistência de combate.
- [x] Guardian recebe fatos mecânicos autorizados para narrar; não escolhe iniciativa, ataque ou dano.

## Pendências para fechar o Gate

- Completar/revisar os procedimentos de condição e encerramento restantes conforme a matriz Cairn 2e.
- Definir procedimento normativo de fuga antes de expor botão/ação; atualmente `EndCombat` é bloqueado durante combate ativo.
- Executar validação visual e de jogabilidade em dispositivo real.
- Não considerar este gate equivalente à implementação integral de Cairn 2e.
