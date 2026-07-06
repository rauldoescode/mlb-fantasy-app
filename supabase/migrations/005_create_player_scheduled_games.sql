-- Per-player game schedule used for lineup locks.
-- The data worker populates this from the MLB schedule endpoint; the engine reads
-- it to decide whether a roster slot is locked (its game has already started).

CREATE TABLE player_scheduled_games (
    id BIGSERIAL PRIMARY KEY,
    player_id INT REFERENCES players(mlb_id) ON DELETE CASCADE,
    game_pk INT NOT NULL,
    game_date DATE NOT NULL,
    game_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(player_id, game_pk)
);

CREATE INDEX idx_player_scheduled_games_player_date
    ON player_scheduled_games (player_id, game_date);
