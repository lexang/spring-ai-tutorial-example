package com.example.springai.metadata;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/**
 * 读取租户消息元数据，并把经过解析的策略信息放入 Advisor context。
 */
@Component
public class TenantAwareAdvisor implements BaseAdvisor {

	public static final String CONTEXT_TENANT_ID = "metadata.tenantId";

	public static final String CONTEXT_TENANT_TIER = "metadata.tenantTier";

	public static final String CONTEXT_TOKEN_LIMIT = "metadata.tokenLimit";

	private static final Logger logger = LoggerFactory.getLogger(TenantAwareAdvisor.class);

	private final Map<String, TenantPolicy> policies = Map.of(
			"tenant-premium", new TenantPolicy("premium", 2000),
			"tenant-standard", new TenantPolicy("standard", 1000),
			"tenant-basic", new TenantPolicy("basic", 500),
			"default", new TenantPolicy("default", 1000));

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
		Map<String, Object> metadata = request.prompt().getUserMessage().getMetadata();
		String tenantId = valueOrDefault(metadata.get(MetadataKeys.TENANT_ID), "default");
		TenantPolicy policy = this.policies.getOrDefault(tenantId, this.policies.get("default"));

		logger.info("租户 Advisor：tenantId={}，tier={}，建议 token 配额={}", tenantId, policy.tier(),
				policy.tokenLimit());

		return request.mutate()
			.context(CONTEXT_TENANT_ID, tenantId)
			.context(CONTEXT_TENANT_TIER, policy.tier())
			.context(CONTEXT_TOKEN_LIMIT, policy.tokenLimit())
			.build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
		String tenantId = valueOrDefault(response.context().get(CONTEXT_TENANT_ID), "default");
		String tier = valueOrDefault(response.context().get(CONTEXT_TENANT_TIER), "default");
		Integer tokenLimit = numberOrDefault(response.context().get(CONTEXT_TOKEN_LIMIT), 1000);
		Integer totalTokens = usageTotalTokens(response);

		logger.info("租户 Advisor：tenantId={}，tier={}，totalTokens={}，建议配额={}", tenantId, tier, totalTokens,
				tokenLimit);
		if (totalTokens != null && totalTokens > tokenLimit) {
			logger.warn("租户 {} 超过示例配额：{} > {}", tenantId, totalTokens, tokenLimit);
		}
		return response;
	}

	@Override
	public int getOrder() {
		return 100;
	}

	private static Integer usageTotalTokens(ChatClientResponse response) {
		if (response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
			return null;
		}
		Usage usage = response.chatResponse().getMetadata().getUsage();
		return usage != null ? usage.getTotalTokens() : null;
	}

	private static String valueOrDefault(Object value, String defaultValue) {
		return value != null ? String.valueOf(value) : defaultValue;
	}

	private static Integer numberOrDefault(Object value, int defaultValue) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		return defaultValue;
	}

	private record TenantPolicy(String tier, int tokenLimit) {
	}

}
