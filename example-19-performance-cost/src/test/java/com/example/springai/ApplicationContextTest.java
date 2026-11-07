package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationContextTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextStartsWithoutExternalModel() throws Exception {
        this.mockMvc.perform(get("/api/performance/usage"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cumulativeTotalTokens").isNumber())
            .andExpect(jsonPath("$.cumulativeNativeUsagePresent").value(false));
    }

    @Test
    void budgetErrorIsAChineseHttp400() throws Exception {
        this.mockMvc.perform(post("/api/performance/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "prompt": "预算拦截",
                      "maxOutputTokens": 400,
                      "maxBudget": 0.00000001
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("超过预算")));
    }

    @Test
    void modelCallPublishesOfficialTokenMeter() throws Exception {
        this.mockMvc.perform(post("/api/performance/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "prompt": "发布 token 指标",
                      "maxOutputTokens": 20,
                      "maxBudget": 0.001
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTokens").isNumber());

        this.mockMvc.perform(get("/actuator/metrics/gen_ai.client.token.usage"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("gen_ai.client.token.usage"));
    }

}
