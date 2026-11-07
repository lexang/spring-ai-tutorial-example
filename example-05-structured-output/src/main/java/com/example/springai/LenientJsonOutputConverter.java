package com.example.springai;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;

import java.util.regex.Pattern;

/**
 * 提取 Markdown 代码围栏中的 JSON，再委托给 Spring AI 的标准转换器。
 */
public final class LenientJsonOutputConverter<T> implements StructuredOutputConverter<T> {

    private static final Pattern FENCE = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final BeanOutputConverter<T> delegate;

    public LenientJsonOutputConverter(Class<T> targetType) {
        this.delegate = new BeanOutputConverter<>(targetType);
    }

    @Override
    public String getFormat() {
        return this.delegate.getFormat();
    }

    @Override
    public String getJsonSchema() {
        return this.delegate.getJsonSchema();
    }

    @Override
    public T convert(String source) {
        String responseText = StructuredOutputResponseSupport.requireText(source);
        var matcher = FENCE.matcher(responseText);
        String json = matcher.find() ? matcher.group(1).trim() : responseText;
        T result = this.delegate.convert(json);
        return StructuredOutputResponseSupport.requireEntity(result, "JSON 转换结果");
    }
}
