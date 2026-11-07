package com.example.springai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "server.port=0")
@EnabledIfEnvironmentVariable(named = "RUN_RAG_INTEGRATION_TESTS", matches = "true")
class RagBasicsApplicationContextTests {

    @Test
    void contextLoads() {
    }
}
