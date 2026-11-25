package io.github.projectlinden.core.circle;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/**
 * Translates {@link CircleRole} to and from its lowercase database text.
 */
@Converter
public final class CircleRoleConverter implements AttributeConverter<CircleRole, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable CircleRole role) {
        return role == null ? null : role.databaseValue();
    }

    @Override
    public @Nullable CircleRole convertToEntityAttribute(@Nullable String value) {
        return value == null ? null : CircleRole.fromDatabaseValue(value);
    }
}
