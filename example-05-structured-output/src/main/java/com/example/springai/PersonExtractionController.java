package com.example.springai;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 博客主线：请求验证、Schema/原生选项、实体转换及业务校验。 */
@RestController
@RequestMapping("/api/extraction/person")
public final class PersonExtractionController {

    private final ChatClient chatClient;
    private final Validator validator;

    public PersonExtractionController(ChatClient.Builder builder, Validator validator) {
        this.chatClient = builder.defaultSystem("从用户提供的描述中提取人员信息。不要猜测缺失的字段。")
                .build();
        this.validator = validator;
    }

    @PostMapping
    public ExtractionReply extract(@Valid @RequestBody ExtractionRequest request) {
        Reliability reliability = request.reliability() == null ? Reliability.SCHEMA : request.reliability();
        ResponseEntity<ChatResponse, Person> result = this.chatClient.prompt()
                .user(user -> user.text("请提取以下描述中的姓名、年龄和职业：\n{description}")
                        .param("description", request.description()))
                .call()
                .responseEntity(Person.class, spec -> {
                    if (reliability != Reliability.PROMPT) spec.validateSchema();
                    if (reliability == Reliability.NATIVE) spec.useProviderStructuredOutput();
                });
        StructuredOutputResponseSupport.requireResponseEntity(result, "人员");
        Person person = result.entity();
        if (!this.validator.validate(person).isEmpty()) {
            throw new InvalidStructuredOutputResponseException("人员输出未通过姓名、年龄与职业校验");
        }
        Usage usage = result.response().getMetadata().getUsage();
        boolean reported = usage != null && !(usage instanceof EmptyUsage);
        return new ExtractionReply(person, reliability, result.response().getMetadata().getModel(),
                reported, reported ? usage.getTotalTokens() : null);
    }

    public enum Reliability { PROMPT, SCHEMA, NATIVE }

    public record ExtractionRequest(
            @NotBlank(message = "description 不能为空")
            @Size(max = 8000, message = "description 不能超过 8000 个字符") String description,
            Reliability reliability) { }

    public record Person(
            @NotBlank(message = "姓名不能为空") String name,
            @NotNull(message = "年龄不能为空")
            @Min(value = 0, message = "年龄不能小于 0")
            @Max(value = 150, message = "年龄不能大于 150") Integer age,
            @NotBlank(message = "职业不能为空") String job) { }

    public record ExtractionReply(Person person, Reliability reliability, String model,
            boolean usageReported, Integer totalTokens) { }
}
