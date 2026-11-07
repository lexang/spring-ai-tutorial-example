package com.example.springai;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.springai.controller.MultimodalController;
import com.example.springai.service.MultimodalService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MultimodalBinaryHttpTests {
    @Test void mvcWritesEveryChunkAsBinaryInOrder() throws Exception {
        var service = mock(MultimodalService.class);
        when(service.streamSpeech("测试")).thenReturn(Flux.just(new byte[] {1, 2}, new byte[] {3, 4}));
        var mvc = MockMvcBuilders.standaloneSetup(new MultimodalController(service)).build();
        var result = mvc.perform(post("/api/audio/speech/stream").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"测试\"}")).andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(5000);
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
    }

    @Test void failedOutputCancelsTheUpstream() {
        AtomicBoolean cancelled = new AtomicBoolean();
        var service = mock(MultimodalService.class);
        when(service.streamSpeech("测试")).thenReturn(Flux.concat(Flux.just(new byte[] {1}), Flux.<byte[]>never())
                .doOnCancel(() -> cancelled.set(true)));
        var body = new MultimodalController(service).streamSpeechPost(new MultimodalController.SpeechTextInput("测试"));
        OutputStream failingOutput = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("模拟连接断开"); }
        };
        assertThatThrownBy(() -> body.getBody().writeTo(failingOutput)).isInstanceOf(IOException.class);
        assertThat(cancelled).isTrue();
    }
}
