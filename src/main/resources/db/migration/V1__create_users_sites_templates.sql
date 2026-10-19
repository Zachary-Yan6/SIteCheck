-- V1: core reference data - users, construction sites and inspection templates.
-- These tables are created by seed data / Swagger (no admin UI) and downloaded
-- to the phone's SQLite on login (FR-07). Inspections and photos come in a later migration.

-- ---------------------------------------------------------------------------
-- Users (FR-01): email + BCrypt password login, two roles only.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,              -- BCrypt hash, never the raw password
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('INSPECTOR', 'MANAGER')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Emails are unique regardless of case ("Zach@x.com" = "zach@x.com").
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));

-- ---------------------------------------------------------------------------
-- Sites (FR-05): name, address and coordinates.
-- ---------------------------------------------------------------------------
CREATE TABLE sites (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    latitude   NUMERIC(9, 6) CHECK (latitude  BETWEEN -90  AND 90),
    longitude  NUMERIC(9, 6) CHECK (longitude BETWEEN -180 AND 180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Which inspectors are assigned to which site (FR-05).
-- The server checks this on every request so an inspector only sees and
-- submits to their own sites (FR-02, acceptance scenario "403 on another's site").
CREATE TABLE site_inspectors (
    site_id     BIGINT      NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (site_id, user_id)
);

-- PK covers lookups by site; this covers "which sites is this inspector on?" (login download).
CREATE INDEX idx_site_inspectors_user ON site_inspectors (user_id);

-- ---------------------------------------------------------------------------
-- Inspection templates (FR-06): a checklist plus how often it must be done,
-- e.g. "Scaffolding inspection" every 7 days.
-- ---------------------------------------------------------------------------
CREATE TABLE inspection_templates (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    description    TEXT,
    frequency_days INTEGER      NOT NULL CHECK (frequency_days > 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Checklist items of a template, shown in "position" order.
CREATE TABLE template_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id BIGINT       NOT NULL REFERENCES inspection_templates (id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL CHECK (position > 0),
    title       VARCHAR(255) NOT NULL,
    guidance    TEXT,                                 -- optional hint for the inspector
    CONSTRAINT uk_template_items_position UNIQUE (template_id, position)
);

-- Which templates apply to which site. Together with frequency_days and the
-- last completed inspection, this drives the overdue / due-today sorting (FR-08)
-- and the daily reminder job (FR-28).
CREATE TABLE site_templates (
    site_id     BIGINT NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    template_id BIGINT NOT NULL REFERENCES inspection_templates (id) ON DELETE CASCADE,
    PRIMARY KEY (site_id, template_id)
);

CREATE INDEX idx_site_templates_template ON site_templates (template_id);
