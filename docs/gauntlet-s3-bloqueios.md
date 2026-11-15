# Bloqueios do Gauntlet S3 na PR #12

Registo de 2026-11-15, branch `practice/boot-3-5-java-21`. A PR é https://github.com/Klaillton/webhook-idempotency-starter/pull/12. O humano põe os labels. O agente não os aplica.

O gate de CRAP falha quando `CRAP > 8`. Um método com complexidade 8 e cobertura total fica em 8,0 e passa. Um método com complexidade 9 e cobertura total fica em 9,0 e falha.

## O que `policy-change-approved` destrava

O label nesta PR autoriza a alteração já feita em `core/pom.xml`: JaCoCo 0.8.13 (`prepare-agent` e `report` na fase `verify`) e o `exec-maven-plugin` 3.5.0 a correr `io.github.klaillton.core.intake.CrapGate`. O tecto 8,0 vive em `CrapGate.CEILING`. O pom não tem tag de limiar.

O check obrigatório `policy-base (base-run, PR)` fica vermelho enquanto esse diff do pom existir. O job `policy-base (base-run, push)` aparece como skipped em `pull_request`: o workflow só o corre em push. Um commit só de documentação não torna o check verde.

Esse label cobre o ficheiro de build que já está no diff. A próxima implementação, depois do label, continua sem autorização para:

- reescrever os seis métodos da tabela abaixo
- acrescentar ao pom uma tag cujo nome contenha `threshold`, `minimum`, `coverage` ou `minTests`
- subir o pin `GAUNTLET_KIT_SHA` `481ead9` ou editar `.github/**`
- editar `gauntlet.config.json`
- ligar o openapi-diff
- fechar a secção 10 de `docs/ARCHITECTURE.md`
- tratar os quatro mutantes equivalentes do PIT como mortos

## O que uma autorização explícita no chat destrava

Uma frase nesta conversa, a pedir a alteração do código, autoriza a próxima implementação a baixar o CRAP dos seis métodos. O label da PR não inicia esse trabalho, e o trabalho não espera pelo label: o pom já não precisa de mudar.

| Método | CC | Cobertura | CRAP | O que o faz passar |
|---|---:|---:|---:|---|
| `StripeSignature.parse` | 9 | 0,56 | 16,11 | Baixar a complexidade para 8 ou menos e cobrir o que o diff tocar |
| `IntakeCommand.equals` | 11 | 1,00 | 11,00 | Baixar a complexidade para 8 ou menos |
| `StripeEventId.readField` | 9 | 1,00 | 9,00 | Baixar a complexidade para 8 ou menos |
| `StripeEventId.Cursor.decodeEscape` | 9 | 1,00 | 9,00 | Baixar a complexidade para 8 ou menos |
| `StripeEventId.Cursor.skipValue` | 9 | 1,00 | 9,00 | Baixar a complexidade para 8 ou menos |
| `StripeEventId.Cursor.skipObject` | 8 | 0,75 | 9,00 | Remover o ramo morto `if (!eat('{')) return false` (linha 141). `skipValue` só chama `skipObject` quando o carácter seguinte já é `{`, por isso esse `return false` não corre. Com complexidade 8 e cobertura total o score é 8,0 e o gate passa |

`IdempotencyRecord.equals` está em 8,00. Fica como está enquanto um diff não o tocar.

O chat não é `ALLOW_SPEC_EDIT=1`, não é um label do GitHub e não é `allowSpecEdit` na base. Se o diff tocar `core/src/main`, os gates lêem outra autorização, posta pelo humano na PR dessa implementação:

- **spec-code (CHANGE-3).** O mesmo diff inclui um spec protegido (Gherkin, OpenAPI ou `docs/holes-review`), ou a PR recebe `spec-sync-approved`.
- **holes-review (D12).** O mesmo diff inclui `docs/holes-review/**/*.md` com as secções preenchidas, ou a PR recebe `holes-approved`.
- **protect-specs.** Criar esse ficheiro em `docs/holes-review/` exige `specs-approved` (ou `ALLOW_SPEC_EDIT=1` na execução). Features e `openapi/openapi.yaml` têm o mesmo requisito.

`policy-change-approved` volta a ser necessário só se o pom, `.github/**` ou `gauntlet.config.json` mudarem outra vez.

## O que fica fora desta PR e desta autorização de código

- **openapi-diff.** O runner pinado em `481ead9` não o executa. Subir o pin mexe em `.github/**` e é outra policy change. O ADR-0002 declara a regra ativa para quando o runner existir. `docs/gauntlet-s4-openapi-diff.md` mantém o motivo de skip: o OpenAPI v0.1 ainda não é baseline estável. Este repositório não liga o gate para “fechar” esse ponto.
- **Chave ausente, tenant e retenção.** Continuam na secção 10. Não há estado HTTP inventado para a chave ausente, não há `tenant_id` na chave, e `expires_at` fica null.
- **Quatro mutantes equivalentes do PIT.** `docs/gauntlet-s3-pendencias.md` descreve-os. Essa nota não é a aprovação humana com motivo. Não se excluem do PIT.
- **Surefire `tests="0"` no Cucumber.** Em 2026-11-15, `mvn -pl sample-app -am test` no main terminou com BUILD SUCCESS. O Flyway aplicou a v1. Os 10 cenários passaram no log, incluindo o rollback com segunda entrega e o redelivery concorrente. O XML do `CucumberTestEngine` continua com `tests="0"`. Isso não bloqueia o CRAP. Não há módulo `e2e/` separado. O módulo de e2e do roadmap de produto continua à espera do ADD-POLY S4 do kit.

## Como seguir

1. Nesta PR, o humano adiciona `policy-change-approved`. Isso deixa o `policy-base` avaliar o pom que já está no diff.
2. Quando a próxima implementação dos seis métodos for para começar, o humano escreve nessa conversa que autoriza a alteração do código. Os labels `specs-approved`, `holes-approved` e `spec-sync-approved` entram na PR desse trabalho, conforme o diff que ela levar. O agente não os aplica.
