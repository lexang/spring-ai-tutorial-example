package com.example.springai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * 教学用内存对话服务：同一会话串行调用，成功后原子提交完整的一轮消息。
 */
@Service
public class ConversationService {

    static final int MAX_TURNS = 20;

    private static final SystemMessage DEFAULT_SYSTEM_MESSAGE =
            new SystemMessage("你是一个友好的 AI 助手");

    private final ChatClient chatClient;

    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();

    public ConversationService(ChatClient.Builder builder) {
        this.chatClient = builder.clone().build();
    }

    /**
     * 发送消息。模型失败或返回空内容时，不会把半个 turn 写入历史。
     */
    public String chat(String conversationId, String userMessage) {
        String actualConversationId = DemoRequestValidation.requireIdentifier(conversationId, "conversationId");
        String actualUserMessage = DemoRequestValidation.requireText(userMessage, "message");
        ConversationState state = lockCurrentConversation(actualConversationId);
        try {
            UserMessage newUserMessage = new UserMessage(actualUserMessage);
            List<Message> promptMessages = new ArrayList<>(state.messages);
            promptMessages.add(newUserMessage);

            String response = ChatResponseSupport.requireContent(this.chatClient.prompt()
                    .messages(List.copyOf(promptMessages))
                    .call()
                    .content());

            state.messages.add(newUserMessage);
            state.messages.add(new AssistantMessage(response));
            trimOldestTurns(state.messages);
            return response;
        }
        finally {
            state.lock.unlock();
        }
    }

    private ConversationState lockCurrentConversation(String conversationId) {
        while (true) {
            ConversationState state = this.conversations.computeIfAbsent(
                    conversationId, ignored -> new ConversationState());
            state.lock.lock();
            if (this.conversations.get(conversationId) == state) {
                return state;
            }
            // 等锁期间 reset 可能移除了旧状态；必须重取当前会话再调用模型。
            state.lock.unlock();
        }
    }

    /**
     * 返回历史快照，调用方无法修改内部列表。
     */
    public List<Map<String, String>> getHistory(String conversationId) {
        String actualConversationId = DemoRequestValidation.requireIdentifier(conversationId, "conversationId");
        ConversationState state = this.conversations.get(actualConversationId);
        if (state == null) {
            return List.of();
        }

        state.lock.lock();
        try {
            return state.messages.stream()
                    .filter(message -> message instanceof UserMessage || message instanceof AssistantMessage)
                    .map(ConversationService::toHistoryEntry)
                    .toList();
        }
        finally {
            state.lock.unlock();
        }
    }

    /**
     * 若有进行中的调用，等待该调用结束后再移除会话。
     */
    public void reset(String conversationId) {
        String actualConversationId = DemoRequestValidation.requireIdentifier(conversationId, "conversationId");
        ConversationState state = this.conversations.get(actualConversationId);
        if (state == null) {
            return;
        }

        state.lock.lock();
        try {
            this.conversations.remove(actualConversationId, state);
        }
        finally {
            state.lock.unlock();
        }
    }

    public List<String> getConversationIds() {
        return this.conversations.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static Map<String, String> toHistoryEntry(Message message) {
        Map<String, String> result = new LinkedHashMap<>();
        if (message instanceof UserMessage userMessage) {
            result.put("role", "user");
            result.put("content", userMessage.getText());
        }
        else if (message instanceof AssistantMessage assistantMessage) {
            result.put("role", "assistant");
            result.put("content", assistantMessage.getText());
        }
        return result;
    }

    private static void trimOldestTurns(List<Message> messages) {
        int maxMessages = 1 + MAX_TURNS * 2;
        while (messages.size() > maxMessages) {
            // 索引 0 始终保留 SystemMessage；按 user/assistant 对删除最旧 turn。
            messages.remove(1);
            if (messages.size() > 1) {
                messages.remove(1);
            }
        }
    }

    private static final class ConversationState {

        private final ReentrantLock lock = new ReentrantLock(true);

        private final List<Message> messages = new ArrayList<>(List.of(DEFAULT_SYSTEM_MESSAGE));

    }

}
