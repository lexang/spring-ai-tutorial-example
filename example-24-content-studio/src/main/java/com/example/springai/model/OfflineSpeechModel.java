package com.example.springai.model;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import reactor.core.publisher.Flux;

/** 返回可播放的半秒静音 PCM WAV，只验证音频契约，不是合成语音。 */
public final class OfflineSpeechModel implements TextToSpeechModel {

    @Override
    public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
        int samples = 4000;
        int dataBytes = samples * 2;
        ByteBuffer wav = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataBytes)
            .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
            .putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000)
            .putShort((short) 2).putShort((short) 16)
            .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataBytes);
        byte[] bytes = wav.array();
        return new TextToSpeechResponse(List.of(new Speech(bytes)));
    }

    @Override
    public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
        return Flux.just(call(prompt));
    }

}
