package com.example.springai.inventory;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST 请求避免把完整问题放进 URL。示例没有认证，应仅在本地教学环境使用。 */
@RestController
@RequestMapping("/api/tools")
public class ToolQueryController {
    private final ToolQueryService service;

    public ToolQueryController(ToolQueryService service) { this.service = service; }

    @PostMapping("/query")
    public ToolQueryService.QueryResult query(@RequestBody ToolQueryService.QueryRequest request) {
        return service.query(request);
    }
}
