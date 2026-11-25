package io.github.projectlinden.core.circle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A circle. Its name is sealed on the phone and stored as an opaque {@code payload} (first byte: format version,
 * {@code 0} = fake lock) – the server never reads it.
 */
@Entity
@Table(name = "circle")
public class Circle {

    /**
     * Same limit as the database check in V2 – a name is short; this stops megabytes "as a name".
     */
    public static final int MAX_PAYLOAD_BYTES = 4096;

    @Id
    private UUID id;

    @Column(name = "payload", nullable = false)
    private byte[] payload;

    @Nullable
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * For JPA only.
     */
    protected Circle() {
    }

    public Circle(UUID id, byte[] payload) {
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "A circle payload must be 1 to " + MAX_PAYLOAD_BYTES + " bytes, was " + payload.length);
        }
        this.id = id;
        this.payload = payload.clone();
    }

    public UUID getId() {
        return id;
    }

    /**
     * A copy – changing the returned array never changes the circle.
     */
    public byte[] getPayload() {
        return payload.clone();
    }

    /**
     * Set by the database on insert; read it back from the database to see it.
     */
    public @Nullable Instant getCreatedAt() {
        return createdAt;
    }

}
