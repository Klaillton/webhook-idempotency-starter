# Webhook & Idempotency Gateway — Arquitetura (v1)

> Estado: **v1 aprovada pelo Dante** (2026-10-09), após revisão do Challenge.
> Stack: **Spring Boot starter**, Java 21, Maven multi-módulo, **Postgres**.
> Qualidade: conceitos do [ai-code-gauntlet](https://github.com/Klaillton/ai-code-gauntlet) (SDD, Gherkin/Cucumber, OpenAPI protegido, openapi-diff, D13) com **ferramentas Java** (PIT, ArchUnit), não o kit TS.

## 1. Objetivo

Biblioteca (starter) que uma app Spring Boot inclui para receber webhooks, verificar a autenticidade e garantir **efeito exatamente-uma-vez**: reserva da chave, efeito de negócio e resposta guardada são gravados **na mesma transação da app**. Não há serviço gateway separado.

## 2. Decisões fechadas

| # | Decisão |
|---|---|
| D-1 | **Starter**, não serviço. Corre no processo e na transação da app. |
| D-2 | Store **Postgres** (fechado). Redis só como eventual adapter futuro de `IdempotencyStore` (ver §9). |
| D-3 | SPI `WebhookHandler` executa dentro da transação da app; reserva + efeito + resposta no **mesmo commit** → exactly-once. |
| D-4 | Chave obtida por **extractor configurável por source**; id nativo do provider é o primário. |
| D-5 | Fingerprint = **SHA-256 dos bytes crus do body** (sem canonicalização JSON). |
| D-6 | HMAC verificado **antes** da idempotência. |
| D-7 | Gates com equivalentes Java (PIT, ArchUnit, openapi-diff, verificação D13 própria). |
| D-8 | **Ordenação de eventos fora do âmbito.** |
| D-9 | Resposta **síncrona `200` + replay** (outbox/202 só como modo opcional futuro). |

## 3. Módulos (reactor Maven)

```
webhook-idempotency-starter/
├── pom.xml                         # parent (Java 21, BOM Spring Boot)
├── openapi/openapi.yaml            # PROTEGIDO — contrato HTTP do intake (desde S1)
├── features/**/*.feature           # PROTEGIDO — Gherkin
├── core/                           # domínio: idempotência, fingerprint, HMAC, SPI (sem Spring)
├── starter/                        # autoconfigure: filtro/controller, JDBC store, Flyway, properties
├── sample-app/                     # app de exemplo que usa o starter = alvo do e2e
├── e2e/                            # Cucumber + Testcontainers (sample-app + Postgres)
├── simulator/                      # v0.2 — só dispara pedidos e devolve respostas cruas
├── ci/images.lock.yaml             # PROTEGIDO — digests de imagens de terceiros e releases
├── docs/{adr,sdd,holes-review}/
└── .github/workflows/{pr-gate.yml,nightly.yml}
```

`core` não depende de Spring nem de JDBC (ArchUnit). `starter` liga `core` ao Spring e ao Postgres.

## 4. SPI e fluxo transacional

```java
public interface WebhookHandler {
    String source();                                  // ex. "github", "stripe"
    WebhookResult handle(WebhookEvent event);         // corre na transação da app
}
```

Fluxo por pedido (`POST /webhooks/{source}`):
1. Limite de tamanho do body (por source) → `413` se exceder.
2. Verificação HMAC + tolerância de timestamp/nonce (§6) → `401` se falhar.
3. Extractor obtém a chave; sem chave → comportamento configurável por source (rejeitar ou processar sem idempotência), **sem 400 genérico**.
4. Abre transação (`@Transactional` da app):
   - `INSERT ... ON CONFLICT DO NOTHING` em `IN_PROGRESS` com `locked_until = now() + lease`.
   - Se já existe: `COMPLETED` + mesmo fingerprint → **replay** da resposta guardada; fingerprint diferente → `422`; `IN_PROGRESS` com lease válido → `409` + `Retry-After`; lease expirado → **takeover** (`UPDATE ... WHERE locked_until < now()`).
   - `WebhookHandler.handle(...)` (efeito de negócio).
   - Grava resposta e passa a `COMPLETED`.
5. Commit. Qualquer falha → **rollback total** (reserva + efeito + resposta), o provider pode reenviar com segurança.

Nota: dentro de uma única transação a linha fica bloqueada pela própria transação; o lease protege o caso em que o handler usa transações próprias ou chamadas externas longas, e o caso outbox (§5).

## 5. Modelo de resposta — **decidido: síncrono 200 + replay**

| Opção | Prós | Contras |
|---|---|---|
| A. Síncrono `200` + replay | Simples; resposta real guardada; encaixa no D-3 | Handler lento pode passar o timeout do provider |
| B. Outbox + `202` | Intake rápido; handler fora do pedido | Mais peças; exactly-once passa a depender do consumidor do outbox |

**Decidido (Dante, 2026-10-09): A.** B pode entrar mais tarde como modo opcional por source, se surgir handler lento. O contrato (`openapi.yaml`) fica protegido a partir de S1 com a opção escolhida.

## 6. Chave, fingerprint, segurança

**Extractors (por source, configuráveis):**

| Source | Extractor por omissão |
|---|---|
| GitHub | header `X-GitHub-Delivery` |
| Stripe | campo do body `id` (event id) |
| Genérico | header `Idempotency-Key` (é só mais um extractor) |

Âmbito da chave: `(source, key)` — ver questão de tenant (§10).

**Fingerprint:** SHA-256 dos bytes crus recebidos (antes de qualquer parsing).

**Assinatura:**
- HMAC por source; **rotação de segredos**: lista de segredos ativos (atual + anterior com data de expiração), aceita qualquer um válido.
- **Anti-replay**: tolerância de timestamp (ex. 5 min, por source, ex. Stripe `t=`) e/ou nonce quando o provider o fornecer; o próprio id nativo + store cobre replays dentro do TTL.
- Limite de tamanho do body por source (default 1 MiB).

## 7. Store (Postgres)

`idempotency_record(source, key, fingerprint, status, locked_until, response_code, response_body, created_at, expires_at)` — PK `(source, key)`.

- **TTL por source ≥ janela de retry do provider** (default 7 dias; Stripe reenvia até 3 dias).
- `response_body`: limite de tamanho (truncar/omitir acima de X KB) e **retenção própria** mais curta que a chave (pode conter PII); após retenção fica `NULL` e o replay devolve só status. Política documentada em `docs/sdd/Security.md`.
- Limpeza por job agendado (batch delete por `expires_at`).

## 8. Endpoints (esboço — fonte de verdade: `openapi.yaml`)

| Método | Path | Versão |
|---|---|---|
| POST | `/webhooks/{source}` | v0.1 |
| GET | `/actuator/health` | v0.1 (wait strategy do Testcontainers) |
| GET | `/admin/idempotency/{source}/{key}` | v0.2 |
| POST | `/admin/idempotency/{source}/{key}/reprocess` | v0.2 — **auditado** (quem, quando, motivo) |

## 9. Testes e CI

| Camada | Ferramenta | Onde |
|---|---|---|
| Unit (`core`) | JUnit 5 + AssertJ; PIT ≥80% kill | PR gate |
| Arquitetura | ArchUnit (`core` sem Spring/JDBC) | PR gate |
| Integração (`starter`) | Spring Boot Test + Testcontainers Postgres (concorrência, lease, rollback) | PR gate |
| Contrato | openapi-diff (base vs PR) + D13 (cada operação tem caso) | PR gate |
| e2e | Cucumber (`features/**`) contra **sample-app + Postgres** via Testcontainers | PR gate (desde S3) |
| Pesados | docker-compose, carga, caos (Toxiproxy) | **nightly**, fora do PR gate (v0.2) |

**Regras e2e:**
1. **Asserções só nos steps Cucumber**, derivados das features protegidas. O simulador (v0.2) apenas dispara pedidos e devolve respostas cruas; o seu contrato também é protegido.
2. **Digests:** `ci/images.lock.yaml` fixa por `@sha256:` as imagens de terceiros (Postgres, Toxiproxy) e as imagens de release publicadas. No PR gate, sample-app (e simulador) são **construídos a partir do mesmo commit**.
3. Contentor não arranca / não fica healthy / Docker ausente / 0 cenários → **FAIL, nunca skip** (sem `@Disabled`, `assumeTrue`, `disabledWithoutDocker`, `@ignore`).
4. Paths protegidos: `features/**`, `openapi/openapi.yaml`, `ci/images.lock.yaml`, contrato do simulador; `pom.xml` sob deps-lock.

**Pesados (v0.2):** `nightly.yml` corre de madrugada; resultado (verde/vermelho + link) **reportado ao Dante de manhã**. Não bloqueia PRs.

**Redis (nota):** só como adapter opcional futuro de `IdempotencyStore`. Se existir: `maxmemory-policy noeviction` ou `volatile-*`, hash + TTL gravados atomicamente (Lua/`MULTI`), e o lock Redis é **eficiência, não correção** — perde-se a garantia de mesma transação.

## 10. Questões em aberto

1. **Âmbito de tenant**: a chave deve incluir `tenant_id` (app multi-tenant) ou fica `(source, key)`?
2. Comportamento por omissão quando o extractor não encontra chave (rejeitar vs processar sem idempotência).
3. Defaults de retenção de `response_body` e limite de tamanho.
4. Registry das imagens de release (GHCR?).
5. Auth do admin (v0.2).
