package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TestGenApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generateEndpointReturnsTestPlan() throws Exception {
        this.mockMvc.perform(post("/api/test-gen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetClass":"UserService",
                      "classSignature":"public class UserService { boolean login(String username, String password); }",
                      "instruction":"覆盖正常登录和错误密码",
                      "maxCases":3
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetClass").value("UserService"))
            .andExpect(jsonPath("$.testCases", org.hamcrest.Matchers.hasSize(3)))
            .andExpect(jsonPath("$.testCases[0].name", containsString("test_")))
            .andExpect(jsonPath("$.summary", containsString("UserService")));
    }

    @Test
    void evaluateEndpointReturnsEvaluation() throws Exception {
        this.mockMvc.perform(post("/api/test-gen/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetClass":"UserService",
                      "summary":"为 UserService 生成 1 个测试用例",
                      "testCases":[
                        {"name":"userservice_test_1","description":"验证基本路径","input":"input1","expectedResult":"expected1","mockData":[{"field":"id","value":"1"}]}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedback").exists());
    }

    @Test
    void blankTargetClassReturnsChinese400() throws Exception {
        this.mockMvc.perform(post("/api/test-gen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetClass":"",
                      "classSignature":"public class A {}"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("targetClass 不能为空")));
    }

    @Test
    void maxCasesOutOfRangeReturnsChinese400() throws Exception {
        this.mockMvc.perform(post("/api/test-gen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetClass":"A",
                      "classSignature":"public class A {}",
                      "maxCases":99
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("1 到 10")));
    }

}
