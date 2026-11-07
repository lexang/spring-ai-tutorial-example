package com.example.springai.model;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** 仅用于离线编排测试的确定性文案模型。 */
public final class OfflineContentChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        String json = """
            {"title":"Spring AI 2.0 内容创作实践","article":"Spring AI 通过统一的 ChatModel、ImageModel 和 TextToSpeechModel 接口编排文案、图片与语音。生产系统仍需审核事实、版权和敏感内容。","imagePrompt":"现代简洁的开发者工作台，屏幕展示 Spring AI 多模态工作流，蓝绿色科技插画，不含文字和商标","speechText":"Spring AI 通过统一模型接口编排文案、图片与语音。发布前请完成事实、版权和安全审核。"}
            """;
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

}
