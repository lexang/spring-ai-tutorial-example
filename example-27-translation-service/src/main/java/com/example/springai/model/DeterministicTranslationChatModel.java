package com.example.springai.model;

import java.util.ArrayList;
import java.util.List;
import com.example.springai.model.TranslationModels.GlossaryHit;
import com.example.springai.model.TranslationModels.TranslationPayload;
import com.example.springai.model.TranslationModels.TranslationResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.util.JsonHelper;

/** 离线模型只替换术语并附上语言标记，不进行真实语言翻译。 */
public final class DeterministicTranslationChatModel implements ChatModel {
    @Override
    public ChatResponse call(Prompt prompt) {
        String text = prompt.getUserMessage().getText();
        String start = "任务数据JSON开始\n";
        int from = text.indexOf(start) + start.length();
        int to = text.indexOf("\n任务数据JSON结束", from);
        if (from < start.length() || to < from) throw new IllegalArgumentException("翻译任务 JSON 边界无效");
        JsonHelper json = new JsonHelper();
        TranslationPayload input = json.fromJson(text.substring(from, to), TranslationPayload.class);
        if (input == null) throw new IllegalArgumentException("翻译任务不能为空");
        String translated = input.text();
        List<GlossaryHit> hits = new ArrayList<>();
        for (var entry : input.glossary()) {
            if (input.text().contains(entry.source())) {
                translated = translated.replace(entry.source(), entry.target());
                hits.add(new GlossaryHit(entry.source(), entry.target()));
            }
        }
        translated += input.target() == TranslationModels.Language.EN
            ? " (offline translation: contract demonstration)" : "（离线翻译标记：" + input.target().label() + "）";
        String output = json.toJson(new TranslationResult(translated, hits, "离线确定性模型仅验证术语与数据边界，不是翻译效果"), false);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(output))));
    }
}
