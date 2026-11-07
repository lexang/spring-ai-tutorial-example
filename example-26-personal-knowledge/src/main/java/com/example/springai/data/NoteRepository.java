package com.example.springai.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.example.springai.model.KnowledgeModels.Note;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NoteRepository {

    private static final RowMapper<Note> NOTE_MAPPER = NoteRepository::mapNote;

    private final JdbcTemplate jdbcTemplate;

    public NoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Note> findAll() {
        return this.jdbcTemplate.query("""
                SELECT id, owner_id, title, content, tag, version, created_at, updated_at
                FROM personal_note ORDER BY created_at, id
                """, NOTE_MAPPER);
    }

    public Optional<Note> findByIdAndOwner(String id, String ownerId) {
        return this.jdbcTemplate.query("""
                SELECT id, owner_id, title, content, tag, version, created_at, updated_at
                FROM personal_note WHERE id = ? AND owner_id = ?
                """, NOTE_MAPPER, id, ownerId).stream().findFirst();
    }

    public long count() {
        Long value = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM personal_note", Long.class);
        return value == null ? 0 : value;
    }

    public Note insert(String id, String ownerId, String title, String content, String tag) {
        this.jdbcTemplate.update("""
                INSERT INTO personal_note (id, owner_id, title, content, tag, version)
                VALUES (?, ?, ?, ?, ?, 1)
                """, id, ownerId, title, content, tag);
        return findByIdAndOwner(id, ownerId).orElseThrow();
    }

    public int update(String id, String ownerId, String title, String content, String tag,
            int expectedVersion) {
        return this.jdbcTemplate.update("""
                UPDATE personal_note SET title = ?, content = ?, tag = ?, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND owner_id = ? AND version = ?
                """, title, content, tag, id, ownerId, expectedVersion);
    }

    public int delete(String id, String ownerId) {
        return this.jdbcTemplate.update("DELETE FROM personal_note WHERE id = ? AND owner_id = ?", id, ownerId);
    }

    private static Note mapNote(ResultSet rs, int rowNumber) throws SQLException {
        return new Note(rs.getString("id"), rs.getString("owner_id"), rs.getString("title"),
                rs.getString("content"), rs.getString("tag"), rs.getInt("version"),
                toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("updated_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

}
