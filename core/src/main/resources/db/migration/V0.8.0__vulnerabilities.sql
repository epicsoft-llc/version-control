-- Known vulnerabilities from a Dependency-Check database; a component without CPE vendor and product is not looked up.
ALTER TABLE component ADD COLUMN cpe_vendor VARCHAR(100);
ALTER TABLE component ADD COLUMN cpe_product VARCHAR(100);

-- One lookup per component, with its own cycle and error state, like the end of life.
CREATE TABLE vulnerability_check
(
    id             UUID         NOT NULL,
    component_id   UUID         NOT NULL,
    checked_at     TIMESTAMP(6) NOT NULL,
    next_check_due TIMESTAMP(6),
    check_state    VARCHAR(32)  NOT NULL,
    error_message  TEXT,
    data_as_of     TIMESTAMP(6),
    created        TIMESTAMP(6) NOT NULL,
    updated        TIMESTAMP(6),
    CONSTRAINT PK_vulnerability_check PRIMARY KEY (id),
    CONSTRAINT FK_vulnerability_check_component FOREIGN KEY (component_id) REFERENCES component (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX UQ_vulnerability_check_component ON vulnerability_check (component_id);
CREATE INDEX IX_vulnerability_check_next_check_due ON vulnerability_check (next_check_due);

-- A finding keeps the version it was found for, not whatever the environment runs today.
CREATE TABLE vulnerability_finding
(
    id                       UUID         NOT NULL,
    component_environment_id UUID         NOT NULL,
    version                  VARCHAR(255) NOT NULL,
    cve                      VARCHAR(32)  NOT NULL,
    score                    NUMERIC(3, 1),
    severity                 VARCHAR(16),
    known_exploited          BOOLEAN      NOT NULL,
    summary                  VARCHAR(500),
    created                  TIMESTAMP(6) NOT NULL,
    updated                  TIMESTAMP(6),
    CONSTRAINT PK_vulnerability_finding PRIMARY KEY (id),
    CONSTRAINT FK_vulnerability_finding_component_environment FOREIGN KEY (component_environment_id)
        REFERENCES component_environment (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX UQ_vulnerability_finding_component_environment_cve ON vulnerability_finding (component_environment_id, cve);
