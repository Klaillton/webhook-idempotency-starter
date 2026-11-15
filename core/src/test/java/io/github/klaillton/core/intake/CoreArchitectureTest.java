package io.github.klaillton.core.intake;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class CoreArchitectureTest {

    @Test
    void shouldRejectWhenDomainDependsOnFrameworkOrStarter() {
        var classes = new ClassFileImporter().importPackages("io.github.klaillton.core");
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "java.sql..",
                        "javax.sql..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "io.github.klaillton.starter..")
                .check(classes);
    }
}
