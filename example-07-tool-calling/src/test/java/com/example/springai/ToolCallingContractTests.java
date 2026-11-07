package com.example.springai;

import com.example.springai.context.CustomerTools;
import com.example.springai.function.WeatherService;
import com.example.springai.method.MethodToolCallbackFactory;
import com.example.springai.model.WeatherRequest;
import com.example.springai.schema.BookingTools;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallingContractTests {

    @Test
    void shouldGenerateDefinitionsFromAnnotatedMethods() {
        ToolCallback[] callbacks = ToolCallbacks.from(new DateTimeTools());

        assertThat(callbacks).hasSize(3);
        assertThat(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder("getCurrentDateTime", "setAlarm", "getDayOfWeek");
        assertThat(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().inputSchema()))
                .allMatch(schema -> schema.contains("type"));
    }

    @Test
    void shouldExposeUniqueToolNames() {
        ToolCallback[] callbacks = ToolCallbacks.from(new CalculatorTools(), new DateTimeTools());
        Set<String> names = Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(names).hasSameSizeAs(callbacks);
    }

    @Test
    void shouldIsolateCustomerDataByTenantContext() {
        CustomerTools customerTools = new CustomerTools();

        String tenantA = customerTools.getCustomerInfo(1L,
                new ToolContext(Map.of("tenantId", "tenant-a", "userId", "user-a")));
        String tenantB = customerTools.getCustomerInfo(1L,
                new ToolContext(Map.of("tenantId", "tenant-b", "userId", "user-b")));

        assertThat(tenantA).contains("张三", "租户A", "user-a").doesNotContain("王五");
        assertThat(tenantB).contains("王五", "租户B", "user-b").doesNotContain("张三");
    }

    @Test
    void shouldGenerateExecutableMethodToolCallbacksWithSchemas() {
        MethodToolCallbackFactory factory = new MethodToolCallbackFactory();

        List<ToolCallback> tools = factory.createTimeTools();

        assertThat(tools).extracting(tool -> tool.getToolDefinition().name())
                .containsExactly("getCurrentTime", "getTimeInTimezone", "daysBetween");
        assertThat(tools).allMatch(tool -> tool.getToolDefinition().inputSchema().contains("type"));

        ToolCallback currentTime = tools.get(0);
        assertThat(currentTime.call("{}")).matches("\\\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\\"");

        ToolCallback timezone = tools.get(1);
        assertThat(timezone.call("{\"timezone\":\"Asia/Shanghai\"}")).contains("Asia/Shanghai");

        ToolCallback daysBetween = tools.get(2);
        assertThat(daysBetween.call("{\"startDate\":\"2024-01-01\",\"endDate\":\"2024-01-03\"}"))
                .isEqualTo("2");
    }

    @Test
    void shouldGenerateDynamicMethodToolWithAnInputSchema() {
        MethodToolCallbackFactory factory = new MethodToolCallbackFactory();

        ToolCallback callback = factory.createToolFromConfig(
                "com.example.springai.method.TimeTools",
                "getCurrentTime",
                "dynamicGetTime",
                "动态创建的获取当前时间工具");

        assertThat(callback.getToolDefinition().name()).isEqualTo("dynamicGetTime");
        assertThat(callback.getToolDefinition().inputSchema()).contains("type");
        assertThat(callback.call("{}")).isNotBlank();
    }

    @Test
    void shouldKeepFileSystemToolsInsideTheConfiguredBaseDirectory(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("inside.txt"), "受保护内容");
        FileSystemTools tools = new FileSystemTools(tempDir.toString());

        assertThat(Path.of(tools.getBaseDir())).isEqualTo(tempDir.toRealPath());
        assertThat(tools.listFiles(".")).contains("inside.txt");
        assertThat(tools.readFile("inside.txt")).isEqualTo("受保护内容");
        assertThat(tools.readFile("../outside.txt")).isEqualTo("错误: 无权访问该路径");
    }

    @Test
    void shouldGenerateBookingSchemaWithRequiredAndOptionalFields() throws Exception {
        ToolCallback callback = Arrays.stream(ToolCallbacks.from(new BookingTools()))
                .filter(tool -> tool.getToolDefinition().name().equals("bookRoom"))
                .findFirst()
                .orElseThrow();

        JsonNode schema = new ObjectMapper().readTree(callback.getToolDefinition().inputSchema());
        JsonNode requestSchema = schema.path("properties").path("request");
        assertThat(schema.path("required").valueStream().map(JsonNode::stringValue).toList())
                .containsExactly("request");
        List<String> required = requestSchema.path("required").valueStream()
                .map(JsonNode::stringValue)
                .toList();

        assertThat(required).containsExactlyInAnyOrder("email", "checkInDate", "checkOutDate");
        assertThat(requestSchema.path("properties").path("roomType").path("enum").toString())
                .contains("standard", "deluxe", "suite");
    }

    @Test
    void shouldGenerateFunctionSchemaWithExplicitRequiredStatus() throws Exception {
        ToolCallback callback = FunctionToolCallback.builder("currentWeather", new WeatherService())
                .description("获取天气")
                .inputType(WeatherRequest.class)
                .build();

        JsonNode schema = new ObjectMapper().readTree(callback.getToolDefinition().inputSchema());
        List<String> required = schema.path("required").valueStream()
                .map(JsonNode::stringValue)
                .toList();

        assertThat(required).containsExactly("location");
        assertThat(schema.path("properties").path("unit").path("description").stringValue())
                .contains("celsius", "fahrenheit");
        assertThat(callback.call("{\"location\":\"北京\"}"))
                .contains("北京", "celsius");
    }
}
