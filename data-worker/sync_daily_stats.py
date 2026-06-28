# Fetches daily MLB stats and upserts them into the daily_performances table

import os
import psycopg2
import json
from dotenv import load_dotenv
from datetime import datetime, timedelta
import mlbstatsapi

# Load the DB from .env file
load_dotenv(dotenv_path='../.env')
DB_URL = os.getenv('DATABASE_URL')

if not DB_URL:
    raise ValueError("DATABASE_URL is not set")

# Connect to the DB
print("Connecting to Supabase DB...")
conn = psycopg2.connect(DB_URL)
cursor = conn.cursor()
print("Connected to Supabase DB")

# Initialize the MLB Stats API
mlb = mlbstatsapi.Mlb()

def get_stat_group(stats: dict, stat_group: str) -> dict:
    """
    Gets a specific stat group from the stats dictionary
    """
    return stats.get(stat_group, {})

def convert_ip_to_decimal(ip: str) -> float:
    """
    Converts innings pitched to a decimal
    """
    if ip == '0.0':
        return 0.0

    parts = ip.split('.')
    whole_innings = int(parts[0])
    outs = int(parts[1])
    fraction = round(outs / 3, 3) # 1 out = 0.333 innings; 2 outs = 0.667 innings
    return whole_innings + fraction

# Sync daily MLB box scores
def sync_box_scores(date: str) -> None:
    """
    Syncs daily MLB box scores for a given date
    """
    print(f"Syncing daily MLB box scores for {date}...\n")
    schedule = mlb.get_schedule(date)
    if schedule is None:
        print(f"No games scheduled for {date}")
        return
    
    # Get the games for the day and loop through them
    games = schedule.dates[0].games
    for game in games:
        game_pk = game.game_pk
        game_status = game.status.detailed_state
        if game_status != 'Final':
            print(f"Game {game_pk} is not final, skipping...")
            continue

        # Get the box score for the game
        print(f"Getting box score for game {game_pk}...")
        box_score = mlb.get_game_box_score(game_pk)
        if box_score is None:
            print(f"No box score found for game {game_pk}, skipping...")
            continue
        
        # Loop through the home and away teams in the box score
        print(f"Retrieving stats for home and away teams for game {game_pk}...")
        for team_type in ['home', 'away']:
            team_roster = getattr(box_score.teams, team_type).players

            # Loop through the players in the team roster and get their stats
            for player_key, player in team_roster.items():
                mlb_id = player.person.id

                # Get batting stats
                b_stats = get_stat_group(player.stats, 'batting')
                plate_appearances = b_stats.get('plateAppearances', 0)

                # Get pitching stats
                p_stats = get_stat_group(player.stats, 'pitching')
                games_pitched = p_stats.get('gamesPitched', 0)

                # If the player didn't bat or pitch, skip them
                if plate_appearances == 0 and games_pitched == 0:
                    print(f"Player {player.person.full_name} DNP in game {game.game_pk}")
                    continue
                print(f"Player {player.person.full_name} played in game {game.game_pk}")

                # Parse stats
                hits = b_stats.get('hits', 0)
                home_runs = b_stats.get('homeRuns', 0)
                rbi = b_stats.get('rbi', 0)
                stolen_bases = b_stats.get('stolenBases', 0)
                strikeouts_batting = b_stats.get('strikeOuts', 0)

                innings_pitched = convert_ip_to_decimal(p_stats.get('inningsPitched', '0.0'))
                earned_runs = p_stats.get('earnedRuns', 0)
                pitching_wins = p_stats.get('wins', 0)
                strikeouts_pitching = p_stats.get('strikeOuts', 0)

                raw_stats = json.dumps({
                    'batting': b_stats,
                    'pitching': p_stats
                }, indent=4)

                # To ensure that we don't lose the stats of a player who is not in the players table currently,
                # such as a player who got called up or demoted immediately after a spot start right before the daily sync,
                # we need to add bare bones player records to the players table so that they exist in the DB.
                # We'll mark them as inactive and set their status description to 'Minor Leagues' in case they get
                # demoted to the minors immediately after a spot start right before the daily sync.
                ghost_player_query = """
                    INSERT INTO players (mlb_id, full_name, is_active, current_status)
                    VALUES (%s, %s, %s, %s)
                    ON CONFLICT (mlb_id) DO NOTHING
                """
                cursor.execute(ghost_player_query, (mlb_id, player.person.full_name, False, 'Minor Leagues'))

                # Upsert the stats into the DB
                upsert_query = """
                    INSERT INTO daily_performances (player_id, game_date, game_pk, hits, home_runs, rbi, stolen_bases, strikeouts_batting, innings_pitched, earned_runs, pitching_wins, strikeouts_pitching, raw_stats)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    ON CONFLICT (player_id, game_date, game_pk) DO UPDATE SET
                        hits = EXCLUDED.hits,
                        home_runs = EXCLUDED.home_runs,
                        rbi = EXCLUDED.rbi,
                        stolen_bases = EXCLUDED.stolen_bases,
                        strikeouts_batting = EXCLUDED.strikeouts_batting,
                        innings_pitched = EXCLUDED.innings_pitched,
                        earned_runs = EXCLUDED.earned_runs,
                        pitching_wins = EXCLUDED.pitching_wins,
                        strikeouts_pitching = EXCLUDED.strikeouts_pitching,
                        raw_stats = EXCLUDED.raw_stats;
                """
                cursor.execute(upsert_query, (mlb_id, date, game_pk, hits, home_runs, rbi, stolen_bases, strikeouts_batting, innings_pitched, earned_runs, pitching_wins, strikeouts_pitching, raw_stats))

        print(f"Finished syncing stats for game {game_pk}")
    print(f"Finished syncing stats for all games for {date}")

# Get the previous day's date
print("Getting previous day's date...")
previous_day = (datetime.now() - timedelta(days=1)).strftime('%Y-%m-%d')

# Sync the previous day's stats
try:
    sync_box_scores(previous_day)
    conn.commit()
    print(f"\nFinished syncing stats for {previous_day}")
except Exception as e:
    print(f"\nError syncing stats for {previous_day}: {e}")
    conn.rollback()
finally:
    cursor.close()
    conn.close()
    print("\nClosed connection to Supabase DB")