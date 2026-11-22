package io.github.projectlinden;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class LindenApplicationIT {

    @Test
    void contextLoads() {
    }
}
