package com.example.springai.config;

import io.micrometer.common.KeyValues;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;

/** 保留操作指标，不把查询和过滤条件写入 trace 高基数字段。 */
public final class PrivacyVectorObservationConvention extends DefaultVectorStoreObservationConvention {
    @Override
    protected KeyValues queryContent(KeyValues values, VectorStoreObservationContext context) { return values; }
    @Override
    protected KeyValues metadataFilter(KeyValues values, VectorStoreObservationContext context) { return values; }
}
