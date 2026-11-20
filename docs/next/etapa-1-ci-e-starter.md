# Etapa 1: CI de build + limpar o starter

Prompt para o Grok CLI. Corre a partir da raiz do repo, num branch novo `fix/ci-build-and-starter` criado a partir do `main`.

---

Contexto: repo `webhook-idempotency-starter` (Spring Boot 3.5, Java 21, Maven multi-módulo `core`, `starter`, `sample-app`). Lê `AGENTS.md` e `docs/CORRECOES-v0.1.md` antes de mexer em qualquer coisa. Não mexas em `openapi/`, `features/`, `docs/adr/`, `docs/holes-review/` nem no ruleset.

Faz só isto, em dois commits:

1. `build: add pr-gate workflow` — cria `.github/workflows/pr-gate.yml` exatamente assim:

```yaml
name: pr-gate
on:
  pull_request:
  push:
    branches: [main]
permissions:
  contents: read
concurrency:
  group: pr-gate-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
        with:
          persist-credentials: false
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Docker obrigatório (fail-closed, Testcontainers)
        run: docker info
      - name: Build e testes
        run: ./mvnw -B -ntp verify
```

2. `fix(starter): remove StarterApplication` — apaga `starter/src/main/java/io/github/klaillton/starter/StarterApplication.java`. A autoconfiguração registada continua a ser `io.github.klaillton.starter.config.WebhookIdempotencyAutoConfiguration` em `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; não a alteres.

Verificação obrigatória antes de abrir o PR, com o Docker Desktop ligado:

```bash
./mvnw -B -ntp verify
```

Só conta o exit code 0. Se falhar, pára e mostra o erro; não desligues testes, gates nem o PIT para passar.

Critério de feito:
- `./mvnw -B -ntp verify` com exit 0 no Katana.
- PR aberto com os dois commits; o job `build` verde no GitHub ao lado do `policy-base`.
- O PR mexe em `.github/**`, por isso leva a label `policy-change-approved`.

Depois do merge (Dante, à mão): juntar `build` aos required checks do ruleset, ao lado de `policy-base (base-run, PR)`.

Próxima etapa a seguir a esta: correção 3 (job de limpeza de `expires_at`).
