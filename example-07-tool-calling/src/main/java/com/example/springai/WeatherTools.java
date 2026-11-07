package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 天气工具类
 *
 * 提供天气查询功能 (演示用,返回模拟数据)
 */
@Component
public class WeatherTools {

    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);
    private final Random random = new Random();

    /**
     * 获取当前天气
     */
    @Tool(description = "获取指定城市的当前天气信息,包括温度、天气状况、湿度和风速")
    public String getCurrentWeather(
            @ToolParam(description = "城市名称,例如: 北京、上海、深圳") String city) {

        log.info("工具被调用: getCurrentWeather, 参数: city={}", city);

        // 模拟天气数据
        int temperature = 15 + random.nextInt(15); // 15-30度
        String[] conditions = {"晴朗", "多云", "阴天", "小雨"};
        String condition = conditions[random.nextInt(conditions.length)];
        int humidity = 30 + random.nextInt(40); // 30-70%
        int wind = 1 + random.nextInt(4); // 1-5级

        String result = String.format("""
                城市: %s
                温度: %d°C
                天气: %s
                湿度: %d%%
                风速: %d级
                """, city, temperature, condition, humidity, wind);

        log.info("返回天气信息: {}", result);
        return result;
    }

    /**
     * 获取天气预报
     */
    @Tool(description = "获取指定城市未来几天的天气预报")
    public String getWeatherForecast(
            @ToolParam(description = "城市名称") String city,
            @ToolParam(description = "预报天数,1-7天", required = false) Integer days) {

        // 默认预报 3 天
        if (days == null) {
            days = 3;
        }

        // 限制在 1-7 天
        if (days < 1 || days > 7) {
            days = 3;
        }

        log.info("工具被调用: getWeatherForecast, 参数: city={}, days={}", city, days);

        StringBuilder forecast = new StringBuilder();
        forecast.append(city).append(" 未来 ").append(days).append(" 天天气预报:\n\n");

        String[] conditions = {"晴朗", "多云", "阴天", "小雨", "雷阵雨"};

        for (int i = 1; i <= days; i++) {
            int tempMin = 15 + random.nextInt(10);
            int tempMax = tempMin + 5 + random.nextInt(5);
            String condition = conditions[random.nextInt(conditions.length)];

            forecast.append(String.format("第%d天: %s, %d-%d°C\n",
                    i, condition, tempMin, tempMax));
        }

        String result = forecast.toString();
        log.info("返回天气预报");
        return result;
    }
}
