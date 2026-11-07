package com.example.mcp.demo.config;

import java.util.Set;

/** 仅面向本仓库已审核计算工具的教学清单，不代表身份授权。 */
public final class McpToolPolicy {
    private McpToolPolicy() { }
    public static final Set<String> ALLOWED_TOOLS = Set.of("basic-calculation", "percentage-calculation",
            "scientific-calculation", "complex-calculation");
    public static boolean allows(String name) { return name != null && ALLOWED_TOOLS.contains(name); }
}
