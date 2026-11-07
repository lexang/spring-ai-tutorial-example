package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CodeEngineeringApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointsReturnStructuredJson() throws Exception {
        this.mockMvc.perform(post("/api/code/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"language":"java","code":"return value;"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary", containsString("输入校验")))
            .andExpect(jsonPath("$.issues[0].severity").value("HIGH"));

        this.mockMvc.perform(post("/api/code/tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"language":"java","code":"return value;"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.framework").value("JUnit 5"));
    }

    @Test
    void invalidInputReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/code/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"language":"java","code":"password=secret"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("疑似包含凭据")));
    }

}
