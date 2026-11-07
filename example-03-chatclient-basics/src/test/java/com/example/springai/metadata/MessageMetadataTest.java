package com.example.springai.metadata;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.example.springai.EmptyChatResponseException;
import com.example.springai.InvalidDemoRequestException;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageMetadataTest {

    @Test
    void mapOverloadKeepsEmptyKeysInThePinnedVersion() {
        RecordingChatModel model = new RecordingChatModel();
        ChatClient.create(model).prompt().user(user -> user.text("测试").metadata(Map.of("", "value")))
                .call().content();
        assertThat(model.lastPrompt.getUserMessage().getMetadata()).containsEntry("", "value");
    }

    @Test
    void emptyMetadataChatResponseFailsClearly() {
        ChatModel model = prompt -> new ChatResponse(List.of());
        MetadataController controller = new MetadataController(ChatClient.builder(model),
                new MetadataAdvisorChain(new TenantAwareAdvisor(), new PriorityAdvisor(), new AuditLoggingAdvisor()));
        assertThatThrownBy(() -> controller.basicMetadata("测试"))
                .isInstanceOf(EmptyChatResponseException.class);
    }

    @Test
    void invalidPriorityIsAnInputError() {
        MetadataController controller = new MetadataController(ChatClient.builder(new RecordingChatModel()),
                new MetadataAdvisorChain(new TenantAwareAdvisor(), new PriorityAdvisor(), new AuditLoggingAdvisor()));
        assertThatThrownBy(() -> controller.priorityChat("测试", "invalid", "demo"))
                .isInstanceOf(InvalidDemoRequestException.class);
    }

	@Test
	void shouldAttachAndMergeUserAndSystemMetadata() {
		RecordingChatModel chatModel = new RecordingChatModel();
		ChatClient chatClient = ChatClient.builder(chatModel)
			.defaultSystem(system -> system.text("默认系统消息")
				.metadata("version", "2.0")
				.metadata("scope", "default"))
			.defaultUser(user -> user.text("默认用户消息")
				.metadata(MetadataKeys.SOURCE, "default")
				.metadata("channel", "tutorial"))
			.build();

		chatClient.prompt()
			.system(system -> system.metadata("scope", "request"))
			.user(user -> user.text("实际问题")
				.metadata(MetadataKeys.MESSAGE_ID, "msg-001")
				.metadata(MetadataKeys.SOURCE, "web-api"))
			.call()
			.content();

		Prompt prompt = chatModel.lastPrompt;
		assertThat(prompt).isNotNull();
		assertThat(prompt.getSystemMessage().getMetadata())
			.containsEntry("version", "2.0")
			.containsEntry("scope", "request")
			.containsEntry("messageType", MessageType.SYSTEM);
		assertThat(prompt.getUserMessage().getMetadata())
			.containsEntry(MetadataKeys.MESSAGE_ID, "msg-001")
			.containsEntry(MetadataKeys.SOURCE, "web-api")
			.containsEntry("channel", "tutorial")
			.containsEntry("messageType", MessageType.USER);
	}

	@Test
	void shouldRejectEmptyMetadataKeyBeforeCallingModel() {
		RecordingChatModel chatModel = new RecordingChatModel();
		ChatClient chatClient = ChatClient.create(chatModel);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> chatClient.prompt()
				.user(user -> user.text("不会调用模型").metadata("", "value")))
			.withMessage("metadata key cannot be null or empty");
		assertThat(chatModel.lastPrompt).isNull();
	}

	@Test
	void shouldPropagateParsedMetadataThroughAdvisorContext() {
		RecordingChatModel chatModel = new RecordingChatModel();
		ChatClient chatClient = ChatClient.builder(chatModel)
			.defaultAdvisors(new TenantAwareAdvisor(), new PriorityAdvisor(), new AuditLoggingAdvisor())
			.build();

		ChatClientResponse response = chatClient.prompt()
			.user(user -> user.text("请总结本次调用")
				.metadata(MetadataKeys.MESSAGE_ID, "msg-002")
				.metadata(MetadataKeys.USER_ID, "user-001")
				.metadata(MetadataKeys.TENANT_ID, "tenant-premium")
				.metadata(MetadataKeys.PRIORITY, "high"))
			.call()
			.chatClientResponse();

		assertThat(response.context())
			.containsEntry(TenantAwareAdvisor.CONTEXT_TENANT_ID, "tenant-premium")
			.containsEntry(TenantAwareAdvisor.CONTEXT_TENANT_TIER, "premium")
			.containsEntry(TenantAwareAdvisor.CONTEXT_TOKEN_LIMIT, 2000)
			.containsEntry(PriorityAdvisor.CONTEXT_PRIORITY, "high")
			.containsKey(AuditLoggingAdvisor.CONTEXT_AUDIT_ENTRY);
	}

	@Test
	void validationEndpointShouldNotCallModel() {
		RecordingChatModel chatModel = new RecordingChatModel();
		MetadataAdvisorChain advisorChain = new MetadataAdvisorChain(
				new TenantAwareAdvisor(), new PriorityAdvisor(), new AuditLoggingAdvisor());
		MetadataController controller = new MetadataController(ChatClient.builder(chatModel), advisorChain);

		Map<String, Object> result = controller.metadataValidation();

		assertThat(result)
			.containsEntry("modelCalled", false)
			.containsEntry("observedError", "metadata key cannot be null or empty");
		assertThat(chatModel.lastPrompt).isNull();
	}

	@Test
	void shouldPropagateParsedMetadataThroughStreamingAdvisorContext() {
		RecordingChatModel chatModel = new RecordingChatModel();
		ChatClient chatClient = ChatClient.builder(chatModel)
			.defaultAdvisors(new TenantAwareAdvisor(), new PriorityAdvisor(), new AuditLoggingAdvisor())
			.build();

		ChatClientResponse response = chatClient.prompt()
			.user(user -> user.text("流式请求")
				.metadata(MetadataKeys.MESSAGE_ID, "msg-stream")
				.metadata(MetadataKeys.TENANT_ID, "tenant-basic")
				.metadata(MetadataKeys.PRIORITY, "low"))
			.stream()
			.chatClientResponse()
			.blockLast();

		assertThat(response).isNotNull();
		assertThat(response.context())
			.containsEntry(TenantAwareAdvisor.CONTEXT_TENANT_ID, "tenant-basic")
			.containsEntry(TenantAwareAdvisor.CONTEXT_TENANT_TIER, "basic")
			.containsEntry(PriorityAdvisor.CONTEXT_PRIORITY, "low");
	}

	private static final class RecordingChatModel implements ChatModel {

		private Prompt lastPrompt;

		@Override
		public ChatResponse call(Prompt prompt) {
			this.lastPrompt = prompt;
			return new ChatResponse(List.of(new Generation(new AssistantMessage("离线响应"))));
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			this.lastPrompt = prompt;
			ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason("STOP").build();
			return Flux.just(new ChatResponse(
					List.of(new Generation(new AssistantMessage("离线流式响应"), metadata))));
		}

	}

}
