# S3 Gates Sketch (practice branch)

Practice branch for Boot 3.5 / Java 21. Thresholds fixed in ADR-0002.

## PIT (mutation testing)

Scope: `core` module only. Threshold: 80 % kill score.

Example Maven config (core/pom.xml):

```xml
<plugin>
  <groupId>org.pitest</groupId>
  <artifactId>pitest-maven</artifactId>
  <version>${pitest.version}</version>
  <configuration>
    <targetClasses>
      <param>io.github.klaillton.core.*</param>
    </targetClasses>
    <targetTests>
      <param>io.github.klaillton.core.*</param>
    </targetTests>
    <mutationThreshold>80</mutationThreshold>
    <coverageThreshold>0</coverageThreshold>
    <timestampedReports>false</timestampedReports>
  </configuration>
</plugin>
```

Equivalent mutants require explicit human approval with reason (do not auto-kill).

## CRAP

Ceiling ≤ 8 on touched methods in `core`. Derived from JaCoCo + cyclomatic complexity.

Gate fails if any method in `core` exceeds 8 after a change. Implementation reuses JaCoCo report (S2) + complexity tool.

## ArchUnit

Fail-closed rule: domain package of `core` must not import Spring, JDBC, servlet, or starter classes.

Example test (core/src/test/.../ArchitectureTest.java):

```java
@AnalyzeClasses(packages = "io.github.klaillton.core")
public class ArchitectureTest {
  @ArchTest
  static final ArchRule domain_isolation = noClasses()
      .that().resideInAPackage("io.github.klaillton.core.intake..")
      .should().dependOnClassesThat()
      .resideInAnyPackage(
          "org.springframework..",
          "java.sql..",
          "jakarta.servlet..",
          "io.github.klaillton.starter..");
}
```

Layers: domain (pure) → adapters (may depend on domain) → starter (autoconfiguration only).

These three can be implemented independently of the e2e (already marked existing).
