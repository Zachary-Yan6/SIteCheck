-- V3: submitted inspections, their per-item results and photo records.
--
-- Rows are only ever inserted, never updated by the app: a submitted inspection is
-- read-only (FR-15), which is what removes edit conflicts from the sync design.

-- ---------------------------------------------------------------------------
-- Inspections. The id is a UUID generated on the phone when the draft is created.
-- It doubles as the idempotency key (FR-20): a retried upload hits the same
-- primary key, so the server can never store the same inspection twice (NFR-03).
-- ---------------------------------------------------------------------------
CREATE TABLE inspections (
    id            UUID PRIMARY KEY,
    site_id       BIGINT      NOT NULL REFERENCES sites (id),
    -- RESTRICT (the default): a template that has been used can't be deleted, so history stays intact.
    template_id   BIGINT      NOT NULL REFERENCES inspection_templates (id),
    inspector_id  BIGINT      NOT NULL REFERENCES users (id),
    started_at    TIMESTAMPTZ NOT NULL,                -- phone clock, when the draft was created
    submitted_at  TIMESTAMPTZ NOT NULL,                -- phone clock, when it was signed (may be days before upload)
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now(),  -- server clock, when it reached us
    signature_png BYTEA       NOT NULL                 -- hand-drawn signature (FR-15), a small PNG
);

-- Site history (FR-09), overdue calculation (FR-08) and reports (FR-31) all look up by site + template + date.
CREATE INDEX idx_inspections_site_template ON inspections (site_id, template_id, submitted_at DESC);
CREATE INDEX idx_inspections_inspector ON inspections (inspector_id);

-- ---------------------------------------------------------------------------
-- One row per answered checklist item (FR-11).
-- ---------------------------------------------------------------------------
CREATE TABLE inspection_results (
    inspection_id    UUID        NOT NULL REFERENCES inspections (id) ON DELETE CASCADE,
    template_item_id BIGINT      NOT NULL REFERENCES template_items (id),
    result           VARCHAR(10) NOT NULL CHECK (result IN ('PASS', 'FAIL', 'NA')),
    note             TEXT,
    PRIMARY KEY (inspection_id, template_item_id)
);

-- ---------------------------------------------------------------------------
-- Photo evidence (FR-13). The row is created when the inspection is submitted,
-- with status PENDING; the file itself is uploaded to S3 afterwards (FR-21/22)
-- and the row flips to UPLOADED. This is how a restarted app knows which photos
-- still need uploading (acceptance scenario 3).
-- ---------------------------------------------------------------------------
CREATE TABLE photos (
    id               UUID PRIMARY KEY,                     -- generated on the phone, like inspections.id
    inspection_id    UUID         NOT NULL,
    template_item_id BIGINT       NOT NULL,
    s3_key           VARCHAR(255) NOT NULL UNIQUE,
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'UPLOADED')),
    taken_at         TIMESTAMPTZ  NOT NULL,
    latitude         NUMERIC(9, 6) CHECK (latitude  BETWEEN -90  AND 90),   -- only captured at photo time (NFR-06)
    longitude        NUMERIC(9, 6) CHECK (longitude BETWEEN -180 AND 180),
    uploaded_at      TIMESTAMPTZ,
    -- A photo must belong to an item that was actually answered in that inspection.
    FOREIGN KEY (inspection_id, template_item_id)
        REFERENCES inspection_results (inspection_id, template_item_id) ON DELETE CASCADE
);

CREATE INDEX idx_photos_inspection ON photos (inspection_id);
