# O que esta fatia do Gauntlet S3 não fecha

Registo de 2026-11-15. O PIT 1.19.4 no módulo `core` matou **339 de 343** mutantes (99%). O limiar do ADR-0002 é 80%. A corrida foi:

```text
.\mvnw.cmd -B -ntp -pl core "-DmutationThreshold=80" org.pitest:pitest-maven:mutationCoverage
```

O limiar vai na linha de comando. Não há elemento de threshold no `pom.xml`: o guarda do gauntlet falha qualquer tag cujo nome contenha `threshold`, `minimum`, `coverage` ou `minTests`. O plugin não está ligado à fase `test`.

Três timeouts de laço infinito entram na conta de mortos do PIT. Não são uma exclusão.

## Mutantes que continuam vivos

O ADR-0002 só conta um mutante equivalente como morto depois de aprovação humana com o motivo registado. **Esta nota não é essa aprovação.** Os quatro abaixo ficam de fora dos 339. O score passa sem eles.

### `StripeEventId.Cursor.skipObject`, linha 141

`if (!eat('{')) return false` substituído por `return true`. Estado no relatório: `NO_COVERAGE`.

`skipValue` só chama `skipObject` quando o carácter seguinte já é `{`. `eat('{')` sucede e o `return false` não corre. O mesmo vale para `skipArray`, linha 167, com `[`.

### `StripeEventId.Cursor.decodeEscape`, linha 226

`if (done()) return null` substituído por `return ""`. Estado: `SURVIVED`.

O único chamador é `readString`. No fim do texto, a string vazia é acrescentada e o ciclo seguinte vê o cursor terminado, por isso `readString` devolve `null` nos dois lados. O `id` observável não muda.

### `StripeEventId.Cursor.unicode`, linha 242

`index + 4 > text.length()` com a fronteira trocada para `>=`. Estado: `SURVIVED`.

Com exatamente quatro dígitos hex não há aspas de fecho a seguir, e `readString` devolve `null` nos dois lados. Com cinco ou mais caracteres (aspas ou mais texto), `>` e `>=` são os dois falsos. O resultado não muda.

## Decisões de produto pendentes

O slice do S4 não fecha estas três. Continuam na secção 10 de `docs/ARCHITECTURE.md`.

- **Chave ausente.** Uma entrega sem chave de idempotência não tem estado HTTP. Não se mapeia para 400 nem para 200, e o store não é escrito. Falta decidir se a omissão rejeita ou se processa sem idempotência.
- **Tenant.** A chave continua `(source, key)`. Incluir `tenant_id` está em aberto.
- **Retenção.** O corpo da resposta é guardado e repetido. Período, limite de tamanho, truncagem e nonce não estão definidos. `expires_at` fica null.

## CRAP nos métodos tocados

Ligado na branch `practice/boot-3-5-java-21`. `mvn -pl core verify` gera o JaCoCo e o `CrapGate` falha se um método do `core` tocado pelo diff passa de 8. O tecto está no código, não numa tag do `pom.xml`. Sem métodos tocados, o gate passa.

Seis métodos já medidos acima de 8 ficam de fora enquanto o diff não os toca: `StripeSignature.parse` (16,11), `IntakeCommand.equals` (11), `StripeEventId.readField` (9), `Cursor.decodeEscape` (9), `Cursor.skipValue` (9), `Cursor.skipObject` (9).

## Ainda aberto no ADR-0002
- **openapi-diff** fica desligado. O motivo está em `docs/gauntlet-s4-openapi-diff.md`. O gate não foi ligado e `gauntlet.config.json` não mudou.
- Os cenários Gherkin 4 (falha do handler, rollback, segunda entrega reprocessa) e 5 (redelivery concorrente) estão em `features/ingest-webhook.feature` e correm no Postgres pela sample-app. O `protect-specs` só passa com `specs-approved`.
