package com.example.springai.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用层成本策略。
 *
 * <p>Spring AI 只提供模型使用量，不提供跨供应商的价格表；这里的单价必须由
 * 应用根据供应商账单自行配置，单位是每一百万 token 的货币金额。</p>
 */
@ConfigurationProperties(prefix = "demo.cost")
public class CostPolicyProperties {

    private BigDecimal inputPerMillion = BigDecimal.ONE;

    private BigDecimal outputPerMillion = BigDecimal.valueOf(2);

    private BigDecimal cacheReadPerMillion = BigDecimal.ZERO;

    private BigDecimal cacheWritePerMillion = BigDecimal.ZERO;

    // OpenAI 型输入统计包含缓存；2.0.2 Anthropic/Bedrock 接入应显式设为 false。
    private boolean promptTokensIncludeCache = true;

    public boolean isPromptTokensIncludeCache() {
        return this.promptTokensIncludeCache;
    }

    public void setPromptTokensIncludeCache(boolean promptTokensIncludeCache) {
        this.promptTokensIncludeCache = promptTokensIncludeCache;
    }

    public BigDecimal getInputPerMillion() {
        return inputPerMillion;
    }

    public void setInputPerMillion(BigDecimal inputPerMillion) {
        this.inputPerMillion = inputPerMillion;
    }

    public BigDecimal getOutputPerMillion() {
        return outputPerMillion;
    }

    public void setOutputPerMillion(BigDecimal outputPerMillion) {
        this.outputPerMillion = outputPerMillion;
    }

    public BigDecimal getCacheReadPerMillion() {
        return cacheReadPerMillion;
    }

    public void setCacheReadPerMillion(BigDecimal cacheReadPerMillion) {
        this.cacheReadPerMillion = cacheReadPerMillion;
    }

    public BigDecimal getCacheWritePerMillion() {
        return cacheWritePerMillion;
    }

    public void setCacheWritePerMillion(BigDecimal cacheWritePerMillion) {
        this.cacheWritePerMillion = cacheWritePerMillion;
    }

}
