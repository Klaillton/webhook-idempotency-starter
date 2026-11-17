# openapi-diff permanece desligado

Motivo de skip revisto em 2026-11-17. O gate não corre neste repositório.

As decisões de chave ausente, tenant e retenção estão no ADR-0004. O OpenAPI descreve o 400 da chave ausente. A coluna `tenant_id` e a retenção do corpo ainda não estão no contrato HTTP: são schema e replay, não um estado novo desta fatia.

O runner do kit pinado não executa openapi-diff. Ligar o gate, ou escrever um limiar em `gauntlet.config.json`, continua à espera do runner e do grant `policy-change-approved`.
