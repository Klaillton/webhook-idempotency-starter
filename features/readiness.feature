# Draft. Restates the approved OpenAPI operation getHealth.

Feature: Readiness
  As an operator
  I want to know when the sample application can receive webhooks
  So that a probe waits until the process is ready

  @op:getHealth @happy
  Scenario: a ready process reports up
    Given the sample application has finished starting
    When a probe checks readiness
    Then the probe reports status UP

  @op:getHealth @unhappy
  Scenario: a process that is not ready reports down
    Given the sample application is not ready
    When a probe checks readiness
    Then the probe reports status DOWN
