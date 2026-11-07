package com.example.springai;

import com.example.springai.support.KnowledgeExceptionHandler;
import com.example.springai.support.KnowledgeOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeExceptionHandlerTest {

    @Test
    void operationFailureMapsToBadGateway() {
        var response = new KnowledgeExceptionHandler()
            .handleOperation(new KnowledgeOperationException("文档检索或回答生成失败"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().message()).isEqualTo("文档检索或回答生成失败");
    }

}
