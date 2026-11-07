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
class TranslationApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void translateEndpointReturnsStructuredResult() throws Exception {
        this.mockMvc.perform(post("/api/translation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text":"Spring AI 让构建企业级 AI 应用变得简单。",
                      "source":"ZH",
                      "target":"EN",
                      "glossary":[
                        {"source":"Spring AI","target":"Spring AI"}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.translatedText", containsString("Spring AI")))
            .andExpect(jsonPath("$.glossaryHits[0].source").value("Spring AI"))
            .andExpect(jsonPath("$.notes", containsString("离线")));
    }

    @Test
    void unsupportedLanguageReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/translation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text":"Hello",
                      "source":"EN",
                      "target":"XX"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("不支持的语言代码")));
    }

    @Test
    void sameSourceAndTargetReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/translation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text":"Hello",
                      "source":"EN",
                      "target":"EN"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("不能相同")));
    }

    @Test
    void blankTextReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/translation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text":"  ",
                      "source":"EN",
                      "target":"ZH"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("text 不能为空")));
    }

}
