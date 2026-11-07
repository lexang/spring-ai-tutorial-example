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
class ContentStudioApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointReturnsCompleteContentPackage() throws Exception {
        this.mockMvc.perform(post("/api/content/packages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"topic":"Spring AI 多模态","audience":"Java 开发者","style":"professional"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", containsString("Spring AI")))
            .andExpect(jsonPath("$.imageBase64").isNotEmpty())
            .andExpect(jsonPath("$.audioBase64").isNotEmpty())
            .andExpect(jsonPath("$.audioContentType").value("audio/wav"));
    }

    @Test
    void invalidStyleReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/content/packages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"topic":"测试","audience":"开发者","style":"unknown"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("style 仅支持")));
    }

}
