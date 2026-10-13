# Webhook & Idempotency Gateway — Roadmap (v1)

> Solo dev; **a procura de emprego tem prioridade**. Ritmo leve, cada semana fecha com PR verde.
> **v0.1 mínima na semana 5–6.** Simulador, admin e testes pesados ficam para **v0.2**.

## S1 — Decisões + esqueleto + gates L0
- ADRs: starter in-transaction (D-1/D-3), extractor por source (D-4), resposta síncrona 200+replay (D-9, já decidida).
- `pom.xml` parent + módulos `core`, `starter`, `sample-app`, `e2e`.
- `openapi/openapi.yaml` (intake + health) e `features/` iniciais — **protegidos desde S1**.
- Gates L0 no `pr-gate.yml`: build, protect-specs, deps-lock, secrets-scan (gitleaks), openapi-diff, D13, ArchUnit.
- **Saída:** S1 fecha com o PR #1 (esqueleto + adopt do gauntlet), ruleset aplicado com `policy-base` como check required, e `openapi/openapi.yaml` commitado e protegido.
- **Dependência:** openapi-diff + D13 passam a obrigatórios quando sair o ADD-POLY S4 do ai-code-gauntlet.

## S2 — Núcleo de idempotência + HMAC
- HMAC por source **antes** da idempotência; rotação de segredos; tolerância de timestamp; limite de body.
- Extractors (GitHub `X-GitHub-Delivery`, Stripe event id, `Idempotency-Key`).
- Fingerprint SHA-256 dos bytes crus; store Postgres + Flyway; lease/takeover; replay; 409/422.
- SPI `WebhookHandler` na transação da app; testes de rollback e concorrência (Testcontainers Postgres).
- PIT ≥80% em `core`.
- **Saída:** exactly-once comprovado em testes de integração.

## S3 — e2e Cucumber contra sample-app
- `sample-app` com 1–2 handlers de exemplo.
- `e2e`: Cucumber + Testcontainers (sample-app construída do mesmo commit + Postgres fixado por digest em `ci/images.lock.yaml`).
- Fail-closed: arranque falhado / Docker ausente / 0 cenários → FAIL.
- **Sem simulador.**
- **Saída:** e2e obrigatório no PR gate.
- **Dependência:** o e2e Cucumber depende do ADD-POLY S4 do ai-code-gauntlet; se o S4 não estiver pronto, a S3 escorrega.

## S4 — Retenção e acabamento
- TTL por source (default 7 dias), retenção/limite de `response_body`, job de limpeza.
- Cenários `@unhappy`/`@edge` (assinatura inválida, timestamp fora da tolerância, body grande, chave em falta).
- SDD `Security.md` / `Observability.md` (métricas básicas Micrometer).

## S5–S6 — Release v0.1 (buffer incluído)
- README de integração do starter, holes-review (D12) em dia.
- Publicar artefacto (Maven) e tag `v0.1.0`.

## v0.2 (sem data)
- **Simulador** (só dispara pedidos, respostas cruas, contrato protegido, imagem de release fixada por digest).
- **Admin**: consulta + `reprocess` **auditado**.
- **Pesados**: docker-compose, carga, caos (Toxiproxy) em `nightly.yml`; resultado reportado ao Dante de manhã; fora do PR gate.
- Outbox/202 se a decisão de S1 o deixar para depois; adapter Redis opcional.

## Fora do âmbito
- Ordenação de eventos.

## Riscos
- Tempo disponível variável → S5–S6 servem de buffer.
- Runner de CI sem Docker → e2e falha por desenho; confirmar antes de S3.
