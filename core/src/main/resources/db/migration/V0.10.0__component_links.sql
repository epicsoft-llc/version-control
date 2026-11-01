-- Kept by hand or found by the link search; the links derived from a component's own fields are never stored.
CREATE TABLE component_link
(
    id           UUID          NOT NULL,
    component_id UUID          NOT NULL,
    url          VARCHAR(2000) NOT NULL,
    kind         VARCHAR(32)   NOT NULL,
    origin       VARCHAR(32)   NOT NULL,
    sort_order   INTEGER       NOT NULL,
    created      TIMESTAMP(6)  NOT NULL,
    updated      TIMESTAMP(6),
    CONSTRAINT PK_component_link PRIMARY KEY (id),
    CONSTRAINT FK_component_link_component FOREIGN KEY (component_id) REFERENCES component (id) ON DELETE CASCADE
);
-- Per origin: two sites may find the same address, and each replaces only its own rows.
CREATE UNIQUE INDEX UQ_component_link_component_origin_url ON component_link (component_id, origin, url);

CREATE TABLE link_search
(
    id             UUID         NOT NULL,
    component_id   UUID         NOT NULL,
    checked_at     TIMESTAMP(6) NOT NULL,
    next_check_due TIMESTAMP(6),
    check_state    VARCHAR(32)  NOT NULL,
    error_message  TEXT,
    created        TIMESTAMP(6) NOT NULL,
    updated        TIMESTAMP(6),
    CONSTRAINT PK_link_search PRIMARY KEY (id),
    CONSTRAINT FK_link_search_component FOREIGN KEY (component_id) REFERENCES component (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX UQ_link_search_component ON link_search (component_id);
CREATE INDEX IX_link_search_next_check_due ON link_search (next_check_due);
