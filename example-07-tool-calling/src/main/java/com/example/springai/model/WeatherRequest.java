package com.example.springai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 天气查询请求
 *
 * 用于 Function 接口的输入参数
 */
public record WeatherRequest(
        @JsonProperty(value = "location", required = true)
        @JsonPropertyDescription("城市名称,例如: 北京、上海")
        String location,

        @JsonProperty(value = "unit", required = false, defaultValue = "celsius")
        @JsonPropertyDescription("温度单位: celsius(摄氏度) 或 fahrenheit(华氏度)")
        String unit
) {
    /**
     * 创建摄氏度请求
     */
    public static WeatherRequest celsius(String location) {
        return new WeatherRequest(location, "celsius");
    }

    /**
     * 创建华氏度请求
     */
    public static WeatherRequest fahrenheit(String location) {
        return new WeatherRequest(location, "fahrenheit");
    }
}
