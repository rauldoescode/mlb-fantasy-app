# Fetches active MLB players and upserts them into the players table

import os
import psycopg2
from dotenv import load_dotenv
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

def sync_players() -> None:
    """
    Syncs active MLB players to the Supabase DB
    """
    print("Fetching active MLB players and syncing to Supabase DB...\n")
    # Note: The A's name is "Athletics" cuz they're homeless (no Oakland, Sacramento, or Las Vegas in their name)
    # Loop through all teams and get the roster for each team
    for team in mlb.get_teams():
        roster = mlb.get_team_roster(team.id)
        for player in roster:
            mlb_id = player.id
            full_name = player.full_name
            team_abbrev = team.abbreviation if team.abbreviation else None
            primary_pos = player.primary_position.abbreviation if player.primary_position else None
            current_status = player.status.description if hasattr(player, 'status') else None
            jersey_number = player.jersey_number if hasattr(player, 'jersey_number') else None
            is_active = True if current_status == 'Active' else False

            # Upsert the player into the DB
            upsert_query = """
                INSERT INTO players (mlb_id, full_name, team_abbrev, primary_pos, is_active, current_status, jersey_number)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (mlb_id) DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    team_abbrev = EXCLUDED.team_abbrev,
                    primary_pos = EXCLUDED.primary_pos,
                    is_active = EXCLUDED.is_active,
                    current_status = EXCLUDED.current_status,
                    jersey_number = EXCLUDED.jersey_number;
            """
            cursor.execute(upsert_query, (mlb_id, full_name, team_abbrev, primary_pos, is_active, current_status, jersey_number))
        print(f"Upserted the {team.name} roster into the DB")

# Sync the players
try:
    sync_players()
    conn.commit()
    print("\nFinished syncing active MLB players to Supabase DB")
except Exception as e:
    print("\nError syncing active MLB players to Supabase DB: {e}")
    conn.rollback()
finally:
    cursor.close()
    conn.close()
    print("\nClosed connection to Supabase DB")