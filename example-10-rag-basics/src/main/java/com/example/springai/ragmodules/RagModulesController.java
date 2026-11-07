package com.example.springai.ragmodules;

import java.util.List;

import com.example.springai.ragmodules.preretrieval.ConversationCompressionService;
import com.example.springai.ragmodules.preretrieval.MultilingualRagService;
import com.example.springai.ragmodules.preretrieval.QueryExpansionService;
import com.example.springai.ragmodules.preretrieval.QueryRewriteService;
import com.example.springai.support.RagInputValidator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单独观察模块化 RAG 的前检索组件输出。
 */
@RestController
@RequestMapping("/api/rag/modules")
public class RagModulesController {

    private final ConversationCompressionService compressionService;
    private final MultilingualRagService multilingualRagService;
    private final QueryExpansionService expansionService;
    private final QueryRewriteService rewriteService;

    public RagModulesController(
            ConversationCompressionService compressionService,
            MultilingualRagService multilingualRagService,
            QueryExpansionService expansionService,
            QueryRewriteService rewriteService) {
        this.compressionService = compressionService;
        this.multilingualRagService = multilingualRagService;
        this.expansionService = expansionService;
        this.rewriteService = rewriteService;
    }

    @GetMapping("/rewrite")
    public QueryResult rewrite(@RequestParam String question) {
        String original = RagInputValidator.requireQuestion(question);
        Query transformed = this.rewriteService.rewriteQuery(original);
        return new QueryResult(original, transformed.text());
    }

    @GetMapping("/translate")
    public QueryResult translate(@RequestParam String question) {
        String original = RagInputValidator.requireQuestion(question);
        Query transformed = this.multilingualRagService.translateQuery(original);
        return new QueryResult(original, transformed.text());
    }

    @GetMapping("/expand")
    public ExpansionResult expand(@RequestParam String question) {
        String original = RagInputValidator.requireQuestion(question);
        List<String> queries = this.expansionService.expandQuery(original).stream()
                .map(Query::text)
                .toList();
        return new ExpansionResult(original, queries);
    }

    @GetMapping("/compress")
    public QueryResult compress(
            @RequestParam String question,
            @RequestParam String previousQuestion,
            @RequestParam String previousAnswer) {
        String currentQuestion = RagInputValidator.requireQuestion(question);
        List<Message> history = List.of(
                new UserMessage(RagInputValidator.requireQuestion(previousQuestion)),
                new AssistantMessage(RagInputValidator.requireQuestion(previousAnswer))
        );
        Query transformed = this.compressionService.compressConversation(currentQuestion, history);
        return new QueryResult(currentQuestion, transformed.text());
    }

    public record QueryResult(String original, String transformed) {
    }

    public record ExpansionResult(String original, List<String> queries) {
    }
}
