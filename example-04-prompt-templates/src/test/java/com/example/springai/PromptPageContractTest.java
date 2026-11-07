package com.example.springai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromptPageContractTest {

    @Test
    void previewReturnsExactLiteralUserTextWithoutCallingModel() throws Exception {
        ChatModel model = prompt -> { throw new AssertionError("预览不得调用模型"); };
        mvc(model).perform(post("/template/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"保留 {name} 与 ${other}\",\"expertise\":\"Spring\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("system").value(org.hamcrest.Matchers.containsString("Spring")))
                .andExpect(jsonPath("user").value("保留 {name} 与 ${other}"));
        mvc(model).perform(post("/template/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"问题\",\"expertise\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postExpertCallsModelAfterSamePromptPreparation() throws Exception {
        mvc(prompt -> response()).perform(post("/template/expert").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"问题\",\"expertise\":\"Spring\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void convenienceMethodsUseFirstSystemLastUserAndLastToolAndReturnEmptyFallback() {
        ToolResponseMessage tool = ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse("tool-007", "lookup", "结果"))).build();
        Prompt prompt = new Prompt(List.of(new SystemMessage("第一条系统"), new SystemMessage("第二条系统"),
                new UserMessage("第一条用户"), new UserMessage("第二条用户"), tool, new AssistantMessage("结束")));
        assertThat(prompt.getSystemMessage().getText()).isEqualTo("第一条系统");
        assertThat(prompt.getUserMessage().getText()).isEqualTo("第二条用户");
        assertThat(prompt.getLastUserOrToolResponseMessage()).isSameAs(tool);
        assertThat(prompt.getSystemMessages()).hasSize(2);
        assertThat(prompt.getUserMessages()).hasSize(2);
        Prompt empty = new Prompt(List.of());
        assertThat(empty.getSystemMessage().getText()).isEmpty();
        assertThat(empty.getUserMessage().getText()).isEmpty();
        assertThat(empty.getLastUserOrToolResponseMessage()).isInstanceOf(UserMessage.class);
    }

    @Test
    void promptConstructorRetainsListWhileExplicitSnapshotPreventsLaterStructuralChanges() {
        List<Message> messages = new ArrayList<>(List.of(new UserMessage("第一条")));
        Prompt shared = new Prompt(messages);
        Prompt snapshot = new Prompt(List.copyOf(messages));
        messages.add(new UserMessage("第二条"));
        assertThat(shared.getInstructions()).hasSize(2);
        assertThat(snapshot.getInstructions()).hasSize(1);
        assertThatThrownBy(() -> snapshot.getInstructions().add(new UserMessage("第三条")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void systemTemplateInheritedOptionsAndMediaOverloadsStillCreateUserMessages() {
        SystemPromptTemplate template = new SystemPromptTemplate("系统文本");
        ChatOptions options = ChatOptions.builder().temperature(0.2).build();
        assertThat(template.create().getSystemMessage().getText()).isEqualTo("系统文本");
        assertThat(template.create(Map.of()).getInstructions().get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(template.create(options).getInstructions().get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(template.create(Map.of(), options).getInstructions().get(0).getMessageType()).isEqualTo(MessageType.USER);
        Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1 }).build();
        assertThat(template.createMessage(List.of(image))).isInstanceOf(UserMessage.class);
        assertThat(template.create().mutate().chatOptions(options).build().getInstructions().get(0).getMessageType())
            .isEqualTo(MessageType.SYSTEM);
    }

    @Test
    void renderingDoesNotMutateDefaultsOrInterpretPlaceholderTextInsideValues() {
        PromptTemplate template = PromptTemplate.builder().template("你好{name}：{question}")
            .variables(Map.of("name", "默认名字", "question", "默认问题")).build();
        assertThat(template.render(Map.of("name", "张三", "question", "{name} ${other}")))
            .isEqualTo("你好张三：{name} ${other}");
        assertThat(template.render()).isEqualTo("你好默认名字：默认问题");
        assertThat(template.mutate().variables(Map.of("name", "李四", "question", "问题")).build().render())
            .isEqualTo("你好李四：问题");
        assertThat(template.render()).contains("默认名字");
    }

    @Test
    void dollarRendererScansOriginalTemplateOnceAndQuotesDollarAndBackslash() {
        DollarTemplateRenderer renderer = new DollarTemplateRenderer();
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "${other} $1 \\ 路径");
        variables.put("other", "不得二次展开");
        assertThat(renderer.apply("你好 ${name}", variables)).isEqualTo("你好 ${other} $1 \\ 路径");
        assertThatThrownBy(() -> renderer.apply("${missing}", variables))
            .hasMessage("模板缺少变量：missing");
    }

    @Test
    void resourceVariablesUseUtf8AndBuilderRejectsTwoSources() {
        var resource = new ByteArrayResource("中文资料".getBytes(StandardCharsets.UTF_8));
        PromptTemplate template = new PromptTemplate("请参考：{context}");
        assertThat(template.render(Map.of("context", resource))).isEqualTo("请参考：中文资料");
        assertThat(new PromptTemplate("静态模板").createMessage()).isInstanceOf(UserMessage.class);
        assertThatThrownBy(() -> PromptTemplate.builder().template("文本").resource(resource).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stFunctionValidationSwitchAddsFunctionNamesToRequiredKeys() {
        assertThat(StTemplateRenderer.builder().build().apply("{length}", Map.of())).isEmpty();
        assertThatThrownBy(() -> StTemplateRenderer.builder().validateStFunctions().build()
            .apply("{length}", Map.of())).isInstanceOf(IllegalStateException.class).hasMessageContaining("length");
    }

    @Test
    void expertResourceBuildsSystemAndLiteralUserMessagesAndCallIsLazy() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel model = prompt -> {
            captured.set(prompt);
            return response();
        };
        ChatClient.CallResponseSpec pending = ChatClient.create(model).prompt("延迟调用").call();
        assertThat(captured.get()).isNull();
        assertThat(pending.content()).isEqualTo("已回答");
        ExpertAssistant expert = new ExpertAssistant(ChatClient.builder(model),
                new ClassPathResource("prompts/system-expert.st"));
        assertThat(expert.ask("含 {question} 与 JSON：{\"a\":1}", "Spring AI")).isEqualTo("已回答");
        assertThat(captured.get().getInstructions()).extracting(Message::getMessageType)
            .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(captured.get().getSystemMessage().getText()).contains("Spring AI", "中文");
        assertThat(captured.get().getUserMessage().getText()).isEqualTo("含 {question} 与 JSON：{\"a\":1}");
    }

    @Test
    void missingParametersAndMalformedJsonReturnChinese400WithoutModelCall() throws Exception {
        ChatModel model = prompt -> { throw new AssertionError("输入无效时不得调用模型"); };
        MockMvc mvc = mvc(model);
        mvc.perform(get("/template/basic").param("topic", "Spring AI"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("error").value("请求参数格式不正确"));
        mvc.perform(post("/template/translate").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("error").value("请求参数格式不正确"));
    }

    @Test
    void expertHttpEndpointAndAllResourceTemplatesAreRunnable() throws Exception {
        MockMvc mvc = mvc(prompt -> response());
        mvc.perform(get("/template/expert").param("question", "什么是 Prompt").param("expertise", "Spring AI"))
            .andExpect(status().isOk());
        for (String name : List.of("translate", "code-review", "system-role", "article", "outline", "customer-service")) {
            PromptTemplate template = new PromptTemplate(new ClassPathResource("prompts/" + name + ".st"));
            assertThat(template.render(Map.ofEntries(
                Map.entry("text", "Hello"), Map.entry("sourceLanguage", "英语"), Map.entry("targetLanguage", "中文"),
                Map.entry("code", "int a = 1;"), Map.entry("language", "Java"), Map.entry("role", "专家"),
                Map.entry("domain", "Spring"), Map.entry("docType", "技术"), Map.entry("topic", "AI"),
                Map.entry("length", 800), Map.entry("style", "博客"), Map.entry("keywords", "Spring AI"),
                Map.entry("customerName", "张三"), Map.entry("customerLevel", "VIP"), Map.entry("queryType", "查询"),
                Map.entry("query", "查询订单"), Map.entry("orderHistory", "暂无订单"), Map.entry("currentTime", "现在"))))
                .isNotBlank();
        }
    }

    private static MockMvc mvc(ChatModel model) {
        ExpertAssistant expert = new ExpertAssistant(ChatClient.builder(model), new ClassPathResource("prompts/system-expert.st"));
        TemplateController controller = new TemplateController(ChatClient.builder(model), model,
            new TranslationService(ChatClient.builder(model), new ClassPathResource("prompts/translate.st")),
            new ContentGenerationService(ChatClient.builder(model)),
            new CustomerServiceBot(ChatClient.builder(model), new ClassPathResource("prompts/customer-service.st")), expert);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new TemplateExceptionHandler()).build();
    }

    private static ChatResponse response() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("已回答"))));
    }

}
