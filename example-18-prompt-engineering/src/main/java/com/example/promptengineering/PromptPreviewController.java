package com.example.promptengineering;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 离线预览提示词。所有端点都只构造文本，不调用模型。
 */
@RestController
@RequestMapping("/api/prompts")
public class PromptPreviewController {

    private final PromptPatternCatalog catalog;

    public PromptPreviewController(PromptPatternCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/zero-shot")
    public Map<String, String> zeroShot(@RequestParam String review) {
        return Map.of("pattern", "zero-shot", "prompt", catalog.render(catalog.zeroShot(review)));
    }

    @GetMapping("/few-shot")
    public Map<String, String> fewShot(@RequestParam String order) {
        return Map.of("pattern", "few-shot", "prompt", catalog.render(catalog.fewShot(order)));
    }

    @GetMapping("/contextual")
    public Map<String, String> contextual(@RequestParam String context, @RequestParam String question) {
        return Map.of("pattern", "contextual", "prompt",
                catalog.render(catalog.contextual(context, question)));
    }

    @GetMapping("/code")
    public Map<String, String> code(@RequestParam String language, @RequestParam String source) {
        return Map.of("pattern", "code", "prompt", catalog.render(catalog.codePrompt(language, source)));
    }

    @GetMapping("/tree-of-thoughts")
    public Map<String, Object> treeOfThoughts(@RequestParam String problem) {
        List<String> branches = catalog.treeOfThoughtsBranches(problem).stream()
                .map(catalog::render)
                .toList();
        return Map.of("pattern", "tree-of-thoughts", "branches", branches);
    }

}
