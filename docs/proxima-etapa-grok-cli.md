# Próxima etapa: correção 1 (CI com build e testes)

Corre no Grok CLI a partir da raiz do repo, num branch novo (ex.: `ci/pr-gate`). Copia o bloco abaixo como prompt.

```text
Tarefa: adicionar .github/workflows/pr-gate.yml ao repo webhook-idempotency-starter (Java 21, Maven wrapper, módulos core, starter, sample-app).

Requisitos:
- Dispara em pull_request para main e em push para main.
- Um job chamado `build` em ubuntu-latest: checkout, actions/setup-java (temurin 21, cache maven), `./mvnw -B -ntp verify`.
- Os testes Testcontainers (Postgres) têm de correr: o runner ubuntu-latest já tem Docker; não desligues nem saltes testes, nem uses -DskipTests ou -Dmaven.test.failure.ignore.
- Permissões mínimas: `permissions: contents: read`.
- Não alteres policy-base.yml, reviewer-approve.yml, rulesets nem ficheiros em docs/sdd ou openapi/.
- Não alteres código de produção nem testes para pôr a CI verde; se algo falhar, pára e mostra o erro.

Prova antes de acabar:
1. `./mvnw -B -ntp verify` local com exit code 0 (mostra o resumo do reactor).
2. Mostra o diff completo do workflow.

Depois disso, num commit separado: apaga starter/src/main/java/io/github/klaillton/starter/StarterApplication.java (correção 2 de docs/correcoes-v0.1.md) e volta a correr `./mvnw -B -ntp verify`.
```

Depois: abre o PR, confirma que o job `build` fica verde e decide tu se o tornas check obrigatório no ruleset.
