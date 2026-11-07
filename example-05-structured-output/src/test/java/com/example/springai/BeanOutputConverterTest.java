package com.example.springai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanOutputConverterTest {

    @Test
    void convertsJsonToRecordAndExposesSchema() {
        BeanOutputConverter<Models.Person> converter = new BeanOutputConverter<>(Models.Person.class);

        Models.Person person = converter.convert("""
                {"name":"张三","age":25,"job":"软件工程师"}
                """);

        assertThat(person).isEqualTo(new Models.Person("张三", 25, "软件工程师"));
        assertThat(converter.getJsonSchema()).contains("name", "age", "job", "$schema");
    }

    @Test
    void convertsGenericList() {
        BeanOutputConverter<List<Models.Person>> converter = new BeanOutputConverter<>(
                new ParameterizedTypeReference<>() {
                });

        List<Models.Person> people = converter.convert("""
                [{"name":"张三","age":25,"job":"工程师"}]
                """);

        assertThat(people).containsExactly(new Models.Person("张三", 25, "工程师"));
    }

    @Test
    void defaultBeanConverterCleansAStandaloneFenceAndThinkingTag() {
        BeanOutputConverter<Models.Person> converter = new BeanOutputConverter<>(Models.Person.class);

        Models.Person person = converter.convert("""
                <think>内部推理不会成为 JSON 内容</think>
                ```json
                {"name":"李四","age":30,"job":"产品经理"}
                ```
                """);

        assertThat(person).isEqualTo(new Models.Person("李四", 30, "产品经理"));
    }

    @Test
    void defaultBeanConverterStillRejectsProseBeforeFence() {
        BeanOutputConverter<Models.Person> converter = new BeanOutputConverter<>(Models.Person.class);

        assertThatThrownBy(() -> converter.convert("""
                下面是提取结果：
                ```json
                {"name":"王五","age":28,"job":"数据分析师"}
                ```
                """))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void lenientConverterExtractsJsonFromProseAndDelegatesSchema() {
        LenientJsonOutputConverter<Models.Person> converter =
                new LenientJsonOutputConverter<>(Models.Person.class);

        Models.Person person = converter.convert("""
                下面是提取结果：
                ```JSON
                {"name":"王五","age":28,"job":"数据分析师"}
                ```
                """);

        assertThat(person).isEqualTo(new Models.Person("王五", 28, "数据分析师"));
        assertThat(converter.getJsonSchema()).contains("name", "age", "job");
    }

    @Test
    void convertersRejectEmptyCustomInputWithChineseBoundaryError() {
        LenientJsonOutputConverter<Models.Person> converter =
                new LenientJsonOutputConverter<>(Models.Person.class);

        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(InvalidStructuredOutputResponseException.class)
                .hasMessage("模型未返回可用的文本内容");
    }

    @Test
    void propertyOrderIsReflectedInGeneratedSchema() {
        BeanOutputConverter<Models.Employee> converter = new BeanOutputConverter<>(Models.Employee.class);
        String schema = converter.getJsonSchema();

        assertThat(schema.indexOf("name")).isLessThan(schema.indexOf("age"));
        assertThat(schema.indexOf("age")).isLessThan(schema.indexOf("department"));
        assertThat(schema).contains("int32");
    }

    @Test
    void listAndMapConvertersHaveDifferentContracts() {
        ListOutputConverter listConverter = new ListOutputConverter(new DefaultConversionService());
        MapOutputConverter mapConverter = new MapOutputConverter();

        assertThat(listConverter.convert("Java, Spring, AI")).containsExactly("Java", "Spring", "AI");
        assertThat(listConverter.getJsonSchema()).isEmpty();

        Map<String, Object> result = mapConverter.convert("```json\n{\"sentiment\":\"positive\"}\n```");
        assertThat(result).containsEntry("sentiment", "positive");
        assertThat(mapConverter.getJsonSchema()).isEmpty();
    }

    @Test
    void convertsACompleteNonJsonFormatWithoutAdvertisingSchema() {
        PipeSeparatedPersonConverter converter = new PipeSeparatedPersonConverter();

        List<Models.Person> people = converter.convert("""
                张三|25|工程师
                李四|30|产品经理
                """);

        assertThat(people).containsExactly(
                new Models.Person("张三", 25, "工程师"),
                new Models.Person("李四", 30, "产品经理"));
        assertThat(converter.getJsonSchema()).isEmpty();
    }

    @Test
    void schemaValidationRejectsAConverterWithoutJsonSchemaBeforeCallingModel() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Schema 缺失时不应调用模型");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.error(new AssertionError("本测试不应进入流式调用"));
            }

            @Override
            public ChatOptions getOptions() {
                return ChatOptions.builder().build();
            }
        };
        ChatClient client = ChatClient.create(chatModel);

        assertThatThrownBy(() -> client.prompt()
                .user("生成一名人员")
                .call()
                .entity(new PipeSeparatedPersonConverter(), spec -> spec.validateSchema()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either outputType or outputJsonSchema must be set");
    }

    @Test
    void defaultSchemaValidationAllowsThreeRetriesAfterTheInitialCall() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                calls.incrementAndGet();
                return response("这不是 JSON");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.error(new AssertionError("Schema 校验不支持流式调用"));
            }

            @Override
            public ChatOptions getOptions() {
                return ChatOptions.builder().build();
            }
        };

        assertThatThrownBy(() -> ChatClient.create(chatModel)
                .prompt()
                .user("生成一名人员")
                .call()
                .entity(Models.Person.class, spec -> spec.validateSchema()))
                .isInstanceOf(RuntimeException.class);
        assertThat(calls).hasValue(4);
    }

    @Test
    void responseEntityKeepsNullableEntityWhenModelHasNoGeneration() {
        ChatResponse emptyResponse = new ChatResponse(List.of());
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return emptyResponse;
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(emptyResponse);
            }

            @Override
            public ChatOptions getOptions() {
                return ChatOptions.builder().build();
            }
        };

        ResponseEntity<ChatResponse, Models.Person> result = ChatClient.create(chatModel)
                .prompt()
                .user("生成一名人员")
                .call()
                .responseEntity(Models.Person.class);

        assertThat(result.response()).isSameAs(emptyResponse);
        assertThat(result.entity()).isNull();
    }

    @Test
    void schemaValidationAccumulatesUsageAcrossFailedAndSuccessfulAttempts() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> {
            int round = calls.incrementAndGet();
            String content = round == 1 ? "不是 JSON"
                    : "{\"name\":\"张三\",\"age\":25,\"job\":\"工程师\"}";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))),
                    ChatResponseMetadata.builder()
                            .usage(new DefaultUsage(10, 5, 15))
                            .build());
        };

        ResponseEntity<ChatResponse, Models.Person> result = ChatClient.create(model)
                .prompt("提取人员")
                .call()
                .responseEntity(Models.Person.class, spec -> spec.validateSchema());

        assertThat(calls).hasValue(2);
        assertThat(result.entity()).isEqualTo(new Models.Person("张三", 25, "工程师"));
        assertThat(result.response().getMetadata().getUsage().getPromptTokens()).isEqualTo(20);
        assertThat(result.response().getMetadata().getUsage().getCompletionTokens()).isEqualTo(10);
        assertThat(result.response().getMetadata().getUsage().getTotalTokens()).isEqualTo(30);
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

}
