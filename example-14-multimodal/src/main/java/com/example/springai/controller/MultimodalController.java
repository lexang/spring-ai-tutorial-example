package com.example.springai.controller;

import com.example.springai.service.MultimodalService;
import com.example.springai.service.MultimodalService.ImageGenerationReply;
import com.example.springai.service.MultimodalService.ImageRequest;
import com.example.springai.service.MultimodalService.ModelStatus;
import com.example.springai.service.MultimodalService.MultimodalReply;
import com.example.springai.service.MultimodalService.SpeechAudio;
import com.example.springai.service.MultimodalService.SpeechRequest;
import com.example.springai.service.MultimodalService.TranscriptionReply;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.UncheckedIOException;
import reactor.core.scheduler.Schedulers;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class MultimodalController {

    private final MultimodalService multimodalService;

    public MultimodalController(MultimodalService multimodalService) {
        this.multimodalService = multimodalService;
    }

    @GetMapping("/multimodal/status")
    public ModelStatus status() {
        return this.multimodalService.status();
    }

    @PostMapping(value = "/multimodal/describe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MultimodalReply describeImage(@RequestPart("file") MultipartFile file,
            @RequestParam(value = "question", defaultValue = "请描述图片内容") String question) {
        return this.multimodalService.describeImage(file, question);
    }

    @PostMapping("/images/generate")
    public ImageGenerationReply generateImage(@RequestBody ImageRequest request) {
        return this.multimodalService.generateImage(request);
    }

    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionReply transcribe(@RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language) {
        return this.multimodalService.transcribe(file, language);
    }

    @PostMapping(value = "/audio/speech", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> synthesize(@RequestBody SpeechRequest request) {
        SpeechAudio result = this.multimodalService.synthesize(request);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(result.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=generated-speech." + result.format())
            .body(result.audio());
    }

    @GetMapping(value = "/audio/speech/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamSpeech(@RequestParam String text) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(binaryStream(this.multimodalService.streamSpeech(text)));
    }

    @PostMapping(value = "/audio/speech/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamSpeechPost(@RequestBody SpeechTextInput input) {
        if (input == null) { throw new IllegalArgumentException("请求体不能为空"); }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(binaryStream(this.multimodalService.streamSpeech(input.text())));
    }

    /** Spring MVC 使用字节写入器，不把 Flux<byte[]> 当成 JSON 数组返回。 */
    private StreamingResponseBody binaryStream(Flux<byte[]> chunks) {
        return output -> {
            try {
                chunks.publishOn(Schedulers.boundedElastic(), 1)
                        .doOnNext(bytes -> {
                            try { output.write(bytes); output.flush(); }
                            catch (java.io.IOException exception) { throw new UncheckedIOException(exception); }
                        }).blockLast();
            }
            catch (UncheckedIOException exception) {
                // 输出失败会取消上游订阅，不继续输出客户端无法接收的音频。
                throw exception.getCause();
            }
        };
    }

    public record SpeechTextInput(String text) { }

}
