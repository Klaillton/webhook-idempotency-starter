# Draft. Restates the approved OpenAPI operation ingestWebhook.
# Missing idempotency key stays unspecified.

Feature: Webhook intake
  As an application
  I want each webhook delivery applied once
  So that a provider retry does not repeat the business effect

  @op:ingestWebhook @happy
  Scenario: a verified delivery returns the handler result
    Given a source "github" with a valid signature
    And delivery "delivery-1001" has not been seen
    When the provider delivers that webhook
    Then the handler result is returned
    And the delivery is stored as completed

  @op:ingestWebhook @edge
  Scenario: the same delivery returns the stored result
    Given a source "github" has completed delivery "delivery-1001"
    When the provider delivers the same body for that delivery again
    Then the stored handler result is returned
    And the handler does not run again

  @op:ingestWebhook @unhappy
  Scenario: a bad signature is rejected before idempotency
    Given a source "github" whose signature does not verify
    When the provider delivers a webhook
    Then the delivery is rejected
    And no idempotency record is stored

  @op:ingestWebhook @unhappy
  Scenario: a different body for a completed delivery is rejected
    Given a source "github" has completed delivery "delivery-1001"
    When the provider delivers a different body for that same delivery
    Then the delivery is rejected as a fingerprint mismatch
    And the stored result is left unchanged

  @op:ingestWebhook @unhappy
  Scenario: a delivery still in progress is rejected
    Given a source "github" has delivery "delivery-1001" in progress with a valid lease
    When the provider delivers that delivery again
    Then the delivery is rejected until the lease ends
    And the caller is told how many whole seconds to wait

  @op:ingestWebhook @edge
  Scenario: a body above the source limit is rejected
    Given a source "github" whose body limit is 1 MiB
    When the provider delivers a body larger than that limit
    Then the delivery is rejected
    And idempotency is not evaluated
