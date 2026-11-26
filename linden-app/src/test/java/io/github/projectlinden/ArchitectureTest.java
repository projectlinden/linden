package io.github.projectlinden;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    // Your rules go here – item 2 onwards
}
