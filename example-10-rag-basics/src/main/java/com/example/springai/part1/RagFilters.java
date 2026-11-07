package com.example.springai.part1;

import java.util.Optional;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.StringUtils;

/**
 * 使用结构化 DSL 构造可移植的元数据过滤表达式。
 */
public final class RagFilters {

    private RagFilters() {
    }

    public static Optional<Filter.Expression> productAndCategory(String product, String category) {
        String normalizedProduct = normalize(product);
        String normalizedCategory = normalize(category);
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        if (normalizedProduct != null && normalizedCategory != null) {
            return Optional.of(builder.and(
                    builder.eq("product", normalizedProduct),
                    builder.eq("category", normalizedCategory)
            ).build());
        }
        if (normalizedProduct != null) {
            return Optional.of(builder.eq("product", normalizedProduct).build());
        }
        if (normalizedCategory != null) {
            return Optional.of(builder.eq("category", normalizedCategory).build());
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
