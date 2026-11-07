package com.example.springai.model;

import java.util.List;
import java.util.Locale;

/**
 * 翻译服务的数据模型。
 *
 * <p>所有字段均使用 record 约束，配合 ChatClient 的结构化输出转换为强类型结果。
 * 语言通过 {@link Language} 枚举白名单限制，避免把用户输入直接拼入提示词。</p>
 */
public final class TranslationModels {

    private TranslationModels() {
    }

    /** 受支持的目标语言白名单。 */
    public enum Language {
        ZH("中文"),
        EN("英语"),
        JA("日语"),
        KO("韩语"),
        FR("法语"),
        DE("德语"),
        ES("西班牙语");

        private final String label;

        Language(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public static Language fromCode(String code) {
            if (code == null) {
                throw new IllegalArgumentException("source/target 不能为空");
            }
            String normalized = code.trim().toUpperCase(Locale.ROOT);
            for (Language language : values()) {
                if (language.name().equals(normalized)) {
                    return language;
                }
            }
            throw new IllegalArgumentException("不支持的语言代码：" + code + "，允许的代码：" + allowedCodes());
        }

        public static String allowedCodes() {
            StringBuilder builder = new StringBuilder();
            for (Language language : values()) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(language.name());
            }
            return builder.toString();
        }
    }

    /** 术语表条目：源词 -> 目标语言译名。 */
    public record GlossaryEntry(String source, String target) {
    }

    /** 翻译请求。 */
    public record TranslationRequest(String text, String source, String target, List<GlossaryEntry> glossary) {
    }

    /** 术语命中：记录哪些术语在翻译中被采用。 */
    public record GlossaryHit(String source, String target) {
    }

    /** ChatClient 返回的结构化翻译结果。 */
    public record TranslationResult(String translatedText, List<GlossaryHit> glossaryHits, String notes) {
    }

    /** 用户数据使用 JSON 编码，离线解析器不将数据中的边界词当作协议。 */
    public record TranslationPayload(String text, Language source, Language target, List<GlossaryEntry> glossary) {
    }

}
