package com.example.springai.part2;

import com.example.springai.ragmodules.postretrieval.TopDocumentPostProcessor;
import com.example.springai.ragmodules.preretrieval.DistinctQueryExpander;
import com.example.springai.support.RagChatClientBuilders;
import com.example.springai.support.RagInputValidator;
import com.example.springai.support.RagResponseValidator;
import com.example.springai.support.RagResultMapper;
import com.example.springai.support.RagUpstreamException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询转换、查询扩展和完整模块化 RAG 流程。
 */
@RestController
@RequestMapping("/api/rag/part2")
public class Part2AdvancedRagController {

    private final ChatClient rewriteChatClient;
    private final ChatClient translationChatClient;
    private final ChatClient expansionChatClient;
    private final ChatClient advancedChatClient;

    public Part2AdvancedRagController(ChatClient.Builder builder, VectorStore vectorStore) {
        ChatClient.Builder transformerBuilder = RagChatClientBuilders.forTransformation(builder);

        RewriteQueryTransformer rewriter = RewriteQueryTransformer.builder()
                .chatClientBuilder(transformerBuilder.clone())
                .build();
        RetrievalAugmentationAdvisor rewriteAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(rewriter)
                .documentRetriever(retriever(vectorStore, 0.60, 5))
                .build();
        this.rewriteChatClient = builder.clone()
                .defaultAdvisors(rewriteAdvisor)
                .build();

        TranslationQueryTransformer translator = TranslationQueryTransformer.builder()
                .chatClientBuilder(transformerBuilder.clone())
                .targetLanguage("english")
                .build();
        RetrievalAugmentationAdvisor translationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(translator)
                .documentRetriever(retriever(vectorStore, 0.60, 5))
                .build();
        this.translationChatClient = builder.clone()
                .defaultAdvisors(translationAdvisor)
                .build();

        QueryExpander expander = new DistinctQueryExpander(MultiQueryExpander.builder()
                .chatClientBuilder(transformerBuilder.clone())
                .numberOfQueries(3)
                .includeOriginal(true)
                .build());
        RetrievalAugmentationAdvisor expansionAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryExpander(expander)
                .documentRetriever(retriever(vectorStore, 0.55, 3))
                .build();
        this.expansionChatClient = builder.clone()
                .defaultAdvisors(expansionAdvisor)
                .build();

        RewriteQueryTransformer advancedRewriter = RewriteQueryTransformer.builder()
                .chatClientBuilder(transformerBuilder.clone())
                .build();
        QueryExpander advancedExpander = new DistinctQueryExpander(MultiQueryExpander.builder()
                .chatClientBuilder(transformerBuilder.clone())
                .numberOfQueries(2)
                .includeOriginal(true)
                .build());
        RetrievalAugmentationAdvisor advancedAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(advancedRewriter)
                .queryExpander(advancedExpander)
                .documentRetriever(retriever(vectorStore, 0.60, 4))
                .documentPostProcessors(new TopDocumentPostProcessor(5))
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .build())
                .build();
        this.advancedChatClient = builder.clone()
                .defaultAdvisors(advancedAdvisor)
                .build();
    }

    @GetMapping("/rewrite")
    public String rewriteRag(@RequestParam String question) {
        return ask(this.rewriteChatClient, question);
    }

    /**
     * 将检索查询翻译为英语；最终回答语言仍由原始问题和模型决定。
     */
    @GetMapping("/translate")
    public String translationRag(@RequestParam String question) {
        return ask(this.translationChatClient, question);
    }

    @GetMapping("/expand")
    public String expansionRag(@RequestParam String question) {
        return ask(this.expansionChatClient, question);
    }

    @GetMapping("/advanced")
    public String advancedRag(@RequestParam String question) {
        return ask(this.advancedChatClient, question);
    }

    /** 与 GET 使用同一个高级客户端，来源取自后处理之后的文档 context。 */
    @PostMapping("/advanced")
    public RagResultMapper.RagAnswer advancedWithSources(@RequestBody QuestionInput input) {
        String question = RagInputValidator.requireQuestion(input == null ? null : input.question());
        try {
            var response = advancedChatClient.prompt().user(question).call().chatClientResponse();
            return RagResultMapper.from(response, RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        }
        catch (RagUpstreamException exception) { throw exception; }
        catch (RuntimeException exception) {
            throw new RagUpstreamException("RAG 改写、检索或生成失败，请检查服务配置", exception);
        }
    }

    public record QuestionInput(String question) { }

    @GetMapping("/health")
    public String health() {
        return "Part 2 高级 RAG 接口正常";
    }

    private String ask(ChatClient chatClient, String question) {
        String content = chatClient.prompt()
                .user(RagInputValidator.requireQuestion(question))
                .call()
                .content();
        return RagResponseValidator.requireContent(content);
    }

    private VectorStoreDocumentRetriever retriever(
            VectorStore vectorStore, double similarityThreshold, int topK) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();
    }
}
