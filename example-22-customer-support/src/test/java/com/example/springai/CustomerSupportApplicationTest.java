package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerSupportApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultOfflineApplicationHandlesRagAndToolRequests() throws Exception {
        this.mockMvc.perform(post("/api/support/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": "customer-a",
                      "conversationId": "http-case",
                      "message": "如何申请退款？"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer", containsString("客服知识库")))
            .andExpect(jsonPath("$.sources[0].source").exists());

        this.mockMvc.perform(post("/api/support/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": "customer-a",
                      "conversationId": "http-case",
                      "message": "请查询订单 ORD-1001 的物流状态"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer", containsString("SF123456789")));
    }

    @Test
    void invalidRequestReturnsChineseBadRequest() throws Exception {
        this.mockMvc.perform(post("/api/support/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": "customer-a",
                      "conversationId": "../other",
                      "message": "测试"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("conversationId 只能包含")));
    }

    @Test
    void conversationCanBeCleared() throws Exception {
        this.mockMvc.perform(post("/api/support/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": "customer-a",
                      "conversationId": "delete-case",
                      "message": "多久发货？"
                    }
                    """))
            .andExpect(status().isOk());

        this.mockMvc.perform(delete("/api/support/conversations")
                .param("customerId", "customer-a")
                .param("conversationId", "delete-case"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedMessages").value(2));
    }

}
