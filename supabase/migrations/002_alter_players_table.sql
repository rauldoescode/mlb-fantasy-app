-- A lot of player information is null (or None), so we gotta update the players table by dropping
-- dead columns and add new columns with what we got

ALTER TABLE players
  DROP COLUMN age,
  DROP COLUMN birth_country;

ALTER TABLE players
  ADD COLUMN current_status VARCHAR(20),
  ADD COLUMN jersey_number VARCHAR(3);