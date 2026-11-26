package io.github.projectlinden;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules as code: the module boundaries (ENGINEERING.md § 5, § 11). Reads the compiled main classes of all modules –
 * no Spring, no database, so it runs in a second.
 */
class ArchitectureTest {

    /**
     * Our main code only – test classes may break the rules (e.g. a test-only controller).
     */
    private static final JavaClasses LINDEN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.projectlinden");

    @Test
    @DisplayName("control: the import sees classes from core and api – otherwise every rule would pass on nothing")
    void import_seesTheModules() {
        assertThat(LINDEN_CLASSES.contain("io.github.projectlinden.core.circle.CircleService")).as("core").isTrue();
        assertThat(LINDEN_CLASSES.contain("io.github.projectlinden.api.circle.CircleController")).as("api").isTrue();
    }

    private static final String CORE = "io.github.projectlinden.core..";
    private static final String API = "io.github.projectlinden.api..";

    @Test
    @DisplayName("core knows nothing about HTTP or login tokens – no web, servlet or Spring Security classes")
    void core_knowsNothingAboutHttp() {
        ArchRule rule = noClasses().that().resideInAPackage(CORE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "org.springframework.security..",
                        "jakarta.servlet..")
                .because("the rules must work the same whoever calls them – HTTP and tokens live in linden-api, "
                        + "core gets the user only as a UUID (ENGINEERING.md § 5)");
        rule.check(LINDEN_CLASSES);
    }

    @Test
    @DisplayName("the API holds no rules and no database details – no repositories, JPA, Spring Data, Hibernate or JDBC")
    void api_usesServicesNotTheDatabase() {
        ArchRule rule = noClasses().that().resideInAPackage(API)
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.hibernate..",
                        "org.springframework.jdbc..",
                        "javax.sql..",
                        "java.sql..")
                .because("every rule (membership, ownership, limits) lives in a core service – a controller that reads "
                        + "the database itself, through JPA or plain JDBC, skips them (ENGINEERING.md § 5)");
        rule.check(LINDEN_CLASSES);
    }

    @Test
    @DisplayName("the API opens no transactions – transactions belong to the core services")
    void api_opensNoTransactions() {
        ArchRule classes = noClasses().that().resideInAPackage(API)
                .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .orShould().beAnnotatedWith(jakarta.transaction.Transactional.class);
        ArchRule methods = noMethods().that().areDeclaredInClassesThat().resideInAPackage(API)
                .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .orShould().beAnnotatedWith(jakarta.transaction.Transactional.class)
                .because("transactions belong to the core services – Spring honours both annotations");
        classes.check(LINDEN_CLASSES);
        methods.check(LINDEN_CLASSES);
    }
}
