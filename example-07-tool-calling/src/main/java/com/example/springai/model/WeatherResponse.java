package com.example.springai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 天气查询响应
 *
 * 用于 Function 接口的输出结果
 */
public record WeatherResponse(
        @JsonProperty(value = "location")
        String location,

        @JsonProperty(value = "temperature")
        double temperature,

        @JsonProperty(value = "unit")
        String unit,

        @JsonProperty(value = "condition")
        String condition,

        @JsonProperty(value = "humidity")
        int humidity,

        @JsonProperty(value = "wind_speed")
        String windSpeed
) {
    @Override
    public String toString() {
        return String.format("""
                天气信息:
                地点: %s
                温度: %.1f°%s
                天气: %s
                湿度: %d%%
                风速: %s
                """,
                location,
                temperature,
                "celsius".equals(unit) ? "C" : "F",
                condition,
                humidity,
                windSpeed);
    }
}
