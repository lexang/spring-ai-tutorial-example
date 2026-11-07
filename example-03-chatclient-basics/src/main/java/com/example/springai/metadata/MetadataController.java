package com.example.springai.metadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import com.example.springai.ClientLoggers;
import com.example.springai.EmptyChatResponseException;
import com.example.springai.InvalidDemoRequestException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示如何为消息添加元数据，以及如何在 Advisor 链中使用这些数据。
 */
@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

	private static final Logger logger = LoggerFactory.getLogger(MetadataController.class);

	private final ChatClient chatClient;

	private final MetadataAdvisorChain advisorChain;

	public MetadataController(ChatClient.Builder chatClientBuilder, MetadataAdvisorChain advisorChain) {
		this.chatClient = chatClientBuilder.build();
		this.advisorChain = advisorChain;
	}

	/**
	 * 场景 1：逐项添加消息元数据。
	 */
	@GetMapping("/basic")
	public Map<String, Object> basicMetadata(@RequestParam String message) {
		logger.info("场景 1：基础 Message Metadata");

		String response = requireContent(this.chatClient.prompt()
			.user(user -> user.text(requireMessage(message))
				.metadata(MetadataKeys.MESSAGE_ID, UUID.randomUUID().toString())
				.metadata(MetadataKeys.TIMESTAMP, System.currentTimeMillis())
				.metadata(MetadataKeys.SOURCE, "web"))
			.call()
			.content());

		return Map.of(
				"scenario", "基础 Metadata",
				"response", response,
				"description", "为消息添加了 messageId、timestamp 和 source 元数据");
	}

	/**
	 * 场景 2：使用 Map 批量添加消息元数据。
	 */
	@GetMapping("/batch")
	public Map<String, Object> batchMetadata(@RequestParam String message,
			@RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
		logger.info("场景 2：批量 Message Metadata，userId={}", userId);

		Map<String, Object> metadata = Map.of(
				MetadataKeys.MESSAGE_ID, UUID.randomUUID().toString(),
				MetadataKeys.USER_ID, userId,
				MetadataKeys.TIMESTAMP, System.currentTimeMillis(),
				MetadataKeys.SOURCE, "web-api",
                "version", "2.0");

		String response = requireContent(this.chatClient.prompt()
			.user(user -> user.text(requireMessage(message)).metadata(metadata))
			.call()
			.content());

		return Map.of(
				"scenario", "批量 Metadata",
				"response", response,
				"metadata", metadata,
				"description", "使用 Map 批量添加多个元数据");
	}

	/**
	 * 场景 3：读取租户元数据并写入 Advisor context。
	 */
	@PostMapping("/multi-tenant")
	public Map<String, Object> multiTenantChat(@RequestParam String message,
			@RequestHeader("X-User-Id") String userId,
			@RequestHeader("X-Tenant-Id") String tenantId) {
		logger.info("场景 3：多租户请求，tenantId={}，userId={}", tenantId, userId);

		String response = requireContent(this.chatClient.prompt()
			.user(user -> user.text(requireMessage(message))
				.metadata(MetadataKeys.USER_ID, userId)
				.metadata(MetadataKeys.TENANT_ID, tenantId)
				.metadata(MetadataKeys.MESSAGE_ID, UUID.randomUUID().toString())
				.metadata(MetadataKeys.TIMESTAMP, System.currentTimeMillis()))
			.advisors(this.advisorChain.getTenantAwareAdvisor())
			.call()
			.content());

		return Map.of(
				"scenario", "多租户追踪",
				"tenantId", tenantId,
				"userId", userId,
				"response", response,
				"description", "Advisor 读取租户元数据，并通过 context 把租户策略传播到响应阶段");
	}

	/**
	 * 场景 4：校验优先级并传播解析结果。
	 */
	@PostMapping("/priority")
	public Map<String, Object> priorityChat(@RequestParam String message,
			@RequestParam(defaultValue = "normal") String priority,
			@RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
		logger.info("场景 4：优先级消息，priority={}，userId={}", priority, userId);

		String response = requireContent(this.chatClient.prompt()
			.user(user -> user.text(requireMessage(message))
				.metadata(MetadataKeys.USER_ID, userId)
				.metadata(MetadataKeys.PRIORITY, priority)
				.metadata(MetadataKeys.MESSAGE_ID, UUID.randomUUID().toString())
				.metadata(MetadataKeys.TIMESTAMP, System.currentTimeMillis()))
			.advisors(this.advisorChain.getPriorityAdvisor())
			.call()
			.content());

		return Map.of(
				"scenario", "优先级处理",
				"priority", priority,
				"response", response,
				"description", "校验优先级并通过 Advisor context 传播");
	}

	/**
	 * 场景 5：携带会话标识，但不把它误写成 Chat Memory。
	 */
	@PostMapping("/session")
	public Map<String, Object> sessionChat(@RequestParam String message,
			@RequestParam(required = false) String sessionId,
			@RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
		String actualSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
		logger.info("场景 5：会话标识，sessionId={}，userId={}", actualSessionId, userId);

		String response = requireContent(this.chatClient.prompt()
			.user(user -> user.text(requireMessage(message))
				.metadata(MetadataKeys.SESSION_ID, actualSessionId)
				.metadata(MetadataKeys.USER_ID, userId)
				.metadata(MetadataKeys.MESSAGE_ID, UUID.randomUUID().toString())
				.metadata(MetadataKeys.TIMESTAMP, System.currentTimeMillis()))
			.call()
			.content());

		return Map.of(
				"scenario", "会话标识",
				"sessionId", actualSessionId,
				"userId", userId,
				"response", response,
				"description", "metadata 只携带会话标识；恢复历史消息仍需 ChatMemory");
	}

	/**
	 * 场景 6：通过 Advisor context 关联请求和响应审计信息。
	 */
	@PostMapping("/audit")
	public Map<String, Object> auditChat(@RequestParam String message,
			@RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
			@RequestHeader(value = "X-Client-IP", defaultValue = "unknown") String clientIp) {
		logger.info("场景 6：审计日志，userId={}，clientIp={}", userId, clientIp);

		String messageId = UUID.randomUUID().toString();
		String response = requireContent(this.chatClient.prompt()
			.user(user -> user.text(requireMessage(message))
				.metadata(MetadataKeys.MESSAGE_ID, messageId)
				.metadata(MetadataKeys.USER_ID, userId)
				.metadata(MetadataKeys.CLIENT_IP, clientIp)
				.metadata(MetadataKeys.TIMESTAMP, System.currentTimeMillis())
				.metadata(MetadataKeys.ACTION, "chat"))
			.advisors(this.advisorChain.getAuditLoggingAdvisor())
			.call()
			.content());

		return Map.of(
				"scenario", "审计日志",
				"messageId", messageId,
				"userId", userId,
				"response", response,
				"description", "记录必要审计字段、响应用量和耗时");
	}

	/**
	 * 场景 7：为 System Message 添加元数据。
	 */
	@GetMapping("/system-metadata")
	public Map<String, Object> systemMetadata(@RequestParam String message) {
		logger.info("场景 7：System Message Metadata");

		String response = requireContent(this.chatClient.prompt()
			.system(system -> system.text("你是一个专业的助手，请用简洁的方式回答问题。")
                .metadata("version", "2.0")
				.metadata("assistantType", "professional")
				.metadata("mode", "concise"))
			.user(user -> user.text(requireMessage(message))
				.metadata(MetadataKeys.MESSAGE_ID, UUID.randomUUID().toString())
				.metadata(MetadataKeys.TIMESTAMP, System.currentTimeMillis()))
			.call()
			.content());

		return Map.of(
				"scenario", "System Message Metadata",
				"response", response,
				"description", "为 System Message 添加版本、类型和模式元数据");
	}

	/**
	 * 场景 8：演示多个 Advisor 通过 context 协同处理。
	 */
	@PostMapping("/complete")
	public Map<String, Object> completeChain(@RequestParam String message,
			@RequestParam(defaultValue = "normal") String priority,
			@RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
			@RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
		logger.info("场景 8：完整处理链，tenantId={}，userId={}，priority={}", tenantId, userId, priority);

		String messageId = UUID.randomUUID().toString();
		ChatClientResponse clientResponse = this.chatClient.prompt()
			.user(user -> user.text(requireMessage(message))
				.metadata(MetadataKeys.MESSAGE_ID, messageId)
				.metadata(MetadataKeys.USER_ID, userId)
				.metadata(MetadataKeys.TENANT_ID, tenantId)
				.metadata(MetadataKeys.PRIORITY, priority)
				.metadata(MetadataKeys.TIMESTAMP, System.currentTimeMillis())
				.metadata(MetadataKeys.SOURCE, "complete-api"))
			.advisors(
					this.advisorChain.getTenantAwareAdvisor(),
					this.advisorChain.getPriorityAdvisor(),
					this.advisorChain.getAuditLoggingAdvisor(),
					ClientLoggers.summaries())
			.call()
			.chatClientResponse();

		String response = "";
		if (clientResponse == null || clientResponse.chatResponse() == null || clientResponse.chatResponse().getResult() == null) {
			throw new EmptyChatResponseException("模型未返回完整响应");
		}
		if (clientResponse.chatResponse() != null && clientResponse.chatResponse().getResult() != null) {
			response = requireContent(clientResponse.chatResponse().getResult().getOutput().getText());
		}

		Map<String, Object> advisorContext = new LinkedHashMap<>();
		advisorContext.put("tenantId", clientResponse.context().get(TenantAwareAdvisor.CONTEXT_TENANT_ID));
		advisorContext.put("tenantTier", clientResponse.context().get(TenantAwareAdvisor.CONTEXT_TENANT_TIER));
		advisorContext.put("tokenLimit", clientResponse.context().get(TenantAwareAdvisor.CONTEXT_TOKEN_LIMIT));
		advisorContext.put("priority", clientResponse.context().get(PriorityAdvisor.CONTEXT_PRIORITY));

		return Map.of(
				"scenario", "完整处理链",
				"messageId", messageId,
				"tenantId", tenantId,
				"userId", userId,
				"priority", priority,
				"response", response,
				"advisorContext", advisorContext,
				"description", "多个 Advisor 通过 context 共享租户、优先级和审计状态");
	}

	/**
	 * 场景 9：演示 ChatClient 本地参数校验，不调用模型。
	 */
	@GetMapping("/validation")
	public Map<String, Object> metadataValidation() {
		logger.info("场景 9：Message Metadata 本地校验");

		String observedError;
		try {
			this.chatClient.prompt().user(user -> user.text("不会调用模型").metadata("", "value"));
			observedError = "未触发预期校验";
		}
		catch (IllegalArgumentException exception) {
			observedError = exception.getMessage();
		}

		return Map.of(
				"scenario", "Metadata 验证",
				"observedError", observedError,
				"modelCalled", false,
				"validationRules", Map.of(
						"rule1", "单键重载的 Key 不能为 null 或空字符串",
						"rule2", "Value 不能为 null",
						"rule3", "Map 不能包含 null key 或 null value"));
	}

	private static String requireContent(String content) {
		if (content == null || content.isBlank()) {
			throw new EmptyChatResponseException("模型未返回文本内容");
		}
		return content;
	}

	private static String requireMessage(String message) {
		if (message == null || message.isBlank()) throw new InvalidDemoRequestException("message 不能为空");
		return message;
	}

}
