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

## Ainda aberto no ADR-0002

- **CRAP ≤ 8** no `core` (JaCoCo mais complexidade ciclomática dos métodos tocados) não está ligado.
- **openapi-diff** continua sem motivo de skip no runner. O ADR manda o gate desligado, com esse motivo, até existir uma baseline estável de OpenAPI.
- Os cenários Gherkin 4 (falha do handler, rollback, segunda entrega reprocessa) e 5 (redelivery concorrente) existem como testes Testcontainers no starter, não em `features/`. Editar Gherkin exige `specs-approved`.
