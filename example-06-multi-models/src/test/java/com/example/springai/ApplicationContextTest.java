package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsTwoManualModelsAndFiveChatClients() {
        assertThat(this.applicationContext.getBeansOfType(ChatModel.class))
            .containsOnlyKeys("openAiChatModel", "miniMaxChatModel");
        assertThat(this.applicationContext.getBeansOfType(ChatClient.class))
            .containsOnlyKeys("fastChatClient", "standardChatClient", "creativeChatClient",
                    "analyticalChatClient", "miniMaxChatClient");
        assertThat(this.applicationContext.getBean(ChatClientBuilderConfigurer.class)).isNotNull();
        assertThat(this.applicationContext.getBean(ChatClient.Builder.class))
            .isNotSameAs(this.applicationContext.getBean(ChatClient.Builder.class));
    }

    @Test
    void modelsEndpointWorksWithoutExternalCredentials() throws Exception {
        this.mockMvc.perform(get("/api/models"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.models.length()").value(5))
            .andExpect(jsonPath("$.models[4].id").value("minimax"));
    }

    @Test
    void blankQuestionReturnsChineseBadRequestBeforeCallingModel() throws Exception {
        this.mockMvc.perform(get("/api/fast").param("question", " "))
            .andExpect(status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(containsString("不能为空")));
    }

    @Test
    void blankMessageAndUnknownModelReturnChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": " ",
                      "model": "fast"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("不能为空")));

        this.mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "测试",
                      "model": "unknown"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("不支持的模型")));
    }

}
