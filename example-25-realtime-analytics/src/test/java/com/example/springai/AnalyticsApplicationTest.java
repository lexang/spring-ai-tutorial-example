package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void queryEndpointReturnsSqlDataAndChart() throws Exception {
        this.mockMvc.perform(post("/api/analytics/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question":"比较各地区销售额",
                      "startDate":"2026-07-20",
                      "endDate":"2026-07-26",
                      "limit":10
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.metric").value("REVENUE"))
            .andExpect(jsonPath("$.plan.dimension").value("REGION"))
            .andExpect(jsonPath("$.sql", containsString("SUM(revenue)")))
            .andExpect(jsonPath("$.chart.type").value("bar"))
            .andExpect(jsonPath("$.data[0].label").value("华南"));
    }

    @Test
    void catalogAndEventEndpointsAreAvailable() throws Exception {
        this.mockMvc.perform(get("/api/analytics/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metrics[0].code").value("REVENUE"))
            .andExpect(jsonPath("$.dimensions[3].code").value("DAY"));

        this.mockMvc.perform(post("/api/analytics/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "saleDate":"2031-04-02",
                      "region":"华南",
                      "product":"MCP 工作坊",
                      "channel":"企业直销",
                      "units":1,
                      "revenue":499.00
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.insertedRows").value(1));
    }

    @Test
    void invalidInputReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/analytics/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question":" ",
                      "startDate":"2026-07-20",
                      "endDate":"2026-07-26"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("question 不能为空")));
    }

}
