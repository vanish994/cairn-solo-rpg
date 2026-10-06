# ADR-003 — Provedor de IA

## Decisão

O projeto utilizará Gemini Flash-Lite como provedor principal de IA narrativa.

## Motivo

Boa adequação para respostas rápidas e uso via API.

## Consequência

O código deve usar uma abstração `AIProvider`, permitindo trocar o provedor sem reescrever o domínio.
