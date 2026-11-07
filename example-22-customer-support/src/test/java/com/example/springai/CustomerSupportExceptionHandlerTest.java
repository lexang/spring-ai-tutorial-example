package com.example.springai;

import com.example.springai.support.CustomerSupportExceptionHandler;
import com.example.springai.support.CustomerSupportOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerSupportExceptionHandlerTest {

    @Test
    void operationFailureMapsToChineseBadGateway() {
        var response = new CustomerSupportExceptionHandler()
            .handleOperation(new CustomerSupportOperationException("客服回答生成失败"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().message()).isEqualTo("客服回答生成失败");
    }

}
