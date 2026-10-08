# Roadmap

## Gate 0 — Arquitetura Android e repositório

Objetivo: estabelecer a base técnica.

Entregáveis:
- projeto Android;
- estrutura inicial;
- README;
- documentação dos Gates;
- build inicial;
- tela inicial mínima.

Concluído quando:
- projeto compila;
- APK de debug é gerado;
- aplicativo abre no Android.

---

## Gate 1 — Motor de Regras Cairn

Objetivo: implementar as mecânicas essenciais de forma determinística.

Entregáveis:
- atributos;
- testes/rolagens;
- HP;
- dano;
- armadura;
- condições;
- inventário básico;
- regras de morte/incapacitação conforme escopo definido;
- testes automatizados.

Concluído quando:
- regras essenciais podem ser executadas sem IA;
- resultados são reproduzíveis quando o RNG é controlado;
- testes passam.

---

## Gate 2 — Criação de Personagem

Objetivo: criar um personagem válido.

Entregáveis:
- fluxo de criação;
- nome;
- atributos;
- HP;
- equipamento;
- inventário;
- validação.

Concluído quando:
- personagem válido pode ser criado;
- dados ficam no GameState.

---

## Gate 3 — Campanha e GameState

Objetivo: criar uma campanha funcional.

Entregáveis:
- campanha;
- localização;
- cena;
- NPCs;
- eventos;
- estado do mundo;
- histórico mínimo.

Concluído quando:
- uma campanha pode ser iniciada;
- ações produzem mudanças no estado.

---

## Gate 4 — Integração Gemini

Objetivo: conectar o provedor de IA sem delegar regras à IA.

Entregáveis:
- AIProvider;
- Gemini provider;
- configuração segura;
- MockAIProvider;
- tratamento de erro;
- timeout/retry apropriados;
- contexto mínimo enviado ao modelo.

Concluído quando:
- Gemini recebe contexto;
- retorna resposta estruturada;
- o aplicativo continua funcionando sem Gemini.

---

## Gate 5 — GM Narrativo

Objetivo: transformar resultados mecânicos em narrativa.

Entregáveis:
- prompt do GM;
- construção de contexto;
- interpretação de intenção;
- narrativa;
- memória contextual limitada;
- proteção contra alterações mecânicas inventadas pela IA.

Concluído quando:
- jogador pode declarar ações em linguagem natural;
- engine decide o resultado;
- Gemini narra o resultado.

---

## Gate 6 — Combate

Objetivo: combate jogável.

**Estado em 2026-10-08: parcial — fatia vertical integrada pelo PR #16 e validada pelo Android Build em `main`.** O Gate 6 não está fechado: os critérios abaixo abrangem procedimentos de Cairn 2e além do escopo desta entrega.

Entregáveis:
- participantes;
- iniciativa/ordem conforme regras adotadas;
- ações;
- ataques;
- dano;
- condições;
- encerramento.

Entregue nesta fatia: proposta estruturada de oponente pelo Guardian, confirmação/recusa do jogador, iniciativa e ataques resolvidos por `GameActionResolver`/`CombatRules`, dano/HP/Armor autoritativos, bloqueio de ações incompatíveis durante combate, narração baseada nos fatos do resolver e persistência com migração segura.

Pendente para fechar o Gate: validar em dispositivo real e completar procedimentos restantes de condição/encerramento segundo a matriz normativa. Não há ação de fuga ou encerramento voluntário no app; `EndCombat` é bloqueado durante combate ativo até existir procedimento normativo aprovado.

Concluído quando:
- combate completo pode ser executado sem depender de texto livre da IA.

---

## Gate 7 — Save/Load

Objetivo: persistência confiável.

Entregáveis:
- salvar campanha;
- carregar campanha;
- migração/versionamento de dados;
- recuperação de estado.

Concluído quando:
- fechar e reabrir o aplicativo não perde uma campanha salva.

---

## Gate 8 — APK

Objetivo: primeira versão jogável distribuível.

Entregáveis:
- build release;
- assinatura adequada;
- testes em dispositivo real;
- correções de estabilidade;
- documentação de instalação.

Concluído quando:
- APK instala;
- campanha pode ser criada;
- personagem pode agir;
- regras funcionam;
- narrativa funciona;
- save/load funciona.
