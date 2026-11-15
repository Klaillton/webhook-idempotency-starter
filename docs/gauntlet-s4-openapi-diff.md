# openapi-diff permanece desligado

Motivo de skip do ADR-0002 (2026-11-15). O gate não corre neste repositório.

O OpenAPI v0.1 não é uma baseline estável. O estado HTTP de uma chave de idempotência ausente, o tenant na chave e a retenção do corpo da resposta continuam abertos na secção 10 de `docs/ARCHITECTURE.md`. Um diff contra este rascunho falharia ou passaria pelo motivo errado.

O runner do kit pinado não executa openapi-diff. Ligar o gate, ou escrever um limiar em `gauntlet.config.json`, espera uma baseline nomeada num ADR novo e o grant `policy-change-approved`.
