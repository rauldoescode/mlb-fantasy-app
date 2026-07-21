-- Matchup lineup eligibility, best-game performance locks, and finalize snapshots.
-- Supports Sleeper-style weekly H2H: freeze who was started per game day, lock one
-- scoring game per player per week, and persist per-player points when a matchup
-- becomes FINAL so past weeks are immutable.

CREATE TABLE lineup_eligibility (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id UUID NOT NULL REFERENCES league(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_number INT NOT NULL,
    player_id INT NOT NULL REFERENCES players(mlb_id),
    game_date DATE NOT NULL,
    was_started BOOLEAN NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE, -- set when first pitch freezes this row
    UNIQUE (league_id, user_id, week_number, player_id, game_date),
    CHECK (week_number >= 1)
);

CREATE INDEX idx_lineup_eligibility_week
    ON lineup_eligibility (league_id, user_id, week_number);

CREATE INDEX idx_lineup_eligibility_player_date
    ON lineup_eligibility (player_id, game_date);

CREATE TABLE performance_locks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id UUID NOT NULL REFERENCES league(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_number INT NOT NULL,
    player_id INT NOT NULL REFERENCES players(mlb_id),
    game_pk INT NOT NULL, -- daily_performances.game_pk for the locked box score
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    auto_locked BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (league_id, user_id, week_number, player_id),
    CHECK (week_number >= 1)
);

CREATE INDEX idx_performance_locks_week
    ON performance_locks (league_id, user_id, week_number);

CREATE TABLE matchup_player_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    matchup_id UUID NOT NULL REFERENCES matchups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    player_id INT NOT NULL REFERENCES players(mlb_id),
    slot_active BOOLEAN NOT NULL, -- starter at finalize time
    points NUMERIC(10, 2) NOT NULL DEFAULT 0,
    game_pk INT, -- scoring game if any (null if bench / no eligible game)
    category_points JSONB,
    UNIQUE (matchup_id, user_id, player_id)
);

CREATE INDEX idx_matchup_player_scores_matchup
    ON matchup_player_scores (matchup_id);

ALTER TABLE matchups
    ADD COLUMN finalized_at TIMESTAMP WITH TIME ZONE;
