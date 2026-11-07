package com.example.springai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromptControllerTest {

    @Test
    void missingTemplateParameterHasChineseBadRequest() throws Exception {
        var controller = new PromptController(ChatClient.builder(new RecordingModel()));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new CoreConceptsExceptionHandler()).build();
        mvc.perform(get("/prompt/template")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数缺失或类型不正确"));
    }

    @Test
    void rendersTheOfficialTemplateAndKeepsParameterBraces() {
        RecordingModel model = new RecordingModel();
        PromptController controller = new PromptController(ChatClient.builder(model));
        assertThat(controller.template("解释 Map.of(\"k\", \"{value}\")")).containsEntry("response", "测试回答");
        assertThat(model.prompt.getUserMessage().getText())
                .contains("Java 专家", "{value}", "中文").doesNotContain("{role}", "{language}");
    }

    @Test
    void blankInputIsRejectedBeforeTheModel() {
        RecordingModel model = new RecordingModel();
        PromptController controller = new PromptController(ChatClient.builder(model));
        assertThatThrownBy(() -> controller.userMessageOnly(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("message 不能为空");
        assertThat(model.calls).isZero();
    }

    @Test
    void missingGenerationHasAModelErrorInsteadOfMapNullPointer() {
        RecordingModel model = new RecordingModel();
        model.response = new ChatResponse(List.of());
        PromptController controller = new PromptController(ChatClient.builder(model));
        assertThatThrownBy(() -> controller.userMessageOnly("你好"))
                .isInstanceOf(IllegalStateException.class).hasMessage("模型未返回可用的文本");
    }

    @Test
    void modelFailureHasAChineseBoundaryMessage() {
        ChatModel model = prompt -> { throw new RuntimeException("内部供应商诊断"); };
        PromptController controller = new PromptController(ChatClient.builder(model));
        assertThatThrownBy(() -> controller.template("你好"))
                .isInstanceOf(IllegalStateException.class).hasMessage("模型调用失败");
    }

    @Test
    void temperatureRejectsNanBeforeCallingModel() {
        RecordingModel model = new RecordingModel();
        TokenController controller = new TokenController(ChatClient.builder(model));
        assertThatThrownBy(() -> controller.temperatureEffect("你好", Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(model.calls).isZero();
    }

    private static final class RecordingModel implements ChatModel {
        private Prompt prompt;
        private int calls;
        private ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("测试回答"))));

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            this.calls++;
            return this.response;
        }
    }
}
