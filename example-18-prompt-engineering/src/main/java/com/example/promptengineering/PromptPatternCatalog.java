package com.example.promptengineering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Service;

/**
 * 只负责构造和评估提示词的离线服务。
 *
 * <p>这里不调用模型，因此可以在没有 API Key 的情况下测试提示词结构；
 * 需要真实模型时由 {@link LivePromptController} 负责执行。</p>
 */
@Service
public class PromptPatternCatalog {

    public Prompt zeroShot(String review) {
        return template("""
                将下面的影评分类为 POSITIVE、NEUTRAL 或 NEGATIVE，只返回一个标签。

                影评：{review}
                """).create(Map.of("review", required(review, "影评")));
    }

    public Prompt fewShot(String order) {
        PromptTemplate template = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template("""
                将披萨订单转换为 JSON，字段必须是 size、sauce 和 toppings。

                示例：小号披萨，番茄酱和芝士
                {"size":"small","sauce":"tomato","toppings":["cheese"]}

                示例：大号披萨，白酱和蘑菇
                {"size":"large","sauce":"white","toppings":["mushroom"]}

                待处理订单：<order>
                """)
                .build();
        return template.create(Map.of("order", required(order, "订单")));
    }

    public Prompt systemAndRole(String role, String question) {
        Message system = new SystemPromptTemplate("你是{role}。回答要准确、简洁，并说明不确定的地方。")
                .createMessage(Map.of("role", required(role, "角色")));
        return new Prompt(List.of(system, new UserMessage(required(question, "问题"))));
    }

    public Prompt contextual(String context, String question) {
        return template("""
                请根据给定背景回答问题。只使用背景中可验证的信息。

                背景：{context}
                问题：{question}
                """).create(Map.of(
                "context", required(context, "上下文"),
                "question", required(question, "问题")));
    }

    public StepBackPlan stepBack(String question) {
        String input = required(question, "问题");
        Prompt background = template("""
                先总结解决下面问题所需的通用概念、约束和判断标准，不要直接给出最终答案：
                {question}
                """).create(Map.of("question", input));
        return new StepBackPlan(input, background, """
                请使用下面的通用背景解决原问题，并给出可以核验的结论。

                通用背景：{background}
                原问题：{question}
                """);
    }

    public Prompt completeStepBack(StepBackPlan plan, String background) {
        if (plan == null) {
            throw new IllegalArgumentException("Step-back 计划不能为空");
        }
        return template(plan.finalPromptTemplate()).create(Map.of(
                "background", required(background, "通用背景"),
                "question", plan.question()));
    }

    public Prompt conciseReasoning(String problem) {
        return template("""
                解决下面的问题。请只给出必要的、可核验的简短步骤，最后单独给出答案；
                不要输出隐藏的内部思维过程。

                问题：{problem}
                """).create(Map.of("problem", required(problem, "问题")));
    }

    public Prompt selfConsistencySample(String problem) {
        return template("""
                独立解决下面的问题，并在最后一行输出 FINAL= 后面的标准化答案。
                每次调用都应独立判断，不要参考其他样本。

                问题：{problem}
                """).create(Map.of("problem", required(problem, "问题")));
    }

    public String majorityVote(List<String> answers) {
        VoteResult result = vote(answers);
        if (!result.strictMajority()) {
            throw new IllegalArgumentException("候选答案没有形成严格多数");
        }
        return result.answer();
    }

    public VoteResult vote(List<String> answers) {
        if (answers == null || answers.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个候选答案");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String answer : answers) {
            String normalized = required(answer, "候选答案").trim().toUpperCase(Locale.ROOT);
            counts.merge(normalized, 1, Integer::sum);
        }
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        List<String> leaders = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey).toList();
        boolean majority = max > answers.size() / 2;
        return new VoteResult(majority ? leaders.get(0) : null, max, answers.size(),
                majority, leaders.size() > 1, Map.copyOf(counts));
    }

    public List<Prompt> treeOfThoughtsBranches(String problem) {
        String input = required(problem, "问题");
        List<Prompt> branches = new ArrayList<>();
        for (String strategy : List.of("从约束出发", "从反例出发", "从最小可行方案出发")) {
            branches.add(template("""
                    使用“{strategy}”策略分析问题，给出候选方案、依据和风险。
                    问题：{problem}
                    """).create(Map.of("strategy", strategy, "problem", input)));
        }
        return List.copyOf(branches);
    }

    public Prompt automaticPromptCandidates(String task) {
        return template("""
                为下面的任务生成 3 个语义等价但表达不同的候选提示词。
                每个候选必须明确输入、输出和验收标准，不要执行任务本身。

                任务：{task}
                """).create(Map.of("task", required(task, "任务")));
    }

    public Prompt codePrompt(String language, String code) {
        PromptTemplate template = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template("""
                        你是<language>代码审查专家。请指出安全、正确性和性能问题，
                        并给出最小修改建议。不要执行代码。

                        代码：
                        ~~~<language>
                        <code>
                        ~~~
                        """)
                .build();
        return template.create(Map.of(
                "language", required(language, "语言"),
                "code", required(code, "代码")));
    }

    public String render(Prompt prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt 不能为空");
        }
        return prompt.getContents();
    }

    private PromptTemplate template(String text) {
        return PromptTemplate.builder().template(text).build();
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        if (value.length() > 12000) {
            throw new IllegalArgumentException(name + "最多允许 12000 个字符");
        }
        return value;
    }

    public record StepBackPlan(String question, Prompt backgroundPrompt, String finalPromptTemplate) {
    }

    public record VoteResult(String answer, int leadingVotes, int validSamples,
            boolean strictMajority, boolean tie, Map<String, Integer> counts) {
    }

}
