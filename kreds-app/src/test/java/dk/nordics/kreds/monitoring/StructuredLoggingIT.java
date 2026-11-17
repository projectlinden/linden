package dk.nordics.kreds.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(properties = "logging.structured.format.console=logstash")
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIT {

    static final Logger logger = LoggerFactory.getLogger(StructuredLoggingIT.class);

    @Test
    void jsonLogLine_containsLevelMessageAndTimestamp(CapturedOutput output) {
        // given
        String message = "structured logging contract";

        // when
        logger.info(message);

        // then
        String line = output.getOut().lines()
                .filter(l -> l.contains(message))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no log line with: " + message));
        JsonNode node = new ObjectMapper().readTree(line);

        assertThat(node.get("level").asString()).isEqualTo("INFO");
        assertThat(node.get("message").asString()).isEqualTo(message);
        assertThat(node.get("@timestamp")).isNotNull();
    }

}
