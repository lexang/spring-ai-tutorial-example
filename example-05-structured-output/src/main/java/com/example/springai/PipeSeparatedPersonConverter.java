package com.example.springai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.converter.StructuredOutputConverter;

/**
 * 非 JSON 转换器示例：每行使用 name|age|job 表示一个人员。
 *
 * <p>该格式没有 JSON Schema，因此只适合普通提示词路径，不能使用
 * validateSchema() 或供应商原生 JSON Schema 约束。</p>
 */
public final class PipeSeparatedPersonConverter
        implements StructuredOutputConverter<List<Models.Person>> {

    @Override
    public String getFormat() {
        return """
                每行只返回一个人员，格式严格为 name|age|job。
                不要返回表头、编号、Markdown 代码块或其他解释。
                """;
    }

    @Override
    public List<Models.Person> convert(String source) {
        String text = StructuredOutputResponseSupport.requireText(source);
        List<Models.Person> people = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split("\\|", -1);
            if (columns.length != 3) {
                throw new InvalidStructuredOutputResponseException(
                        "第 " + (index + 1) + " 行不是 name|age|job 格式");
            }
            try {
                people.add(new Models.Person(
                        columns[0].trim(),
                        Integer.parseInt(columns[1].trim()),
                        columns[2].trim()));
            }
            catch (NumberFormatException exception) {
                throw new InvalidStructuredOutputResponseException(
                        "第 " + (index + 1) + " 行的年龄不是整数");
            }
        }
        return List.copyOf(StructuredOutputResponseSupport.requireNonEmpty(people, "人员列表"));
    }

}
