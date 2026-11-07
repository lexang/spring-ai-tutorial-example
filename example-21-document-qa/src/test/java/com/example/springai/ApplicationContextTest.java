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
class ApplicationContextTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultOfflineApplicationRunsEndToEndWithoutCredentials() throws Exception {
        this.mockMvc.perform(post("/api/knowledge/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "HTTP RAG 文档",
                      "source": "test://http-rag",
                      "category": "spring-ai",
                      "content": "Spring AI 的 QuestionAnswerAdvisor 从 VectorStore 检索文档，并把上下文加入 ChatModel 请求。"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chunkCount").value(1));

        this.mockMvc.perform(post("/api/knowledge/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question": "QuestionAnswerAdvisor 做什么？",
                      "topK": 1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer", containsString("根据知识库")))
            .andExpect(jsonPath("$.sources[0].source").value("test://http-rag"));
    }

    @Test
    void invalidRequestReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/knowledge/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question": "",
                      "topK": 99
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("question 不能为空")));
    }

}
