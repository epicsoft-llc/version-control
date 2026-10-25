-- A rollout planned for one environment: the version, the day and why. It ends by
-- itself once that version is recorded; a day that passes before is a task.
ALTER TABLE component_environment ADD COLUMN planned_version VARCHAR(200);
ALTER TABLE component_environment ADD COLUMN planned_for DATE;
ALTER TABLE component_environment ADD COLUMN planned_note TEXT;

-- Where the release notes of one version are; {version} stands for the tag.
ALTER TABLE component ADD COLUMN release_notes_url VARCHAR(500);

-- What was changed on a component, field by field, with decisions and rollout
-- plans among them. Versions are not repeated here: their history is the
-- version change, with backup and rollback path.
CREATE TABLE component_change
(
    id           UUID         NOT NULL,
    component_id UUID         NOT NULL,
    changed_at   TIMESTAMP(6) NOT NULL,
    field        VARCHAR(50)  NOT NULL,
    old_value    TEXT,
    new_value    TEXT,
    note         TEXT,
    created      TIMESTAMP(6) NOT NULL,
    updated      TIMESTAMP(6),
    CONSTRAINT PK_component_change PRIMARY KEY (id),
    CONSTRAINT FK_component_change_component FOREIGN KEY (component_id) REFERENCES component (id) ON DELETE CASCADE
);
CREATE INDEX IX_component_change_component ON component_change (component_id, changed_at DESC);
