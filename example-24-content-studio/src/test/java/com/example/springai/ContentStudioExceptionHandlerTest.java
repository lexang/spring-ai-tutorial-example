package com.example.springai;

import com.example.springai.support.ContentStudioException;
import com.example.springai.support.ContentStudioExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ContentStudioExceptionHandlerTest {

    @Test
    void modelFailureMapsToBadGateway() {
        var response = new ContentStudioExceptionHandler()
            .operation(new ContentStudioException("多模态生成失败"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(502);
    }

}
