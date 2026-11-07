package com.example.springai.config;

import com.example.springai.exception.CustomExceptionProcessor;
import com.example.springai.model.WeatherRequest;
import com.example.springai.model.WeatherResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具配置类 - Bean 方式注册工具
 *
 * 演示 Part2 中使用显式 ToolCallback Bean 的方式。
 * 使用常量定义工具名称，避免硬编码，提高代码可维护性。
 */
@Configuration
public class ToolConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ToolConfiguration.class);

    // ========== 工具名称常量 ==========
    // 定义工具名称常量，避免硬编码，提供类型安全

    /**
     * 当前天气工具名称
     */
    public static final String CURRENT_WEATHER = "currentWeather";

    /**
     * 天气预报工具名称
     */
    public static final String WEATHER_FORECAST = "weatherForecast";


    // 仅在 custom-tool-errors profile 下替换默认异常处理器；默认情况下保留
    // spring.ai.tools.throw-exception-on-error 属性控制的官方实现。
    @Bean
    @Profile("custom-tool-errors")
    public CustomExceptionProcessor customExceptionProcessor() {
        return new CustomExceptionProcessor();
    }

    // ========== Bean 工具定义 ==========

    /**
     * 注册天气查询工具
     *
     * 以 @Bean 注册 ToolCallback；ChatClient 调用时显式传入回调。
     */
    @Bean(CURRENT_WEATHER)  // 使用常量作为 Bean 名称
    public ToolCallback currentWeather() {
        return FunctionToolCallback.builder(CURRENT_WEATHER, (WeatherRequest request) -> {
            log.info("currentWeather Bean 被调用: {}", request);

            if (request == null || request.location() == null || request.location().isBlank()) {
                throw new IllegalArgumentException("城市名称不能为空");
            }
            String unit = request.unit() == null || request.unit().isBlank() ? "celsius" : request.unit();
            if (!"celsius".equals(unit) && !"fahrenheit".equals(unit)) {
                throw new IllegalArgumentException("温度单位必须是 celsius 或 fahrenheit");
            }

            // 模拟天气查询
            double temperature = "celsius".equals(unit) ? 25.0 : 77.0;

            return new WeatherResponse(
                    request.location(),
                    temperature,
                    unit,
                    "晴朗",
                    45,
                    "微风 3级"
            );
        }).description("获取指定城市的当前天气信息,包括温度、天气状况、湿度和风速")
                .inputType(WeatherRequest.class)
                .build();
    }

    /**
     * 注册天气预报工具
     */
    @Bean(WEATHER_FORECAST)  // 使用常量作为 Bean 名称
    public ToolCallback weatherForecast() {
        return FunctionToolCallback.builder(WEATHER_FORECAST, (ForecastRequest request) -> {
            log.info("weatherForecast Bean 被调用: city={}, days={}",
                    request != null ? request.city() : null,
                    request != null ? request.days() : null);

            if (request == null || request.city() == null || request.city().isBlank()) {
                throw new IllegalArgumentException("城市名称不能为空");
            }
            if (request.days() < 1 || request.days() > 7) {
                throw new IllegalArgumentException("预报天数必须在 1-7 之间");
            }

            // 生成模拟预报数据
            List<DailyForecast> forecasts = new ArrayList<>();
            for (int i = 1; i <= request.days(); i++) {
                forecasts.add(new DailyForecast(
                        "2024-01-" + (15 + i),
                        20.0 + i,
                        i % 2 == 0 ? "晴" : "多云"
                ));
            }

            return new ForecastResponse(request.city(), request.days(), forecasts);
        }).description("获取指定城市未来几天的天气预报")
                .inputType(ForecastRequest.class)
                .build();
    }

    /**
     * 天气预报请求
     */
    public record ForecastRequest(
            @ToolParam(description = "城市名称,例如: 北京、上海")
            String city,

            @ToolParam(description = "预报天数,1-7天")
            int days
    ) {}

    /**
     * 天气预报响应
     */
    public record ForecastResponse(
            @JsonProperty("city") String city,
            @JsonProperty("days") int days,
            @JsonProperty("forecasts") List<DailyForecast> forecasts
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(city).append(" 未来 ").append(days).append(" 天天气预报:\n\n");

            for (int i = 0; i < forecasts.size(); i++) {
                DailyForecast f = forecasts.get(i);
                sb.append(String.format("第%d天 (%s): %s, %.1f°C\n",
                        i + 1, f.date(), f.condition(), f.temp()));
            }

            return sb.toString();
        }
    }

    /**
     * 每日天气预报
     */
    public record DailyForecast(
            @JsonProperty("date") String date,
            @JsonProperty("temp") double temp,
            @JsonProperty("condition") String condition
    ) {}
}
