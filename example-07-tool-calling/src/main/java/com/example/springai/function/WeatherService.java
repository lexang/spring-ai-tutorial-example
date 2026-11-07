package com.example.springai.function;

import com.example.springai.model.WeatherRequest;
import com.example.springai.model.WeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * 天气服务 - 使用 Function 接口实现工具
 *
 * 这是 Part2 中介绍的使用 Function 接口定义工具的方式
 * 实现 Function<WeatherRequest, WeatherResponse> 接口
 */
public class WeatherService implements Function<WeatherRequest, WeatherResponse> {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    @Override
    public WeatherResponse apply(WeatherRequest request) {
        if (request == null || request.location() == null || request.location().isBlank()) {
            throw new IllegalArgumentException("城市名称不能为空");
        }

        String unit = request.unit() == null || request.unit().isBlank() ? "celsius" : request.unit();
        if (!"celsius".equals(unit) && !"fahrenheit".equals(unit)) {
            throw new IllegalArgumentException("温度单位必须是 celsius 或 fahrenheit");
        }

        log.info("WeatherService 被调用: location={}, unit={}",
                request.location(), unit);

        // 模拟调用真实的天气 API
        // 在实际应用中,这里会调用真实的天气服务

        double temperature = "fahrenheit".equals(unit) ? 72.0 : 22.0;

        WeatherResponse response = new WeatherResponse(
                request.location(),
                temperature,
                unit,
                "晴朗",
                45,
                "微风 3级"
        );

        log.info("WeatherService 返回结果: {}", response);
        return response;
    }
}
