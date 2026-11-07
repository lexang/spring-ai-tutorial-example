package com.example.springai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonExtractionTest {

    private static final String PERSON = "{\"name\":\"张三\",\"age\":25,\"job\":\"工程师\"}";

    @Test
    void schemaRetryAccumulatesUsageAndDefaultModeIsSchema() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> response(calls.incrementAndGet() == 1 ? "不是 JSON" : PERSON);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var controller = new PersonExtractionController(ChatClient.builder(model), factory.getValidator());
            var result = controller.extract(new PersonExtractionController.ExtractionRequest("张三，25岁，工程师", null));
            assertThat(calls).hasValue(2);
            assertThat(result.person().age()).isEqualTo(25);
            assertThat(result.reliability()).isEqualTo(PersonExtractionController.Reliability.SCHEMA);
            assertThat(result.totalTokens()).isEqualTo(30);
        }
    }

    @Test
    void nativeModeUsesProviderSchemaButPromptModeDoesNotRetry() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) { captured.set(prompt); return response(PERSON); }
            public ChatOptions getOptions() { return AnthropicChatOptions.builder().build(); }
        };
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var controller = new PersonExtractionController(ChatClient.builder(model), factory.getValidator());
            controller.extract(new PersonExtractionController.ExtractionRequest("张三 {literal}", PersonExtractionController.Reliability.NATIVE));
            assertThat(((AnthropicChatOptions) captured.get().getOptions()).getOutputSchema()).contains("name", "age");
            assertThat(captured.get().getUserMessage().getText()).contains("{literal}");
            AtomicInteger calls = new AtomicInteger();
            var invalid = new PersonExtractionController(ChatClient.builder(prompt -> { calls.incrementAndGet(); return response("不是 JSON"); }), factory.getValidator());
            assertThatThrownBy(() -> invalid.extract(new PersonExtractionController.ExtractionRequest("描述", PersonExtractionController.Reliability.PROMPT)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void businessValidationRejectsMissingAndOutOfRangeAge() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (String json : List.of(PERSON.replace("25", "999"), PERSON.replace("25", "null"))) {
                var controller = new PersonExtractionController(ChatClient.builder(prompt -> response(json)), factory.getValidator());
                assertThatThrownBy(() -> controller.extract(new PersonExtractionController.ExtractionRequest("描述", PersonExtractionController.Reliability.PROMPT)))
                        .isInstanceOf(InvalidStructuredOutputResponseException.class);
            }
        }
    }

    @Test
    void blankJsonAndUnknownModeReturn400BeforeModelCall() throws Exception {
        ChatModel model = prompt -> { throw new AssertionError("输入无效时不应调用模型"); };
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var mvc = MockMvcBuilders.standaloneSetup(new PersonExtractionController(ChatClient.builder(model), factory.getValidator()))
                    .setControllerAdvice(new StructuredOutputExceptionHandler()).build();
            for (String body : List.of("{", "{\"description\":\" \"}", "{\"description\":\"描述\",\"reliability\":\"invalid\"}")) {
                mvc.perform(post("/api/extraction/person").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().model("fixture").usage(new DefaultUsage(10, 5, 15)).build());
    }
}
