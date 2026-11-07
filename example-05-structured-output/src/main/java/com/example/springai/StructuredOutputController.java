package com.example.springai;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结构化输出控制器。
 *
 * <p>端点同时覆盖高层 ChatClient API 和官方文档中的低层转换器 API。所有模型
 * 返回值都在进入业务逻辑前检查，因为 Spring AI 2.0 的相关方法明确允许空结果。</p>
 */
@RestController
@RequestMapping("/structured")
public class StructuredOutputController {

    private final ChatClient chatClient;

    private final ChatModel chatModel;

    private final Validator validator;

    public StructuredOutputController(ChatClient.Builder builder, ChatModel chatModel, Validator validator) {
        this.chatClient = builder.build();
        this.chatModel = chatModel;
        this.validator = validator;
    }

    /** 低层 BeanOutputConverter 示例使用的目标类型。 */
    record ActorsFilms(String actor, List<String> movies) {
    }

    /** 默认提示词约束：entity(Class) 会生成 Schema 并将结果转换为记录。 */
    @GetMapping("/person")
    public Models.Person extractPerson(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        Models.Person person = chatClient.prompt()
                .user("提取人员信息：\n" + input)
                .call()
                .entity(Models.Person.class);
        return validateModelOutput(person);
    }

    /** 结构化 Schema 校验失败时，把错误反馈给模型并自动重试。 */
    @GetMapping("/person/validated")
    public Models.Person extractValidatedPerson(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        Models.Person person = chatClient.prompt()
                .user("提取人员信息：\n" + input)
                .call()
                .entity(Models.Person.class, spec -> spec.validateSchema());
        return validateModelOutput(person);
    }

    /**
     * 将 JSON Schema 交给供应商的原生结构化输出能力。
     *
     * <p>该能力是供应商和模型相关的可选约束；MiniMax Anthropic 兼容端点是否接受
     * 当前 Schema，应以实际服务响应为准。</p>
     */
    @GetMapping("/person/native")
    public Models.Person extractNativePerson(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        Models.Person person = chatClient.prompt()
                .user("提取人员信息：\n" + input)
                .call()
                .entity(Models.Person.class, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());
        return validateModelOutput(person);
    }

    /** 自定义转换器：允许模型在 JSON 外包裹一段 Markdown 说明。 */
    @GetMapping("/person/lenient")
    public Models.Person extractLenientPerson(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        Models.Person person = chatClient.prompt()
                .user("提取人员信息；可以先给一句简短说明，再给出 JSON：\n" + input)
                .call()
                .entity(new LenientJsonOutputConverter<>(Models.Person.class));
        return validateModelOutput(person);
    }

    /** 同时取得结构化实体和完整 ChatResponse（包括用量、元数据等）。 */
    @GetMapping("/person/response")
    public ResponseEntity<ChatResponse, Models.Person> extractPersonWithResponse(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        ResponseEntity<ChatResponse, Models.Person> result = chatClient.prompt()
                .user("提取人员信息：\n" + input)
                .call()
                .responseEntity(Models.Person.class, spec -> spec.validateSchema());
        StructuredOutputResponseSupport.requireResponseEntity(result, "人员");
        validateModelOutput(result.entity());
        return result;
    }

    /** 低层 ChatModel + BeanOutputConverter 写法。 */
    @GetMapping("/films/low-level")
    public ActorsFilms extractFilmsLowLevel(@RequestParam(defaultValue = "Tom Hanks") String actor) {
        String actorName = StructuredOutputRequestValidation.requireText(actor, "actor");
        BeanOutputConverter<ActorsFilms> converter = new BeanOutputConverter<>(ActorsFilms.class);
        String template = """
                生成演员 {actor} 的 5 部电影作品。
                {format}
                """;
        Prompt prompt = PromptTemplate.builder()
                .template(template)
                .variables(Map.of("actor", actorName, "format", converter.getFormat()))
                .build()
                .create();
        ChatResponse response = StructuredOutputResponseSupport.requireResponse(chatModel.call(prompt));
        String content = StructuredOutputResponseSupport.requireText(response);
        Generation generation = response.getResult();
        // 上面的 requireText 已检查 Generation 和输出，这里只演示低层对象的完整访问路径。
        StructuredOutputResponseSupport.requireEntity(generation, "模型生成结果");
        return StructuredOutputResponseSupport.requireEntity(converter.convert(content), "电影作品");
    }

    /** 原生 Schema 列表包装示例；兼容供应商是否支持仍需真实验证。 */
    @GetMapping("/films/native-wrapped")
    public Models.FilmographyList extractNativeFilmographies() {
        Models.FilmographyList result = this.chatClient.prompt()
                .user("生成两位演员的电影作品，用 films 字段包装列表")
                .call()
                .entity(Models.FilmographyList.class, spec -> spec
                        .useProviderStructuredOutput().validateSchema());
        return validateModelOutput(result);
    }

    /** ParameterizedTypeReference 用于保留 List<Person> 的泛型信息。 */
    @GetMapping("/persons")
    public List<Models.Person> extractPersons() {
        List<Models.Person> people = chatClient.prompt()
                .user("生成 3 个人员信息，包含姓名、年龄和职业")
                .call()
                .entity(new ParameterizedTypeReference<List<Models.Person>>() {
                });
        List<Models.Person> result = StructuredOutputResponseSupport.requireNonEmpty(people, "人员列表");
        result.forEach(this::validateModelOutput);
        return result;
    }

