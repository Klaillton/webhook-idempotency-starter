# Holes review: S1 skeleton

## Âmbito

O que este PR entrega: módulos core, starter, sample-app, CODEOWNERS e a adoção do gauntlet.

## Buracos conhecidos

- Ainda não há openapi.yaml com contrato real
- Não há Postgres, Flyway nem Testcontainers.
- Não há ArchUnit.
- Os testes são só smoke, sem e2e.

## Não coberto por testes

- Contexto Spring startup de sample-app (core and starter não tem contexto Spring)

## Decisões tomadas

- Core não tem Spring dependency ou classe main; Apenas sample-app tem @SpringBootApplication. Porquê: Bibliotecas não fazem boot, a app host sim.
- O nome do artefacto parent é -parent porque é um pom agregador e não é publicado como dependência. O artefacto que os clientes vão usar é o webhook-idempotency-spring-boot-starter.
- Alinhamento do package com a convenção no sample-app e tests. O @SpringBootTest procura a configuração subindo pelos packages

## Próximo passo

"Planejado para (Qui/Sex) S1"

- Contrato do openapi.yaml
- Núcleo de idempotency no Postgres/Flyway com Testcontainers test
- ArchUnit rule core ->/Spring