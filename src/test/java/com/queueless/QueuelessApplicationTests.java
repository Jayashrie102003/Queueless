package com.queueless;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "test-local"})
class QueuelessApplicationTests {

    @Test
    void contextLoads() {
        // Passes if the application context starts against the test database.
    }
}