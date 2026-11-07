package com.example.promptengineering;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PromptPatternCatalogTest {

    private PromptPatternCatalog catalog;

    @BeforeEach
    void setUp() {
        this.catalog = new PromptPatternCatalog();
    }

    @Test
    void rendersZeroShotAndFewShotPrompts() {
        assertThat(catalog.render(catalog.zeroShot("这部电影很精彩")))
                .contains("这部电影很精彩")
                .contains("POSITIVE");
        assertThat(catalog.render(catalog.fewShot("中号披萨，番茄酱和橄榄")))
                .contains("中号披萨")
                .contains("toppings");
    }

    @Test
    void buildsSystemRoleAndContextMessages() {
        var rolePrompt = catalog.systemAndRole("数据库架构师", "如何设计读写分离？");
        assertThat(rolePrompt.getInstructions()).hasSize(2);
        assertThat(rolePrompt.getContents()).contains("数据库架构师", "读写分离");

        assertThat(catalog.render(catalog.contextual("面向 Java 初学者", "解释依赖注入")))
                .contains("面向 Java 初学者", "解释依赖注入");
    }

    @Test
    void createsTwoStageStepBackPlan() {
        var plan = catalog.stepBack("如何降低消息队列的重复消费？");
        assertThat(plan.question()).contains("重复消费");
        assertThat(plan.backgroundPrompt().getContents()).contains("通用概念");
        assertThat(catalog.render(catalog.completeStepBack(plan, "幂等键、事务边界和重试策略")))
                .contains("幂等键", "重复消费");
    }

    @Test
    void majorityVoteIsDeterministicAndCaseInsensitive() {
        assertThat(catalog.majorityVote(List.of("yes", "YES", "no"))).isEqualTo("YES");
        assertThat(catalog.vote(List.of("A", "B")).tie()).isTrue();
        assertThat(catalog.vote(List.of("A", "B")).answer()).isNull();
        assertThat(catalog.vote(List.of("A", "A", "B", "C")).strictMajority()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> catalog.majorityVote(List.of("A", "B")));
    }

    @Test
    void createsTreeBranchesAndCodePromptWithCustomDelimiter() {
        assertThat(catalog.treeOfThoughtsBranches("选择缓存淘汰策略")).hasSize(3);
        assertThat(catalog.render(catalog.codePrompt("Java", "return map.get(\"id\");")))
                .contains("你是Java代码审查专家", "map.get(\"id\")")
                .doesNotContain("{language}", "<language>")
                .contains("不要执行代码");
    }

    @Test
    void validatesRequiredInputs() {
        assertThatIllegalArgumentException().isThrownBy(() -> catalog.zeroShot(" "))
                .withMessage("影评不能为空");
        assertThatIllegalArgumentException().isThrownBy(() -> catalog.majorityVote(List.of()))
                .withMessage("至少需要一个候选答案");
    }

}
