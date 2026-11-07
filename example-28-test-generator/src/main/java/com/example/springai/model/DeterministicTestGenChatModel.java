package com.example.springai.model;

import java.util.ArrayList;
import java.util.List;

import com.example.springai.model.TestGenModels.GeneratedTestCase;
import com.example.springai.model.TestGenModels.MockDataEntry;
import com.example.springai.model.TestGenModels.TestPlan;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 离线确定性测试生成模型。
 *
 * <p>仅用于无外部依赖的教学与测试。它从提示词中的结构化分隔标记提取目标类名、
 * 类签名和指令，再按确定性规则生成 JSON 测试计划，使结果可重复验证。
 * 真正的测试用例质量由 live profile 下的 OpenAI 模型提供。</p>
 */
public final class DeterministicTestGenChatModel implements ChatModel {

    private static final String TARGET_TOKEN = "目标类开始";

    private static final String TARGET_END_TOKEN = "目标类结束";

    private static final String SIG_TOKEN = "类签名开始";

    private static final String SIG_END_TOKEN = "类签名结束";

    private static final String INSTR_TOKEN = "指令开始";

    private static final String INSTR_END_TOKEN = "指令结束";

    private static final String MAX_TOKEN = "用例上限开始";

    private static final String MAX_END_TOKEN = "用例上限结束";

    @Override
    public ChatResponse call(Prompt prompt) {
        String promptText = prompt.getUserMessage().getText();
        String targetClass = extractBetween(promptText, TARGET_TOKEN, TARGET_END_TOKEN);
        String signature = extractBetween(promptText, SIG_TOKEN, SIG_END_TOKEN);
        String instruction = extractBetween(promptText, INSTR_TOKEN, INSTR_END_TOKEN);
        int maxCases = extractInt(promptText, MAX_TOKEN, MAX_END_TOKEN, 3);

        List<GeneratedTestCase> cases = generateCases(targetClass, signature, instruction, maxCases);
        String json = toJson(targetClass, cases);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private List<GeneratedTestCase> generateCases(String targetClass, String signature,
            String instruction, int maxCases) {
        List<GeneratedTestCase> cases = new ArrayList<>();
        int count = Math.min(maxCases, 3);
        for (int i = 1; i <= count; i++) {
            List<MockDataEntry> mockData = List.of(
                new MockDataEntry("id", String.valueOf(i)),
                new MockDataEntry("name", "sample" + i));
            String input = "input" + i;
            String expected = "expected" + i;
            String desc = "测试用例 " + i + "：验证 " + targetClass + " 的基本路径";
            cases.add(new GeneratedTestCase(targetClass.toLowerCase() + "_test_" + i, desc,
                    input, expected, mockData));
        }
        return cases;
    }

    private String extractBetween(String text, String startToken, String endToken) {
        int start = text.indexOf(startToken);
        int end = text.indexOf(endToken);
        if (start < 0 || end <= start) {
            return "";
        }
        return text.substring(start + startToken.length(), end).trim();
    }

    private int extractInt(String text, String startToken, String endToken, int defaultValue) {
        String value = extractBetween(text, startToken, endToken);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String toJson(String targetClass, List<GeneratedTestCase> cases) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"targetClass\":\"").append(escape(targetClass)).append("\",");
        json.append("\"summary\":\"为 ").append(escape(targetClass)).append(" 生成 ").append(cases.size())
            .append(" 个测试用例\",");
        json.append("\"testCases\":[");
        for (int i = 0; i < cases.size(); i++) {
            GeneratedTestCase tc = cases.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"name\":\"").append(escape(tc.name())).append("\",");
            json.append("\"description\":\"").append(escape(tc.description())).append("\",");
            json.append("\"input\":\"").append(escape(tc.input())).append("\",");
            json.append("\"expectedResult\":\"").append(escape(tc.expectedResult())).append("\",");
            json.append("\"mockData\":[");
            List<MockDataEntry> mockData = tc.mockData();
            for (int j = 0; j < mockData.size(); j++) {
                MockDataEntry entry = mockData.get(j);
                if (j > 0) {
                    json.append(',');
                }
                json.append("{\"field\":\"").append(escape(entry.field())).append("\",");
                json.append("\"value\":\"").append(escape(entry.value())).append("\"}");
            }
            json.append("]}");
        }
        json.append("]}");
        return json.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

}
