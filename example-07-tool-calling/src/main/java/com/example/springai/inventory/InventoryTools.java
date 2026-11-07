package com.example.springai.inventory;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** 只读演示库存。没有真实数据库，也不提供扣库存或下单能力。 */
@Component
public class InventoryTools {

    private static final Logger log = LoggerFactory.getLogger(InventoryTools.class);
    private static final Map<String, Map<String, Integer>> STOCK = Map.of(
            "demo-shanghai", Map.of("BOOK-001", 12, "BOOK-002", 0),
            "demo-beijing", Map.of("BOOK-001", 3, "BOOK-002", 8));

    @Tool(description = "按商品编号查询当前演示仓库的可用库存，只读，不预订或扣减库存")
    public StockSnapshot queryStock(
            @ToolParam(description = "商品编号，例如 BOOK-001；不知道编号时先向用户询问") String sku,
            ToolContext toolContext) {
        if (sku == null || !sku.matches("BOOK-\\d{3}")) {
            throw new IllegalArgumentException("商品编号必须符合 BOOK-001 格式");
        }
        Object warehouse = toolContext.getContext().get("warehouseId");
        if (!(warehouse instanceof String)) {
            throw new IllegalStateException("未配置可用的演示仓库");
        }
        Map<String, Integer> inventory = STOCK.get(warehouse);
        if (inventory == null) {
            throw new IllegalStateException("未配置可用的演示仓库");
        }
        Integer available = inventory.get(sku);
        if (available == null) {
            throw new IllegalArgumentException("未找到这个演示商品，请核对商品编号");
        }
        // 不记录问题、模型参数、仓库身份或库存正文。
        log.info("执行只读库存工具 queryStock");
        return new StockSnapshot(sku, warehouse.toString(), available, "固定演示数据，不代表真实库存");
    }

    public record StockSnapshot(String sku, String warehouseId, int available, String source) {
    }

    /** 单独的工具对象用于演示直接返回；不要与同名普通工具合并。 */
    public static class DirectInventoryTools {
        private final InventoryTools inventoryTools;

        public DirectInventoryTools(InventoryTools inventoryTools) {
            this.inventoryTools = inventoryTools;
        }

        @Tool(name = "queryStock", description = "按商品编号查询演示库存并直接返回结果，只读", returnDirect = true)
        public StockSnapshot queryStock(
                @ToolParam(description = "商品编号，例如 BOOK-001") String sku, ToolContext toolContext) {
            return inventoryTools.queryStock(sku, toolContext);
        }
    }
}
