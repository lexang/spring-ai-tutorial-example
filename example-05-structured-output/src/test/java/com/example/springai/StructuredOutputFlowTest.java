package com.example.springai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StructuredOutputFlowTest {

    private static final String PERSON = "{\"name\":\"张三\",\"age\":25,\"job\":\"工程师\"}";

    @Test
    void defaultEntityAddsFormatToUserAndIgnoresUnknownProperties() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel model = model(prompt -> {
            captured.set(prompt);
            return PERSON.substring(0, PERSON.length() - 1) + ",\"extra\":true}";
        });
        Models.Person person = ChatClient.create(model).prompt().system("系统规则")
            .user("提取人员").call().entity(Models.Person.class);
        assertThat(person.name()).isEqualTo("张三");
        assertThat(captured.get().getSystemMessage().getText()).isEqualTo("系统规则");
        assertThat(captured.get().getUserMessage().getText()).contains("提取人员", "JSON Schema");
    }

    @Test
    void nativeAndValidationComposeWithoutAppendingTextFormat() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel model = model(prompt -> { captured.set(prompt); return PERSON; });
        Models.Person result = ChatClient.create(model).prompt().system("系统规则").user("提取人员")
            .call().entity(Models.Person.class, spec -> spec.useProviderStructuredOutput().validateSchema());
        assertThat(result.age()).isEqualTo(25);
        assertThat(((AnthropicChatOptions) captured.get().getOptions()).getOutputSchema()).contains("name", "age");
        assertThat(captured.get().getUserMessage().getText()).isEqualTo("提取人员");
        assertThat(captured.get().getSystemMessage().getText()).isEqualTo("系统规则");
    }

    @Test
    void defaultAdvisorParameterEnablesNativeAndUnsupportedModelFallsBack() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatClient client = ChatClient.builder(model(prompt -> { captured.set(prompt); return PERSON; }))
            .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT).build();
        client.prompt("提取人员").call().entity(Models.Person.class);
        assertThat(((AnthropicChatOptions) captured.get().getOptions()).getOutputSchema()).isNotBlank();
        ChatModel plain = prompt -> { captured.set(prompt); return response(PERSON); };
        ChatClient.create(plain).prompt("提取人员").call()
            .entity(Models.Person.class, spec -> spec.useProviderStructuredOutput());
        assertThat(captured.get().getOptions()).isNotInstanceOf(AnthropicChatOptions.class);
        assertThat(captured.get().getUserMessage().getText()).contains("JSON Schema");
    }

    @Test
    void noSchemaConverterFallsBackWhenNativeIsRequested() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        List<Models.Person> people = ChatClient.create(model(prompt -> {
            captured.set(prompt); return "张三|25|工程师";
        })).prompt("生成人员").call().entity(new PipeSeparatedPersonConverter(),
                spec -> spec.useProviderStructuredOutput());
        assertThat(people).hasSize(1);
        assertThat(((AnthropicChatOptions) captured.get().getOptions()).getOutputSchema()).isNull();
        assertThat(captured.get().getUserMessage().getText()).contains("name|age|job");
    }

    @Test
    void explicitValidationAdvisorDoesNotReplaceConsumerRegisteredValidation() {
        AtomicInteger advisorCount = new AtomicInteger();
        CallAdvisor inspection = new CallAdvisor() {
            public String getName() { return "ValidationInspection"; }
            public int getOrder() { return 0; }
            public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
                advisorCount.set((int) chain.getCallAdvisors().stream()
                    .filter(StructuredOutputValidationAdvisor.class::isInstance).count());
                return chain.nextCall(request);
            }
        };
        ChatClient.builder(model(prompt -> PERSON)).defaultAdvisors(inspection,
            StructuredOutputValidationAdvisor.builder().outputType(Models.Person.class)
                .advisorOrder(100).maxRepeatAttempts(0).build()).build()
            .prompt("提取人员").call().entity(Models.Person.class, spec -> spec.validateSchema());
        assertThat(advisorCount).hasValue(2);
    }

    @Test
    void validationRunsOnRawTextBeforeLenientConversion() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = model(prompt -> calls.incrementAndGet() == 1
                ? "这是说明：\n```json\n" + PERSON + "\n```" : PERSON);
        Models.Person result = ChatClient.create(model).prompt("提取人员").call()
            .entity(new LenientJsonOutputConverter<>(Models.Person.class), spec -> spec.validateSchema());
        assertThat(result.name()).isEqualTo("张三");
        assertThat(calls).hasValue(2);
    }

    @Test
    void genericMapRetainsValueTypeAndWrappedListSchemaIsObject() {
        BeanOutputConverter<Map<String, Models.Person>> converter = new BeanOutputConverter<>(
            new ParameterizedTypeReference<>() { });
        assertThat(converter.convert("{\"person\":" + PERSON + "}").get("person").age()).isEqualTo(25);
        BeanOutputConverter<Models.FilmographyList> wrapped = new BeanOutputConverter<>(Models.FilmographyList.class);
        assertThat(wrapped.getJsonSchemaMap().get("type")).isEqualTo("object");
        assertThat(wrapped.convert("{\"films\":[{\"actor\":\"演员\",\"movies\":[\"电影\"]}]}"))
            .isEqualTo(new Models.FilmographyList(List.of(new Models.ActorsFilms("演员", List.of("电影")))));
    }

    @Test
    void wrappedNativeEndpointAndBusinessFailuresAreVerifiedOffline() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ChatModel wrapped = model(prompt -> "{\"films\":[{\"actor\":\"演员\",\"movies\":[\"电影\"]}]}");
            var mvc = MockMvcBuilders.standaloneSetup(new StructuredOutputController(
                ChatClient.builder(wrapped), wrapped, factory.getValidator()))
                .setControllerAdvice(new StructuredOutputExceptionHandler()).build();
            mvc.perform(get("/structured/films/native-wrapped")).andExpect(status().isOk())
                .andExpect(jsonPath("films[0].actor").value("演员"));
            ChatModel invalid = model(prompt -> PERSON.replace("25", "999"));
            var invalidMvc = MockMvcBuilders.standaloneSetup(new StructuredOutputController(
                ChatClient.builder(invalid), invalid, factory.getValidator()))
                .setControllerAdvice(new StructuredOutputExceptionHandler()).build();
            invalidMvc.perform(get("/structured/person").param("text", "提取人员"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("requestId").isNotEmpty());
            invalidMvc.perform(get("/structured/person")).andExpect(status().isBadRequest());
            ChatModel lowLevel = model(prompt -> prompt.getUserMessage().getText().contains("comma-separated")
                    ? "Java, Spring" : "{\"numbers\":[1,2]}");
            var lowMvc = MockMvcBuilders.standaloneSetup(new StructuredOutputController(
                ChatClient.builder(lowLevel), lowLevel, factory.getValidator()))
                .setControllerAdvice(new StructuredOutputExceptionHandler()).build();
            lowMvc.perform(get("/structured/keywords/low-level").param("text", "提取关键词"))
                .andExpect(status().isOk()).andExpect(jsonPath("[0]").value("Java"));
            lowMvc.perform(get("/structured/analyze/low-level").param("text", "分析"))
                .andExpect(status().isOk()).andExpect(jsonPath("numbers[1]").value(2));
        }
    }

    private static ChatModel model(Function<Prompt, String> answer) {
        return new ChatModel() {
            public ChatResponse call(Prompt prompt) { return response(answer.apply(prompt)); }
            public ChatOptions getOptions() { return AnthropicChatOptions.builder().build(); }
        };
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

}
