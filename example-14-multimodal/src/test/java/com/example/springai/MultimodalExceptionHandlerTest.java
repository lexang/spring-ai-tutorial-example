package com.example.springai;

import com.example.springai.support.MultimodalExceptionHandler;
import com.example.springai.support.MultimodalOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MultimodalExceptionHandlerTest {

    @Test
    void modelOperationFailureMapsToChineseBadGatewayResponse() {
        MultimodalExceptionHandler handler = new MultimodalExceptionHandler();

        ResponseEntity<MultimodalExceptionHandler.ApiError> response = handler.handleModelOperation(
                new MultimodalOperationException("图片生成模型调用失败"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().message()).isEqualTo("图片生成模型调用失败");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

}
