package com.example.springai.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void shouldReturnStructuredBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rag/part1/simple");

        var response = this.handler.handleBadRequest(
                new RagRequestException("问题不能为空"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("问题不能为空");
        assertThat(response.getBody().path()).isEqualTo("/api/rag/part1/simple");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void shouldReturnBadGatewayForEmptyModelResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rag/part2/advanced");

        var response = this.handler.handleUpstream(
                new RagUpstreamException("模型未返回可用文本，请稍后重试"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UPSTREAM_RESPONSE_INVALID");
        assertThat(response.getBody().path()).isEqualTo("/api/rag/part2/advanced");
    }
}
