# Correções a fazer para a v0.1 operacional

Estado verificado em `main` @ `a89bf98` (merge do PR #16, 18/11/2026). Fontes: revisão do Arq e do PM na sala Gateway, 20/11/2026.

## Já feito
- Contrato `openapi/openapi.yaml` e cenários Gherkin do intake e do health.
- `core`: HMAC GitHub/Stripe verificado antes da idempotência, fingerprint SHA-256, limite de body, decisões 409/422; ArchUnit, PIT e CRAP.
- `starter`: `POST /webhooks/{source}`, store Postgres com Flyway (V1, V2), uma só transação para reserva + handler + resposta; registado em `AutoConfiguration.imports`.
- `sample-app` com handler de exemplo e Cucumber.
- Chave em falta dá 400; corpo guardado 24h; linha expira em 7 dias; `tenant_id` nullable.

## Correções, por ordem

| # | Prioridade | O quê | Onde | Dono | Pronto quando |
|---|---|---|---|---|---|
| 1 | Bloqueia | A CI não corre build nem testes: só existem `policy-base.yml` e `reviewer-approve.yml`. Falta `pr-gate.yml` com `./mvnw verify` (inclui Testcontainers). Os PRs #7–#16 entraram sem prova de CI. | `.github/workflows/pr-gate.yml` | Dante (diff do Ops) | Job `build` verde num PR; tornar check obrigatório é decisão do Dante no ruleset. |
| 2 | Mudar | `StarterApplication.java` tem `@AutoConfiguration` e um `main` dentro do starter. Não está nos imports, mas não pode ir no jar publicado. | `starter/src/main/java/io/github/klaillton/starter/StarterApplication.java` | Dante | Ficheiro apagado, `./mvnw verify` verde. |
| 3 | Ver | Não há job de limpeza dos registos expirados (7 dias). O único `DELETE` em `PostgresIdempotencyStore` é o da libertação de `IN_PROGRESS`; `ResponseRetention` só diz "a later cleanup job may delete rows". A tabela cresce sem fim. | `starter/.../store/PostgresIdempotencyStore.java`, novo componente de limpeza | Dante (fronteira com o Arq) | Limpeza por `expires_at` com teste Testcontainers; propriedade para desligar. |
| 4 | Falta (v0.1) | `GET /admin/idempotency/{source}/{key}` só de leitura. Não existe no código. | `starter` + `openapi/openapi.yaml` + feature Gherkin | Dante | Contrato, feature e teste HTTP verdes. |
| 5 | Falta (v0.1) | Métricas Micrometer básicas (aceites, duplicados, conflitos 409/422, rejeições de assinatura). Não existe no código. | `starter` + `docs/sdd/Observability.md` | Dante | Contadores expostos e testados. |
| 6 | Falta (v0.1) | README de integração (quickstart) e demo Docker. | `README.md`, `sample-app` | Dante / Ops | Quickstart reproduzível do zero. |
| 7 | Parado | Publish no Maven Central (S5–S6): o `pom` está em `0.1.0-SNAPSHOT` sem licença, `scm` nem `developers`. Fica parado até o Dante escolher a licença. | `pom.xml` | Dante / Ops | Licença escolhida. |

Timing e corte do MVP: PM.

## Próxima etapa
Correção 1. Prompt pronto em [`docs/proxima-etapa-grok-cli.md`](proxima-etapa-grok-cli.md).
