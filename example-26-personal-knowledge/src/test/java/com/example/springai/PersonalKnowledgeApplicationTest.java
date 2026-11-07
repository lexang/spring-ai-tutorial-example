package com.example.springai;

import com.example.springai.model.KnowledgeModels.AskRequest;
import com.example.springai.model.KnowledgeModels.CreateNoteRequest;
import com.example.springai.model.KnowledgeModels.SearchRequest;
import com.example.springai.model.KnowledgeModels.UpdateNoteRequest;
import com.example.springai.service.PersonalKnowledgeService;
import com.example.springai.support.KnowledgeConflictException;
import com.example.springai.support.KnowledgeNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PersonalKnowledgeApplicationTest {

    @Autowired
    private PersonalKnowledgeService service;

    @Autowired
    private MockMvc mockMvc;
    @Autowired private com.example.springai.data.NoteRepository repository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void modelContextUsesCurrentAuthorizedFactsRatherThanStaleIndexText() {
        var alice = repository.findAll().stream().filter(note -> note.ownerId().equals("alice")).findFirst().orElseThrow();
        var bob = repository.findAll().stream().filter(note -> note.ownerId().equals("bob")).findFirst().orElseThrow();
        var vector = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        org.mockito.Mockito.when(vector.similaritySearch(org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class)))
            .thenReturn(java.util.List.of(
                org.springframework.ai.document.Document.builder().id(alice.id()).text("STALE_INDEX_TEXT").build(),
                org.springframework.ai.document.Document.builder().id(bob.id()).text("OTHER_OWNER_SECRET").build()));
        org.springframework.ai.chat.model.ChatModel model = prompt -> {
            assertThat(prompt.getContents()).contains(alice.content()).doesNotContain("STALE_INDEX_TEXT", "OTHER_OWNER_SECRET", bob.content());
            return new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(new org.springframework.ai.chat.model.Generation(
                new org.springframework.ai.chat.messages.AssistantMessage("根据当前授权笔记回答"))));
        };
        var guarded = new PersonalKnowledgeService(repository, vector, model, transactionManager);
        var answer = guarded.ask(new AskRequest("alice", "读取笔记", null, 10));
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.sources().get(0).id()).isEqualTo(alice.id());
    }

    @Test
    void startupRebuildsAllSeedNotes() {
        var status = this.service.status();
        assertThat(status.noteCount()).isGreaterThanOrEqualTo(4);
        assertThat(status.indexedCount()).isEqualTo(status.noteCount());
    }

    @Test
    void searchAppliesOwnerAndTagFilters() {
        var result = this.service.search(new SearchRequest("alice", "工具调用", "spring-ai", 5));
        assertThat(result.hits()).isNotEmpty().allMatch(hit -> hit.tag().equals("spring-ai"));
        assertThat(result.hits()).extracting(hit -> hit.id())
            .doesNotContain("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void aliceCannotRetrieveBobsPrivateBudget() {
        var result = this.service.search(new SearchRequest("alice", "季度培训预算两万元", null, 10));
        assertThat(result.hits()).extracting(hit -> hit.id())
            .doesNotContain("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void ragAnswerReturnsHydratedSources() {
        var answer = this.service.ask(new AskRequest("alice", "工具调用由谁管理？", "spring-ai", 3));
        assertThat(answer.answer()).contains("根据个人笔记");
        assertThat(answer.sources()).isNotEmpty()
            .allMatch(source -> source.tag().equals("spring-ai") && source.version() >= 1);
    }

    @Test
    void createUpdateConflictAndDeleteFormACompleteLifecycle() {
        var created = this.service.create(new CreateNoteRequest("alice", "发布清单", "先运行测试再发布", "work"));
        assertThat(this.service.search(new SearchRequest("alice", "运行测试", "work", 5)).hits())
            .extracting(hit -> hit.id()).contains(created.id());

        var updated = this.service.update(created.id(),
                new UpdateNoteRequest("alice", "发布清单", "先运行完整回归再发布", "release", created.version()));
        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThat(this.service.search(new SearchRequest("alice", "完整回归", "work", 5)).hits())
            .extracting(hit -> hit.id()).doesNotContain(created.id());
        assertThat(this.service.search(new SearchRequest("alice", "完整回归", "release", 5)).hits())
            .extracting(hit -> hit.id()).contains(created.id());

        assertThatThrownBy(() -> this.service.update(created.id(),
                new UpdateNoteRequest("alice", "旧版本", "不应写入", "release", created.version())))
            .isInstanceOf(KnowledgeConflictException.class);
        assertThat(this.service.delete(created.id(), "alice").deletedRows()).isEqualTo(1);
        assertThatThrownBy(() -> this.service.delete(created.id(), "alice"))
            .isInstanceOf(KnowledgeNotFoundException.class);
    }

    @Test
    void reindexRestoresSearchableDatabaseNotes() {
        var result = this.service.reindexAll();
        assertThat(result.indexedCount()).isEqualTo(result.noteCount());
        assertThat(this.service.search(new SearchRequest("alice", "上海博物馆", "travel", 3)).hits())
            .isNotEmpty();
    }

    @Test
    void httpLayerReturnsChinese400404And409() throws Exception {
        this.mockMvc.perform(post("/api/knowledge/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"alice\",\"query\":\" \",\"topK\":3}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("query 不能为空")));

        this.mockMvc.perform(delete("/api/knowledge/notes/11111111-1111-1111-1111-111111111111")
                .param("ownerId", "bob"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", containsString("不属于当前用户")));

        this.mockMvc.perform(put("/api/knowledge/notes/11111111-1111-1111-1111-111111111111")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ownerId":"alice","title":"冲突更新","content":"不会覆盖", "tag":"spring-ai","expectedVersion":99}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("版本冲突")));
    }

}
