package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.anthropic.api-key=test-key",
        "server.port=0"
})
class ToolCallingApplicationContextTests {

    @Test
    void contextLoads() {
    }
}
