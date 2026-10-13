# Holes review: S1 skeleton

## Âmbito

O que este PR entrega: módulos core, starter, sample-app, CODEOWNERS e a adoção do gauntlet.

## Buracos conhecidos

- Ainda não há openapi.yaml com contrato real (chega no S4).
- Não há Postgres, Flyway nem Testcontainers.
- Não há ArchUnit.
- Os testes são só smoke, sem e2e.

## Não coberto por testes

- O arranque do contexto Spring em cada módulo.

## Riscos e decisões

- Há @SpringBootApplication nas bibliotecas: o core não devia ter, e o starter deve usar @AutoConfiguration.
- O package do smoke test (`sample.app`) não bate com o do main (`sampleapp`).
- O nome do artefacto (-parent ou webhook-idempotency-spring-boot-starter) ainda está por fechar.

## Próximo passo

O que o ROADMAP marca para a semana seguinte.
