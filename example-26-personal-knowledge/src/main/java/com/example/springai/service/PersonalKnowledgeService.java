package com.example.springai.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ConcurrentHashMap;

import com.example.springai.data.NoteRepository;
import com.example.springai.model.KnowledgeModels.AnswerResult;
import com.example.springai.model.KnowledgeModels.AskRequest;
import com.example.springai.model.KnowledgeModels.CreateNoteRequest;
import com.example.springai.model.KnowledgeModels.DeleteResult;
import com.example.springai.model.KnowledgeModels.KnowledgeStatus;
import com.example.springai.model.KnowledgeModels.Note;
import com.example.springai.model.KnowledgeModels.ReindexResult;
import com.example.springai.model.KnowledgeModels.SearchHit;
import com.example.springai.model.KnowledgeModels.SearchRequest;
import com.example.springai.model.KnowledgeModels.SearchResult;
import com.example.springai.model.KnowledgeModels.Source;
import com.example.springai.model.KnowledgeModels.UpdateNoteRequest;
import com.example.springai.support.KnowledgeConflictException;
import com.example.springai.support.KnowledgeNotFoundException;
import com.example.springai.support.KnowledgeOperationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class PersonalKnowledgeService {

    private static final int MAX_TOP_K = 10;
    private final NoteRepository repository;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final TransactionTemplate transactions;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Set<String> indexedIds = ConcurrentHashMap.newKeySet();

    public PersonalKnowledgeService(NoteRepository repository, VectorStore vectorStore, ChatModel chatModel,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Note create(CreateNoteRequest request) {
        String owner = owner(request == null ? null : request.ownerId());
        String title = text(request == null ? null : request.title(), "title", 120);
        String content = text(request == null ? null : request.content(), "content", 10_000);
        String tag = text(request == null ? null : request.tag(), "tag", 40);
        String id = java.util.UUID.randomUUID().toString();
        lock.writeLock().lock();
        try {
            return this.transactions.execute(status -> {
            Note note = repository.insert(id, owner, title, content, tag);
            try { vectorStore.add(List.of(toDocument(note))); indexedIds.add(id); }
            catch (RuntimeException e) { repository.delete(id, owner); throw operation("笔记向量化失败", e); }
            return note;
            });
        }
        finally { lock.writeLock().unlock(); }
    }

    public Note update(String id, UpdateNoteRequest request) {
        String noteId = identifier(id, "id");
        String owner = owner(request == null ? null : request.ownerId());
        String title = text(request == null ? null : request.title(), "title", 120);
        String content = text(request == null ? null : request.content(), "content", 10_000);
        String tag = text(request == null ? null : request.tag(), "tag", 40);
        int expected = request == null || request.expectedVersion() == null ? 0 : request.expectedVersion();
        lock.writeLock().lock();
        try {
            return this.transactions.execute(status -> {
            Note old = repository.findByIdAndOwner(noteId, owner).orElseThrow(() -> new KnowledgeNotFoundException("笔记不存在或不属于当前用户"));
            if (old.version() != expected) throw new KnowledgeConflictException("版本冲突，请先读取最新笔记");
            if (repository.update(noteId, owner, title, content, tag, expected) != 1) throw new KnowledgeConflictException("版本冲突，请重试");
            Note updated = repository.findByIdAndOwner(noteId, owner).orElseThrow();
            try { vectorStore.delete(List.of(noteId)); vectorStore.add(List.of(toDocument(updated))); indexedIds.add(noteId); }
            catch (RuntimeException e) { try { vectorStore.delete(List.of(noteId)); vectorStore.add(List.of(toDocument(old))); } catch (RuntimeException ignored) { } throw operation("笔记向量索引更新失败", e); }
            return updated;
            });
        }
        finally { lock.writeLock().unlock(); }
    }

    public DeleteResult delete(String id, String ownerId) {
        String noteId = identifier(id, "id"); String owner = owner(ownerId);
        lock.writeLock().lock();
        try {
            return this.transactions.execute(status -> {
            if (repository.findByIdAndOwner(noteId, owner).isEmpty()) throw new KnowledgeNotFoundException("笔记不存在或不属于当前用户");
            int rows = repository.delete(noteId, owner); vectorStore.delete(List.of(noteId)); indexedIds.remove(noteId); return new DeleteResult(noteId, rows);
            });
        }
        finally { lock.writeLock().unlock(); }
    }

    public SearchResult search(SearchRequest request) {
        String owner = owner(request == null ? null : request.ownerId());
        String query = text(request == null ? null : request.query(), "query", 1_000);
        String tag = optional(request == null ? null : request.tag(), "tag", 40);
        int topK = topK(request == null ? null : request.topK());
        lock.readLock().lock();
        try {
            List<Document> documents = freshDocuments(vectorStore.similaritySearch(buildSearch(query, owner, tag, topK)), owner, tag);
            List<SearchHit> hits = documents.stream().map(d -> repository.findByIdAndOwner(d.getId(), owner)
                    .map(note -> new SearchHit(note.id(), note.title(), note.tag(), note.version(), d.getScore(), excerpt(note.content())))
                    .orElse(null)).filter(java.util.Objects::nonNull).toList();
            return new SearchResult(query, hits);
        }
        catch (RuntimeException e) { throw operation("知识检索失败", e); }
        finally { lock.readLock().unlock(); }
    }

    public AnswerResult ask(AskRequest request) {
        String owner = owner(request == null ? null : request.ownerId());
        String question = text(request == null ? null : request.question(), "question", 1_000);
        String tag = optional(request == null ? null : request.tag(), "tag", 40);
        int topK = topK(request == null ? null : request.topK());
        lock.readLock().lock();
        try {
            QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(factBackedView(owner, tag))
                    .searchRequest(buildSearch(question, owner, tag, topK)).build();
            ChatClientResponse response = ChatClient.create(chatModel).prompt().advisors(advisor).user(question).call().chatClientResponse();
            if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null
                    || !StringUtils.hasText(response.chatResponse().getResult().getOutput().getText())) throw operation("回答模型没有返回可用文本", null);
            Object value = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
            List<Source> sources = value instanceof List<?> list ? list.stream().filter(Document.class::isInstance).map(Document.class::cast)
                    .map(d -> repository.findByIdAndOwner(d.getId(), owner).map(n -> new Source(n.id(), n.title(), n.tag(), n.version(), d.getScore(), excerpt(n.content()))).orElse(null))
                    .filter(java.util.Objects::nonNull).toList() : List.of();
            return new AnswerResult(question, sources.isEmpty() ? "个人知识库没有足够信息，无法依据笔记回答。"
                : response.chatResponse().getResult().getOutput().getText(), sources,
                sources.isEmpty() ? "NO_CONTEXT" : "ANSWERED");
        }
        catch (KnowledgeOperationException e) { throw e; }
        catch (RuntimeException e) { throw operation("知识问答失败", e); }
        finally { lock.readLock().unlock(); }
    }

    public ReindexResult reindexAll() {
        lock.writeLock().lock();
        try {
            List<Note> notes = repository.findAll();
            List<String> ids = List.copyOf(indexedIds);
            if (!ids.isEmpty()) vectorStore.delete(ids);
            if (!notes.isEmpty()) vectorStore.add(notes.stream().map(this::toDocument).toList());
            indexedIds.clear(); indexedIds.addAll(notes.stream().map(Note::id).toList());
            return new ReindexResult(notes.size(), notes.size(), vectorStore.getName());
        }
        catch (RuntimeException e) { throw operation("知识库重建失败", e); }
        finally { lock.writeLock().unlock(); }
    }

    public KnowledgeStatus status() { lock.readLock().lock(); try { return new KnowledgeStatus(repository.count(), indexedIds.size(), vectorStore.getName(), chatModel.getClass().getSimpleName()); } finally { lock.readLock().unlock(); } }

    private org.springframework.ai.vectorstore.SearchRequest buildSearch(String query, String owner, String tag, int topK) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filter = b.eq("owner_id", owner); if (tag != null) filter = b.and(filter, b.eq("tag", tag));
        return org.springframework.ai.vectorstore.SearchRequest.builder().query(query).topK(topK).similarityThreshold(0.0).filterExpression(filter.build()).build();
    }

    /** 在模型获得上下文之前回查事实库和授权，不只替换答案旁边的来源标题。 */
    private List<Document> freshDocuments(List<Document> candidates, String owner, String tag) {
        return candidates.stream().map(candidate -> repository.findByIdAndOwner(candidate.getId(), owner)
            .filter(note -> tag == null || tag.equals(note.tag()))
            .map(note -> toDocument(note).mutate().score(candidate.getScore()).build()).orElse(null))
            .filter(java.util.Objects::nonNull).toList();
    }

    private VectorStore factBackedView(String owner, String tag) {
        return new VectorStore() {
            @Override public List<Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
                return freshDocuments(vectorStore.similaritySearch(request), owner, tag);
            }
            @Override public void add(List<Document> documents) { throw new UnsupportedOperationException("事实检索视图不允许写入"); }
            @Override public void delete(List<String> ids) { throw new UnsupportedOperationException("事实检索视图不允许删除"); }
            @Override public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filter) {
                throw new UnsupportedOperationException("事实检索视图不允许删除");
            }
        };
    }

    private Document toDocument(Note n) { return Document.builder().id(n.id()).text(n.title() + "\n" + n.content()).metadata("owner_id", n.ownerId()).metadata("tag", n.tag()).metadata("title", n.title()).metadata("version", n.version()).build(); }
    private String owner(String value) { return identifier(value, "ownerId"); }
    private String identifier(String value, String field) { String v = text(value, field, 80); if (!v.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException(field + " 只能包含字母、数字、下划线或连字符"); return v; }
    private String text(String value, String field, int max) { if (!StringUtils.hasText(value)) throw new IllegalArgumentException(field + " 不能为空"); String v = value.trim(); if (v.length() > max) throw new IllegalArgumentException(field + " 长度不能超过 " + max + " 个字符"); return v; }
    private String optional(String value, String field, int max) { return StringUtils.hasText(value) ? text(value, field, max) : null; }
    private int topK(Integer value) { int k = value == null ? 4 : value; if (k < 1 || k > MAX_TOP_K) throw new IllegalArgumentException("topK 必须在 1 到 " + MAX_TOP_K + " 之间"); return k; }
    private String excerpt(String value) { return value.length() <= 240 ? value : value.substring(0, 240) + "..."; }
    private KnowledgeOperationException operation(String message, Throwable cause) { return cause == null ? new KnowledgeOperationException(message) : new KnowledgeOperationException(message, cause); }
}
