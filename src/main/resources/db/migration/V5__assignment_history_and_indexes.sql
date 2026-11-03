-- V5: ended site assignments, and an index for the manager's failed-items list.

-- ---------------------------------------------------------------------------
-- When a manager unassigns an inspector, the row leaves site_inspectors and lands here.
-- An inspection started while the assignment was still running is accepted even if it is
-- uploaded afterwards: it may have been filled in offline days before the change, and
-- refusing it would lose the inspector's work for good.
-- ---------------------------------------------------------------------------
CREATE TABLE site_inspector_history (
    site_id       BIGINT      NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assigned_at   TIMESTAMPTZ NOT NULL,
    unassigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (unassigned_at >= assigned_at)
);

CREATE INDEX idx_site_inspector_history ON site_inspector_history (site_id, user_id);

-- Failed items across all sites (FR-30) filter on submitted_at alone, which the
-- (site_id, template_id, submitted_at) index can't serve.
CREATE INDEX idx_inspections_submitted ON inspections (submitted_at DESC);
