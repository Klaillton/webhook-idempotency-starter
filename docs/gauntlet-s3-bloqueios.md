# Bloqueios do Gauntlet S3 na PR #12

Registo de 2026-11-15, branch `practice/boot-3-5-java-21`. A PR é https://github.com/Klaillton/webhook-idempotency-starter/pull/12. O humano põe os labels. O agente não os aplica.

O gate de CRAP falha quando `CRAP > 8`. Um método com complexidade 8 e cobertura total fica em 8,0 e passa. Um método com complexidade 9 e cobertura total fica em 9,0 e falha.

## O que `policy-change-approved` destrava

O label nesta PR autoriza a alteração já feita em `core/pom.xml`: JaCoCo 0.8.13 (`prepare-agent` e `report` na fase `verify`) e o `exec-maven-plugin` 3.5.0 a correr `io.github.klaillton.core.intake.CrapGate`. O tecto 8,0 vive em `CrapGate.CEILING`. O pom não tem tag de limiar.

O label já está na PR. O check obrigatório `policy-base (base-run, PR)` passou com ele. O job `policy-base (base-run, push)` aparece como skipped em `pull_request`: o workflow só o corre em push.

Esse label cobre o ficheiro de build que já está no diff. Continua sem autorização para:

- acrescentar ao pom uma tag cujo nome contenha `threshold`, `minimum`, `coverage` ou `minTests`
- subir o pin `GAUNTLET_KIT_SHA` `481ead9` ou editar `.github/**`
- editar `gauntlet.config.json`
- ligar o openapi-diff
- fechar a secção 10 de `docs/ARCHITECTURE.md`
- tratar os mutantes equivalentes do PIT como mortos

## O que a autorização explícita no chat destravou

A frase a autorizar a alteração do código dos seis métodos foi dada em 2026-11-15. O pom não voltou a mudar. Cada método tocado ficou em 8 ou menos no `mvn -pl core verify` desse dia. `skipValue` está em 8,0.

| Método | CC | Coberto | CRAP |
|---|---:|---:|---:|
| `StripeSignature.parse` | 6 | 6 | 6 |
| `StripeSignature.acceptPart` | 4 | 4 | 4 |
| `IntakeCommand.equals` | 4 | 4 | 4 |
| `IntakeCommand.sameDelivery` | 6 | 6 | 6 |
| `IntakeCommand.samePolicy` | 5 | 5 | 5 |
| `StripeEventId.readField` | 4 | 4 | 4 |
| `StripeEventId.valueOf` | 2 | 2 | 2 |
| `StripeEventId.stringId` | 2 | 2 | 2 |
| `StripeEventId.skippedField` | 4 | 4 | 4 |
| `Cursor.skipValue` | 8 | 8 | 8 |
| `Cursor.skipString` | 2 | 2 | 2 |
| `Cursor.skipObject` | 7 | 7 | 7 |
| `Cursor.decodeEscape` | 2 | 2 | 2 |
| `Cursor.escapeChar` | 2 | 2 | 2 |
| `Cursor.simpleEscape` | 7 | 7 | 7 |

`IdempotencyRecord.equals` está em 8,00. Fica como está enquanto um diff não o tocar.

O chat não é `ALLOW_SPEC_EDIT=1`, não é um label do GitHub e não é `allowSpecEdit` na base. Este diff toca `core/src/main`. O humano pôs `specs-approved` na PR #12, e o mesmo diff inclui `docs/holes-review/s3-crap.md`. Os gates também aceitam estes labels, que o humano já pôs e que o agente não aplica:

- **spec-code (CHANGE-3).** O mesmo diff inclui um spec protegido (Gherkin, OpenAPI ou `docs/holes-review`), ou a PR recebe `spec-sync-approved`.
- **holes-review (D12).** O mesmo diff inclui `docs/holes-review/**/*.md` com as secções preenchidas, ou a PR recebe `holes-approved`.
- **protect-specs.** Criar esse ficheiro em `docs/holes-review/` exige `specs-approved` (ou `ALLOW_SPEC_EDIT=1` na execução). Features e `openapi/openapi.yaml` têm o mesmo requisito.

`policy-change-approved` volta a ser necessário só se o pom, `.github/**` ou `gauntlet.config.json` mudarem outra vez.

## O que fica fora desta PR e desta autorização de código

- **openapi-diff.** O runner pinado em `481ead9` não o executa. Subir o pin mexe em `.github/**` e é outra policy change. O ADR-0002 declara a regra ativa para quando o runner existir. `docs/gauntlet-s4-openapi-diff.md` mantém o motivo de skip: o OpenAPI v0.1 ainda não é baseline estável. Este repositório não liga o gate para “fechar” esse ponto.
- **Chave ausente, tenant e retenção.** Continuam na secção 10. Não há estado HTTP inventado para a chave ausente, não há `tenant_id` na chave, e `expires_at` fica null.
- **Três mutantes equivalentes do PIT.** `docs/gauntlet-s3-pendencias.md` descreve-os: `skipArray`, `decodeEscape` e `unicode`. O ramo morto de `skipObject` saiu com o código, e esse mutante saiu com ele. A aprovação com motivo está no ADR-0003. O PIT não os marca como mortos e o `pom.xml` não os exclui.
- **Surefire `tests="0"` no Cucumber.** Em 2026-11-15, `mvn -pl sample-app -am test` no main terminou com BUILD SUCCESS. O Flyway aplicou a v1. Os 10 cenários passaram no log, incluindo o rollback com segunda entrega e o redelivery concorrente. O XML do `CucumberTestEngine` continua com `tests="0"`. Isso não bloqueia o CRAP. Não há módulo `e2e/` separado. O módulo de e2e do roadmap de produto continua à espera do ADD-POLY S4 do kit.

## Como seguir

1. `policy-change-approved` já está nesta PR e cobre o pom do CRAP. Este commit não muda o pom.
2. O check obrigatório do repositório é `policy-base (base-run, PR)`. O holes-review deste diff é `docs/holes-review/s3-crap.md`, escrito depois de o humano pôr `specs-approved`. `holes-approved` e `spec-sync-approved` também estão na PR. O agente não os pôs e não faz o merge.
