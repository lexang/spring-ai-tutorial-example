package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.springai.service.AgentWorkflowService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentWorkflowContractTest {

    @Autowired
    private AgentWorkflowService workflowService;

    @Test
    void chainPassesEachResultToTheNextStep() {
        AgentWorkflowService.ChainResult result = this.workflowService.chain(
            "用户需求", java.util.List.of("提取约束", "生成摘要"));

        assertThat(result.trace()).hasSize(2);
        assertThat(result.output()).contains("链式步骤结果");
    }

    @Test
    void parallelRunsWorkersAndAggregatesResults() {
        AgentWorkflowService.ParallelResult result = this.workflowService.parallel(
            "市场变化", java.util.List.of("客户视角", "员工视角", "投资者视角"));

        assertThat(result.workers()).hasSize(3);
        assertThat(result.summary()).contains("并行汇总");
    }

    @Test
    void routingUsesAConstrainedRouteSet() {
        AgentWorkflowService.RouteResult result = this.workflowService.route("上周账户被重复扣费");

        assertThat(result.route()).isEqualTo("billing");
        assertThat(result.response()).isNotBlank();
        assertThat(this.workflowService.route("接口发生故障").route()).isEqualTo("technical");
        assertThat(this.workflowService.route("请介绍产品").route()).isEqualTo("general");
    }

    @Test
    void orchestratorProducesTypedPlanAndWorkerResults() {
        AgentWorkflowService.OrchestrationResult result = this.workflowService.orchestrate(
            "为 REST API 编写技术和用户说明");

        assertThat(result.plan().subtasks()).hasSize(2);
        assertThat(result.workers()).hasSize(2);
        assertThat(result.finalAnswer()).contains("并行汇总");
    }

    @Test
    void evaluatorOptimizerStopsAfterApassingIteration() {
        AgentWorkflowService.OptimizationResult result =
            this.workflowService.evaluateAndOptimize("实现线程安全计数器", 3);

        assertThat(result.evaluations()).hasSize(2);
        assertThat(result.evaluations().get(0).evaluation().pass()).isFalse();
        assertThat(result.evaluations().get(1).evaluation().pass()).isTrue();
        assertThat(result.finalSolution()).contains("改进稿");
        assertThat(result.draft()).contains("初稿");
        assertThat(result.accepted()).isTrue();
        assertThat(result.stopReason()).isEqualTo("PASSED");
    }

}
