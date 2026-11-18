# openapi-diff permanece desligado

Motivo de skip revisto em 2026-11-17. O gate não corre neste repositório.

As decisões de chave ausente, tenant e retenção estão no ADR-0004. O OpenAPI descreve o 400 da chave ausente e o replay 200 com corpo vazio. A coluna `tenant_id` está na migration e não é um campo HTTP. A retenção não acrescenta um estado HTTP novo.

O runner do kit pinado não executa openapi-diff. Ligar o gate, ou escrever um limiar em `gauntlet.config.json`, continua à espera do runner e do grant `policy-change-approved`.
