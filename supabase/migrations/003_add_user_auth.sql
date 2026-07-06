-- Adds the columns the Java league-engine needs for authentication.
-- The data worker never writes to these; they are owned by the API.

ALTER TABLE users
  ADD COLUMN password_hash VARCHAR(100),
  ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'; -- 'USER' or 'ADMIN'
