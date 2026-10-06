# Gate 1 — Motor de Regras Cairn

## Objetivo

Criar a autoridade mecânica do jogo.

## Princípio

A IA nunca deve ser necessária para calcular uma regra.

## Componentes esperados

- atributos;
- modificadores/testes;
- dados/RNG;
- HP;
- dano;
- armadura;
- condições;
- inventário;
- regras de equipamento;
- regras essenciais de sobrevivência.

## Requisitos

- domínio independente de Android;
- entradas e saídas previsíveis;
- testes automatizados;
- RNG isolável para testes.

## Critérios de aceitação

- [ ] Regras essenciais implementadas.
- [ ] Testes automatizados passam.
- [ ] Engine funciona sem Gemini.
- [ ] GameState só muda por operações válidas.
