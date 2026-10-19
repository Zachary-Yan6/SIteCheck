-- V4: push reminders for checks that are overdue or due soon (FR-27 to FR-29).

-- Expo push tokens of the phones users are logged in on (FR-27). One user can have several
-- phones; a token belongs to whoever logged in on that phone most recently.
CREATE TABLE device_tokens (
    token      VARCHAR(200) PRIMARY KEY,                 -- e.g. ExponentPushToken[xxxxxxxx]
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform   VARCHAR(10)  NOT NULL CHECK (platform IN ('android', 'ios')),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);

-- Which reminders have gone out: at most one per inspector, site and day. The daily job claims a
-- row before sending, so a restart or a second run on the same day never sends twice.
CREATE TABLE reminder_log (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    sent_on DATE   NOT NULL,                              -- Irish calendar day
    PRIMARY KEY (user_id, site_id, sent_on)
);
