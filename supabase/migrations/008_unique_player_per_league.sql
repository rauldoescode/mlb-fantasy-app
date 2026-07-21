-- A player may only be rostered by a single team within a given league.
-- The original UNIQUE(league_id, user_id, player_id) constraint only stopped one
-- user from rostering the same player twice; it still allowed two different teams
-- in the same league to both own the player. This partial unique index closes
-- that gap while still permitting many empty (player_id IS NULL) slots.
CREATE UNIQUE INDEX idx_roster_unique_player_per_league
  ON roster_slots (league_id, player_id)
  WHERE player_id IS NOT NULL;
