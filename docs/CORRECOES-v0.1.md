# Correções e pendências para a v0.1 operacional

Estado lido no `main` em `a89bf98` (PR #16, 18/11/2026). Consolidado pelo PM a partir das leituras do Arq, do Ops e do PM no canal Gateway em 19–20/11/2026.

## Já feito (S1–S4, parcial)

- Contrato `openapi/openapi.yaml` (intake + health) e `features/` (intake, readiness).
- `core`: HMAC GitHub e Stripe verificado antes da idempotência, fingerprint SHA-256, limite de body, decisões 409/422, 400 para chave em falta.
- `starter`: `POST /webhooks/{source}`, `PostgresIdempotencyStore`, Flyway V1 e V2, uma só transação para reserva + handler + resposta guardada, registo em `AutoConfiguration.imports`.
- Retenção: corpo guardado 24 h, linha com `expires_at` a 7 dias; `tenant_id` nullable (não usado).
- `sample-app` com handler de exemplo e e2e Cucumber.
- Gates: ArchUnit, PIT (mutantes equivalentes aprovados no ADR-0003), CRAP; `policy-base` e `reviewer-approve` no GitHub.

## Correções, por ordem

| # | Tipo | O quê | Porquê | Dono |
|---|------|-------|--------|------|
| 1 | Bloquear | Criar `.github/workflows/pr-gate.yml` com o job `build` (`docker info` fail-closed + `./mvnw -B -ntp verify`) e depois pô-lo nos required checks do ruleset | Nenhum workflow compila nem corre testes; os PRs #7–#16 entraram sem prova de CI do build, Testcontainers e PIT | Ops dá o diff, Dante faz commit e muda o ruleset |
| 2 | Mudar | Apagar `starter/src/main/java/io/github/klaillton/starter/StarterApplication.java` | Tem `@AutoConfiguration` e um `main` dentro do starter; não pode ir no jar publicado. A autoconfiguração real é `WebhookIdempotencyAutoConfiguration` | Dante |
| 3 | Mudar | Job de limpeza que apaga linhas com `expires_at < now()` | ADR-0004 adiou-o para a S4; sem ele a tabela cresce sem fim | Dante, fronteira revista pelo Arq |
| 4 | Falta (v0.1) | `GET /admin/idempotency/{source}/{key}` só de leitura: 401 por defeito, `trace_id` no header, JSON com estado, fingerprint, `locked_until`, código de resposta e timestamps, **sem payload** | Decidido em 05/11 como âmbito da v0.1; ainda não está no `openapi.yaml` nem no código. O contrato entra primeiro (ficheiro protegido) | Dante |
| 5 | Falta (v0.1) | Métricas básicas Micrometer (recebidos, replays, 409, 422, falhas de assinatura) | Roadmap S4; vêm antes da landing | Dante |
| 6 | Falta (S5–S6) | README de integração (quickstart), `docker-compose.yml` com Postgres + sample-app, metadados do `pom` (name, description, url, developers, scm) | Demo e adoção | Dante + Ops |
| 7 | Gate de release | Escolher a licença | Sem licença o Maven Central rejeita o publish e nenhuma app pode depender do starter | Dante |

## Fora da v0.1 (continua em v0.2)

Reprocess, simulador, outbox, admin com UI, forwarding para localhost, payloads no admin.

## Próxima etapa

`docs/next/etapa-1-ci-e-starter.md`: o prompt para correr no Grok CLI (correções 1 e 2).
