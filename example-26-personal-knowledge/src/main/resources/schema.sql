DROP TABLE IF EXISTS personal_note;

CREATE TABLE personal_note (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(40) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(10000) NOT NULL,
    tag VARCHAR(40) NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX personal_note_owner_idx ON personal_note (owner_id);
CREATE INDEX personal_note_owner_tag_idx ON personal_note (owner_id, tag);
