package io.github.projectlinden.core.schema;

import io.github.projectlinden.core.PostgresTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Rule as code: no content in the database – every column is classified (VISION.md § 5, ENGINEERING.md § 11).
 * Runs Flyway against a real Postgres, reads every column and compares them with {@value #ALLOW_LIST}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class SchemaClassificationIT {

    private static final String ALLOW_LIST = "/schema-classification.csv";

    /**
     * Flyway's own bookkeeping – not app data, and its columns change with Flyway versions.
     */
    private static final String FLYWAY_TABLE = "flyway_schema_history";

    enum Classification {METADATA, ENCRYPTED}

    record ClassifiedColumn(Classification classification, String reason) {
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("control: the query sees our tables – otherwise an empty result would make every check green")
    void query_seesTheAppTables() {
        assertThat(databaseColumns()).contains("app_user.id");
    }

    @Test
    @DisplayName("every column in the database is on the allow list")
    void everyColumn_isClassified() throws IOException {
        // given
        Set<String> unclassified = databaseColumns();

        // when
        unclassified.removeAll(readAllowList().keySet());

        // then
        assertThat(unclassified)
                .as("columns without a classification – add each to %s as metadata or encrypted, with a reason",
                        ALLOW_LIST)
                .isEmpty();
    }

    @Test
    @DisplayName("every line on the allow list is a column that exists – no stale entries")
    void everyClassifiedColumn_exists() throws IOException {
        // given
        Set<String> stale = new TreeSet<>(readAllowList().keySet());

        // when
        stale.removeAll(databaseColumns());

        // then
        assertThat(stale).as("lines in %s for columns that no longer exist – remove them", ALLOW_LIST).isEmpty();
    }

    /**
     * "table.column" for every column the app's migrations created, in every non-system schema.
     */
    private Set<String> databaseColumns() {
        return new TreeSet<>(jdbcTemplate.queryForList("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                  AND table_name <> ?
                """, String.class, FLYWAY_TABLE));
    }

    /**
     * Reads the allow list; a malformed line fails the test with its line number.
     */
    private static Map<String, ClassifiedColumn> readAllowList() throws IOException {
        List<String> lines;
        try (InputStream in = SchemaClassificationIT.class.getResourceAsStream(ALLOW_LIST)) {
            assertThat(in).as("%s on the test classpath", ALLOW_LIST).isNotNull();
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }

        Map<String, ClassifiedColumn> columns = new TreeMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            int lineNumber = i + 1;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",", 4); // the reason may contain commas
            if (parts.length != 4 || parts[3].isBlank()) {
                fail("%s line %d: expected table,column,classification,reason – got '%s'", ALLOW_LIST, lineNumber, line);
            }
            String key = parts[0].strip() + "." + parts[1].strip();
            ClassifiedColumn previous = columns.put(key, new ClassifiedColumn(classificationOf(parts[2], lineNumber), parts[3].strip()));
            if (previous != null) {
                fail("%s line %d: %s is listed twice", ALLOW_LIST, lineNumber, key);
            }
        }
        return columns;
    }

    private static Classification classificationOf(String value, int lineNumber) {
        try {
            return Classification.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownLevel) {
            throw new AssertionError("%s line %d: unknown classification '%s' – use metadata or encrypted"
                    .formatted(ALLOW_LIST, lineNumber, value.strip()), unknownLevel);
        }
    }
}
