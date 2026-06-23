CREATE TABLE players (
    mlb_id INT PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    age INT NOT NULL,
    team_abbrev VARCHAR(10),
    primary_pos VARCHAR(5),
    birth_country VARCHAR(70),
    is_active BOOLEAN DEFAULT true
);

CREATE TABLE daily_performances (
    id BIGSERIAL PRIMARY KEY,
    player_id INT REFERENCES players(mlb_id),
    game_date DATE NOT NULL,
    game_pk INT NOT NULL, -- Which game in doubleheader (if any)
    hits INT DEFAULT 0,
    home_runs INT DEFAULT 0,
    rbi INT DEFAULT 0,
    stolen_bases INT DEFAULT 0,
    strikeouts_batting INT DEFAULT 0,
    innings_pitched DECIMAL DEFAULT 0.0,
    earned_runs INT DEFAULT 0,
    pitching_wins INT DEFAULT 0,
    strikeouts_pitching INT DEFAULT 0,
    raw_stats JSONB,
    UNIQUE(player_id, game_date, game_pk) -- Ensures no box score is inserted twice
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE league (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_name VARCHAR(70) NOT NULL,
    season_year INT NOT NULL,
    commissioner_id UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(league_name, season_year) -- For the sake of this project, no names duplicates allowed
);

CREATE TABLE league_members (
    league_id UUID REFERENCES league(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    team_name VARCHAR(70) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (league_id, user_id)
);

CREATE TABLE roster_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id UUID REFERENCES league(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    player_id INT REFERENCES players(mlb_id) ON DELETE SET NULL, -- Set NULL if slot is empty
    slot_type VARCHAR(10) NOT NULL, -- Position, Bench, IL, etc.
    is_active BOOLEAN DEFAULT true, -- False if on the bench
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(league_id, user_id, player_id) -- Prevents the user from having the same player multiple times in the same league
);

CREATE TABLE matchups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id UUID REFERENCES league(id) ON DELETE CASCADE,
    week_number INT NOT NULL,
    user_one_id UUID REFERENCES users(id),
    user_one_score DECIMAL DEFAULT 0.0,
    user_two_id UUID REFERENCES users(id),
    user_two_score DECIMAL DEFAULT 0.0,
    winner_id UUID REFERENCES users(id) DEFAULT NULL, -- NULL until the end of the week
    status VARCHAR(20) DEFAULT 'SCHEDULED', -- 'SCHEDULED', 'IN_PROGRESS', 'FINAL'
    UNIQUE(league_id, week_number, user_one_id, user_two_id) -- Prevents the same two users from playing multiple times in the same week
);

CREATE TABLE scoring_rules (
    league_id UUID PRIMARY KEY REFERENCES league(id) ON DELETE CASCADE,
    point_values JSONB
);