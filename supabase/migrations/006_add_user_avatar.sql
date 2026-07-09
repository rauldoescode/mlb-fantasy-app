-- Adds an avatar image to users, stored as a data URL (small, client-resized JPEG/PNG).
ALTER TABLE users ADD COLUMN avatar_url TEXT;