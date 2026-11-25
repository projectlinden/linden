package io.github.projectlinden.core.circle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The rules a circle enforces on its own – no Spring, no database.
 */
class CircleTest {

    private static final UUID CIRCLE_ID = UUID.fromString("3c1e7a52-8f4d-4b6a-9e21-5d7f0a8c4b13");

    @Test
    @DisplayName("empty payload → rejected, the message says the allowed size")
    void new_withEmptyPayload_throws() {
        // when + then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Circle(CIRCLE_ID, new byte[0]))
                .withMessageContaining("1 to " + Circle.MAX_PAYLOAD_BYTES)
                .withMessageContaining("was 0");
    }

    @Test
    @DisplayName("payload one byte over the limit → rejected")
    void new_withPayloadOverTheLimit_throws() {
        // given
        byte[] tooLarge = new byte[Circle.MAX_PAYLOAD_BYTES + 1];

        // when + then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Circle(CIRCLE_ID, tooLarge))
                .withMessageContaining("was " + (Circle.MAX_PAYLOAD_BYTES + 1));
    }

    @ParameterizedTest(name = "{0} bytes")
    @ValueSource(ints = {1, Circle.MAX_PAYLOAD_BYTES})
    @DisplayName("payload exactly at the limits (1 byte, the maximum) → accepted")
    void new_withPayloadAtTheLimits_isAccepted(int size) {
        // when
        Circle circle = new Circle(CIRCLE_ID, new byte[size]);

        // then
        assertThat(circle.getPayload()).hasSize(size);
    }

    @Test
    @DisplayName("changing the array that was passed in afterwards → the circle keeps its payload")
    void new_copiesThePayload() {
        // given
        byte[] payload = {0, 70, 97, 109};
        Circle circle = new Circle(CIRCLE_ID, payload);

        // when
        payload[1] = 99;

        // then
        assertThat(circle.getPayload()).containsExactly(0, 70, 97, 109);
    }

    @Test
    @DisplayName("changing the returned array → the circle keeps its payload")
    void getPayload_returnsACopy() {
        // given
        Circle circle = new Circle(CIRCLE_ID, new byte[]{0, 70, 97, 109});

        // when
        circle.getPayload()[1] = 99;

        // then
        assertThat(circle.getPayload()).containsExactly(0, 70, 97, 109);
    }
}
