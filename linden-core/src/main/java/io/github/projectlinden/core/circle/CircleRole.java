package io.github.projectlinden.core.circle;

/**
 * A member's role in a circle. Stored as lowercase text ({@code owner}, {@code member}) – the values the database
 * CHECK in V2 allows. The stored text is fixed here, so renaming or reordering the constants never changes stored data.
 */
public enum CircleRole {

    /**
     * Created the circle; may remove members.
     */
    OWNER("owner"),

    /**
     * Belongs to the circle.
     */
    MEMBER("member");

    private final String databaseValue;

    CircleRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    String databaseValue() {
        return databaseValue;
    }

    /**
     * The role for a stored value – an unknown value means the database and the code disagree, so it fails loudly.
     */
    static CircleRole fromDatabaseValue(String value) {
        for (CircleRole role : values()) {
            if (role.databaseValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown circle role in the database: '" + value + "'");
    }
}
