package com.example.springai.metadata;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.example.springai.InvalidDemoRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/**
 * 校验消息优先级，并通过 Advisor context 把结果传播到响应阶段。
 */
@Component
public class PriorityAdvisor implements BaseAdvisor {

	public static final String CONTEXT_PRIORITY = "metadata.priority";

	private static final Set<String> SUPPORTED_PRIORITIES = Set.of("high", "normal", "low");

	private static final Logger logger = LoggerFactory.getLogger(PriorityAdvisor.class);

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
		Map<String, Object> metadata = request.prompt().getUserMessage().getMetadata();
		String priority = String.valueOf(metadata.getOrDefault(MetadataKeys.PRIORITY, "normal"))
			.toLowerCase(Locale.ROOT);
		if (!SUPPORTED_PRIORITIES.contains(priority)) {
            throw new InvalidDemoRequestException("priority 仅支持 high、normal 或 low");
		}

		logger.info("优先级 Advisor：priority={}", priority);
		return request.mutate().context(CONTEXT_PRIORITY, priority).build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
		String priority = String.valueOf(response.context().getOrDefault(CONTEXT_PRIORITY, "normal"));
		Integer totalTokens = usageTotalTokens(response);
		logger.info("优先级 Advisor：priority={}，totalTokens={}", priority, totalTokens);
		return response;
	}

	@Override
	public int getOrder() {
		return 200;
	}

	private static Integer usageTotalTokens(ChatClientResponse response) {
		if (response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
			return null;
		}
		Usage usage = response.chatResponse().getMetadata().getUsage();
		return usage != null ? usage.getTotalTokens() : null;
	}

}
