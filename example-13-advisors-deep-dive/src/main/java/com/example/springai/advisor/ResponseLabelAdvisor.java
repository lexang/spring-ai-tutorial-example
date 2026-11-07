package com.example.springai.advisor;

import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * 演示响应阶段的修改：保留 ChatResponse 元数据，只替换每个 AssistantMessage 的文本。
 */
public final class ResponseLabelAdvisor implements BaseAdvisor {

    private final String label;

    private final int order;

    public ResponseLabelAdvisor(String label, int order) {
        this.label = label;
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (response.chatResponse() == null) {
            return response;
        }

        List<Generation> generations = response.chatResponse().getResults().stream()
            .map(generation -> {
                AssistantMessage output = generation.getOutput();
                if (output == null || output.getClass() != AssistantMessage.class || output.hasToolCalls()
                        || output.getText() == null || output.getText().isBlank()) {
                    return generation;
                }
                AssistantMessage labeled = AssistantMessage.builder()
                    .content(this.label + output.getText())
                    .properties(output.getMetadata())
                    .toolCalls(output.getToolCalls())
                    .media(output.getMedia())
                    .build();
                return new Generation(labeled, generation.getMetadata());
            })
            .toList();

        ChatResponse labeledResponse = ChatResponse.builder()
            .from(response.chatResponse())
            .generations(generations)
            .build();
        return response.mutate().chatResponse(labeledResponse).build();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

}
