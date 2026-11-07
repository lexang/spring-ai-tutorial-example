package com.example.springai;

import com.example.springai.support.CodeEngineeringException;
import com.example.springai.support.CodeEngineeringExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CodeEngineeringExceptionHandlerTest {

    @Test
    void operationFailureMapsToBadGateway() {
        var response = new CodeEngineeringExceptionHandler()
            .operation(new CodeEngineeringException("结构化转换失败"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(502);
    }

}
