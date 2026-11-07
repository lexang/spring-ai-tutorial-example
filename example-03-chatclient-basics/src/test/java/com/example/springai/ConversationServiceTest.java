package com.example.springai;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationServiceTest {

    @Test
    void aQueuedCallAfterResetUsesTheNewConversationState() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        ChatModel model = prompt -> {
            if (firstCall.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("测试等待超时");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return response("响应");
        };
        ConversationService service = new ConversationService(ChatClient.builder(model));
        ExecutorService firstExecutor = Executors.newSingleThreadExecutor();
        var reset = new java.util.concurrent.FutureTask<Void>(() -> { service.reset("reset-race"); return null; });
        var next = new java.util.concurrent.FutureTask<String>(() -> service.chat("reset-race", "新消息"));
        Thread resetThread = new Thread(reset);
        Thread nextThread = new Thread(next);
        try {
            Future<String> first = firstExecutor.submit(() -> service.chat("reset-race", "旧消息"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            resetThread.start();
            awaitWaiting(resetThread);
            nextThread.start();
            awaitWaiting(nextThread);
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            reset.get(5, TimeUnit.SECONDS);
            assertThat(next.get(5, TimeUnit.SECONDS)).isEqualTo("响应");
            assertThat(service.getHistory("reset-race")).hasSize(2);
            assertThat(service.getHistory("reset-race").get(0)).containsEntry("content", "新消息");
        } finally {
            release.countDown();
            firstExecutor.shutdownNow();
            resetThread.join(5000);
            nextThread.join(5000);
        }
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(thread.getState()).isEqualTo(Thread.State.WAITING);
    }

    @Test
    void failedCallDoesNotLeaveHalfTurnInHistory() {
        FailOnceChatModel model = new FailOnceChatModel();
        ConversationService service = new ConversationService(ChatClient.builder(model));

        assertThatThrownBy(() -> service.chat("conv-fail", "第一次"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟供应商失败");
        assertThat(service.getHistory("conv-fail")).isEmpty();

        assertThat(service.chat("conv-fail", "第二次")).isEqualTo("恢复后的响应");
        assertThat(service.getHistory("conv-fail"))
                .extracting(entry -> entry.get("role"))
                .containsExactly("user", "assistant");
    }

    @Test
    void sameConversationIsSerializedAndHistoryContainsCompleteTurns() throws Exception {
        ConcurrentProbeChatModel model = new ConcurrentProbeChatModel();
        ConversationService service = new ConversationService(ChatClient.builder(model));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> first = executor.submit(() -> {
                start.await();
                return service.chat("conv-same", "消息一");
            });
            Future<String> second = executor.submit(() -> {
                start.await();
                return service.chat("conv-same", "消息二");
            });
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).startsWith("响应-");
            assertThat(second.get(5, TimeUnit.SECONDS)).startsWith("响应-");
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(model.maxActiveCalls).hasValue(1);
        assertThat(service.getHistory("conv-same"))
                .hasSize(4)
                .extracting(entry -> entry.get("role"))
                .containsExactly("user", "assistant", "user", "assistant");
    }

    @Test
    void historyKeepsOnlyLatestConfiguredNumberOfTurns() {
        EchoChatModel model = new EchoChatModel();
        ConversationService service = new ConversationService(ChatClient.builder(model));

        for (int index = 0; index < ConversationService.MAX_TURNS + 2; index++) {
            service.chat("conv-window", "问题-" + index);
        }

        List<java.util.Map<String, String>> history = service.getHistory("conv-window");
        assertThat(history).hasSize(ConversationService.MAX_TURNS * 2);
        assertThat(history.get(0)).containsEntry("role", "user").containsEntry("content", "问题-2");
        assertThat(history.get(history.size() - 1))
                .containsEntry("role", "assistant")
                .containsEntry("content", "离线响应");
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private static final class FailOnceChatModel implements ChatModel {

        private final AtomicBoolean first = new AtomicBoolean(true);

        @Override
        public ChatResponse call(Prompt prompt) {
            if (this.first.compareAndSet(true, false)) {
                throw new IllegalStateException("模拟供应商失败");
            }
            return response("恢复后的响应");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

    }

    private static final class ConcurrentProbeChatModel implements ChatModel {

        private final AtomicInteger activeCalls = new AtomicInteger();

        private final AtomicInteger maxActiveCalls = new AtomicInteger();

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            int current = this.activeCalls.incrementAndGet();
            this.maxActiveCalls.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(40);
                return response("响应-" + this.sequence.incrementAndGet());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("离线并发测试被中断", exception);
            }
            finally {
                this.activeCalls.decrementAndGet();
            }
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

    }

    private static final class EchoChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return response("离线响应");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

    }

}
