# Spring AI ETL Pipeline

## 概述

ETL (Extract, Transform, Load) 是 Spring AI 中用于数据处理的核心组件。

## 核心组件

### DocumentReader

负责从各种数据源读取文档：
- TextReader - 读取文本文件
- JsonReader - 读取 JSON 文件
- MarkdownDocumentReader - 读取 Markdown 文件
- PagePdfDocumentReader - 读取 PDF 文件

### DocumentTransformer

负责对文档进行转换：
- TokenTextSplitter - 按 Token 分割文本
- KeywordMetadataEnricher - 提取关键词
- SummaryMetadataEnricher - 生成摘要

### DocumentWriter

负责将文档写入目标存储：
- VectorStore - 向量数据库
- FileDocumentWriter - 文件系统

## ETL 流程示例

```java
// 读取 → 分割 → 写入
vectorStore.accept(
    tokenTextSplitter.apply(
        pdfReader.get()
    )
);
```

## 最佳实践

1. **合理分割**：根据模型的上下文窗口大小选择合适的分割参数
2. **元数据增强**：添加元数据可以提高检索准确性
3. **批量处理**：大量文档时应该分批处理
4. **错误处理**：做好异常处理和日志记录

---

## 高级特性

### 自定义 Transformer

可以实现自定义的 DocumentTransformer 来满足特殊需求：

```java
public class CustomTransformer implements DocumentTransformer {
    @Override
    public List<Document> apply(List<Document> documents) {
        // 自定义转换逻辑
        return transformedDocuments;
    }
}
```

### 链式处理

可以将多个 Transformer 串联起来：

```java
List<Document> result = transformer2.apply(
    transformer1.apply(documents)
);
```
