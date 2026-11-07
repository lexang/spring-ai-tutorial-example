package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationContextTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void chainEndpointReturnsTrace() throws Exception {
        this.mockMvc.perform(post("/api/agents/chain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "input": "整理需求",
                      "steps": ["提取约束", "生成摘要"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace.length()").value(2));
    }

    @Test
    void blankInputReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/agents/route")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "input": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("不能为空")));
    }

}
