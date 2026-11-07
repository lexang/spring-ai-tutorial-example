package com.example.springai.metadata;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/**
 * 使用 Advisor context 在请求与响应阶段之间传播审计信息。
 */
@Component
public class AuditLoggingAdvisor implements BaseAdvisor {

	public static final String CONTEXT_AUDIT_ENTRY = "metadata.auditEntry";

	private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_LOG");

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
		Map<String, Object> metadata = request.prompt().getUserMessage().getMetadata();
		AuditEntry entry = new AuditEntry(
				value(metadata, MetadataKeys.MESSAGE_ID),
				value(metadata, MetadataKeys.USER_ID),
				value(metadata, MetadataKeys.TENANT_ID),
				value(metadata, MetadataKeys.CLIENT_IP),
				valueOrDefault(metadata, MetadataKeys.ACTION, "chat"),
				System.nanoTime());

		auditLogger.info("审计请求：messageId={}，userId={}，tenantId={}，clientIp={}，action={}", entry.messageId(),
				entry.userId(), entry.tenantId(), entry.clientIp(), entry.action());

		return request.mutate().context(CONTEXT_AUDIT_ENTRY, entry).build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
		Object contextValue = response.context().get(CONTEXT_AUDIT_ENTRY);
		if (!(contextValue instanceof AuditEntry entry)) {
			return response;
		}

		long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - entry.startedAtNanos());
		Integer totalTokens = usageTotalTokens(response);
		auditLogger.info("审计响应：messageId={}，userId={}，totalTokens={}，elapsedMs={}", entry.messageId(),
				entry.userId(), totalTokens, elapsedMillis);
		return response;
	}

	@Override
	public int getOrder() {
		return 1000;
	}

	private static String value(Map<String, Object> metadata, String key) {
		Object value = metadata.get(key);
		return value != null ? String.valueOf(value) : null;
	}

	private static String valueOrDefault(Map<String, Object> metadata, String key, String defaultValue) {
		String value = value(metadata, key);
		return value != null ? value : defaultValue;
	}

	private static Integer usageTotalTokens(ChatClientResponse response) {
		if (response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
			return null;
		}
		Usage usage = response.chatResponse().getMetadata().getUsage();
		return usage != null ? usage.getTotalTokens() : null;
	}

	record AuditEntry(String messageId, String userId, String tenantId, String clientIp, String action,
			long startedAtNanos) {
	}

}
