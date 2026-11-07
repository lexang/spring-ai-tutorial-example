package com.example.springai.metadata;

import org.springframework.stereotype.Component;

/**
 * Metadata Advisor 链管理器
 * 统一管理各种基于 Metadata 的 Advisor
 */
@Component
public class MetadataAdvisorChain {

    private final TenantAwareAdvisor tenantAwareAdvisor;
    private final PriorityAdvisor priorityAdvisor;
    private final AuditLoggingAdvisor auditLoggingAdvisor;

    public MetadataAdvisorChain(TenantAwareAdvisor tenantAwareAdvisor,
                                PriorityAdvisor priorityAdvisor,
                                AuditLoggingAdvisor auditLoggingAdvisor) {
        this.tenantAwareAdvisor = tenantAwareAdvisor;
        this.priorityAdvisor = priorityAdvisor;
        this.auditLoggingAdvisor = auditLoggingAdvisor;
    }

    public TenantAwareAdvisor getTenantAwareAdvisor() {
        return tenantAwareAdvisor;
    }

    public PriorityAdvisor getPriorityAdvisor() {
        return priorityAdvisor;
    }

    public AuditLoggingAdvisor getAuditLoggingAdvisor() {
        return auditLoggingAdvisor;
    }
}
