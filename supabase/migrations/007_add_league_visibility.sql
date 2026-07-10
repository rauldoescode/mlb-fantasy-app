-- Adds public/private visibility, a self-serve join code, and a hard member cap.
-- Existing rows backfill to visibility = 'PRIVATE', join_code = NULL, max_members = 10.
-- A NULL join_code on a legacy row is treated as "not yet generated"; the
-- regenerate endpoint (or a one-off backfill) fixes it lazily.

ALTER TABLE league
  ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
  ADD COLUMN join_code VARCHAR(10) UNIQUE,
  ADD COLUMN max_members INT NOT NULL DEFAULT 10;

ALTER TABLE league
  ADD CONSTRAINT chk_league_max_members
    CHECK (max_members >= 2 AND max_members <= 12 AND max_members % 2 = 0);

CREATE INDEX idx_league_visibility ON league (visibility);