    /** 无 JSON Schema 的非 JSON 自定义格式。 */
    @GetMapping("/persons/pipe")
    public List<Models.Person> extractPipeSeparatedPersons() {
        List<Models.Person> people = chatClient.prompt()
                .user("生成 3 个人员信息")
                .call()
                .entity(new PipeSeparatedPersonConverter());
        List<Models.Person> result = StructuredOutputResponseSupport.requireNonEmpty(people, "人员列表");
        result.forEach(this::validateModelOutput);
        return result;
    }

    /** 复杂嵌套记录。 */
    @GetMapping("/employee")
    public Models.Employee extractEmployee(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        Models.Employee employee = chatClient.prompt()
                .user("提取员工完整信息：\n" + input)
                .call()
                .entity(Models.Employee.class);
        return StructuredOutputResponseSupport.requireEntity(employee, "员工");
    }

    /** ListOutputConverter：转换为逗号分隔的字符串列表。 */
    @GetMapping("/keywords")
    public List<String> extractKeywords(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        ListOutputConverter converter = new ListOutputConverter(new DefaultConversionService());
        List<String> keywords = chatClient.prompt()
                .user("从以下文本提取关键词：\n" + input)
                .call()
                .entity(converter);
        return StructuredOutputResponseSupport.requireNonEmpty(keywords, "关键词列表");
    }

    /** MapOutputConverter：转换为 Map<String,Object>。 */
    @GetMapping("/analyze")
    public Map<String, Object> analyzeText(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        MapOutputConverter converter = new MapOutputConverter();
        Map<String, Object> result = chatClient.prompt()
                .user("""
                        分析以下文本，并返回 sentiment、topic、keywords 和 wordCount 字段：
                        %s
                        """.formatted(input))
                .call()
                .entity(converter);
        return StructuredOutputResponseSupport.requireNonEmptyMap(result, "分析结果");
    }

    /** 低层 Map 转换需要显式加入格式说明。 */
    @GetMapping("/analyze/low-level")
    public Map<String, Object> analyzeLowLevel(@RequestParam String text) {
        Map<String, Object> result = convertLowLevel(text, new MapOutputConverter());
        return StructuredOutputResponseSupport.requireNonEmptyMap(result, "低层分析结果");
    }

    /** 低层 List 转换同样由应用构造 Prompt 并读取完整文本。 */
    @GetMapping("/keywords/low-level")
    public List<String> keywordsLowLevel(@RequestParam String text) {
        return StructuredOutputResponseSupport.requireNonEmpty(
                convertLowLevel(text, new ListOutputConverter()), "低层关键词列表");
    }

    private <T> T convertLowLevel(String text, StructuredOutputConverter<T> converter) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        Prompt prompt = PromptTemplate.builder().template("处理以下文本：{text}\n{format}")
                .variables(Map.of("text", input, "format", converter.getFormat())).build().create();
        ChatResponse response = StructuredOutputResponseSupport.requireResponse(this.chatModel.call(prompt));
        return converter.convert(StructuredOutputResponseSupport.requireText(response));
    }

    /** 产品信息提取，同时演示请求体 @Valid。 */
    @PostMapping("/product")
    public Models.Product extractProduct(@Valid @RequestBody Models.DescriptionRequest request) {
        Models.Product product = chatClient.prompt()
                .system("你是产品信息提取专家，从描述中提取结构化信息")
                .user("提取产品信息：\n" + request.description().trim())
                .call()
                .entity(Models.Product.class, spec -> spec.validateSchema());
        return StructuredOutputResponseSupport.requireEntity(product, "产品");
    }

    /** 简历解析，同时验证模型输出中的邮箱和手机号业务约束。 */
    @PostMapping("/resume")
    public Models.Resume parseResume(@Valid @RequestBody Models.ResumeRequest request) {
        Models.Resume resume = chatClient.prompt()
                .system("你是简历解析专家，准确提取简历中的所有信息")
                .user("解析简历：\n" + request.resumeText().trim())
                .call()
                .entity(Models.Resume.class, spec -> spec.validateSchema());
        return validateModelOutput(resume);
    }

    /** ListOutputConverter 的任务分解场景。 */
    @GetMapping("/tasks")
    public List<String> breakdownTask(@RequestParam String task) {
        String input = StructuredOutputRequestValidation.requireText(task, "task");
        ListOutputConverter converter = new ListOutputConverter(new DefaultConversionService());
        List<String> steps = chatClient.prompt()
                .user("将以下任务分解为具体步骤：\n" + input)
                .call()
                .entity(converter);
        return StructuredOutputResponseSupport.requireNonEmpty(steps, "任务步骤");
    }

    /** 最简洁的 entity(Class) 写法。 */
    @GetMapping("/entity")
    public Models.Person useEntityMethod(@RequestParam String text) {
        String input = StructuredOutputRequestValidation.requireText(text, "text");
        Models.Person person = chatClient.prompt()
                .user("提取人员信息：\n" + input)
                .call()
                .entity(Models.Person.class);
        return validateModelOutput(person);
    }

    private <T> T validateModelOutput(T value) {
        T actualValue = StructuredOutputResponseSupport.requireEntity(value, "结构化实体");
        Set<ConstraintViolation<T>> violations = validator.validate(actualValue);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .collect(Collectors.joining(", "));
            throw new InvalidStructuredOutputResponseException("模型输出未通过业务校验：" + errors);
        }
        return actualValue;
    }

}
