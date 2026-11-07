package com.example.springai;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.template.TemplateRenderer;
import org.springframework.util.Assert;

/**
 * 教学用自定义渲染器，演示如何实现 TemplateRenderer 的 2.0 泛型签名。
 *
 * <p>只扫描原模板一次，参数中的占位符不会二次展开；不提供列表和函数能力。</p>
 */
final class DollarTemplateRenderer implements TemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    @Override
    public String apply(String template, Map<String, ? extends @Nullable Object> variables) {
        Assert.hasText(template, "template 不能为空");
        Assert.notNull(variables, "variables 不能为 null");
        Assert.noNullElements(variables.keySet(), "variables 的键不能为 null");

        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!variables.containsKey(name)) {
                throw new IllegalArgumentException("模板缺少变量：" + name);
            }
            Object value = variables.get(name);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

}
