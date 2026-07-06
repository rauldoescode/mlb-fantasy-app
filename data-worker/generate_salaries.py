# Computes a "fantasy salary" for every player and writes it to players.salary.
#
# There is no free/reliable source for real MLB payroll data, so — like the salary
# caps on DraftKings/FanDuel/Yahoo — we derive a synthetic price from projected
# fantasy value instead of real dollars. For each player we pull their most recent
# completed MLB season and run the counting stats through the same default scoring
# formula as the league-engine's ScoringService (HR x4, RBI x1, SB x2, pitching
# W x5, batting K x-1), then linearly scale that projected point total into a
# salary between MIN_SALARY and MAX_SALARY. Players with no prior-season stats
# (rookies, players who didn't play last year, etc.) land at the salary floor.
#
# Run this once per offseason (or whenever you want to refresh pricing) after
# sync_players.py has populated the players table:
#   python3 generate_salaries.py [season]
# `season` defaults to last calendar year.

import os
import sys
import time
from datetime import datetime

import psycopg2
import requests
from dotenv import load_dotenv

load_dotenv(dotenv_path='../.env')
DB_URL = os.getenv('DATABASE_URL')

if not DB_URL:
    raise ValueError("DATABASE_URL is not set")

STATS_URL_TEMPLATE = "https://statsapi.mlb.com/api/v1/people/{mlb_id}/stats"

# Mirrors ScoringService.DEFAULT_POINT_VALUES in the Java league-engine so a
# player's fantasy salary lines up with the league's default scoring formula.
POINT_VALUES = {
    'home_runs': 4.0,
    'rbi': 1.0,
    'stolen_bases': 2.0,
    'pitching_wins': 5.0,
    'strikeouts_batting': -1.0,
}

MIN_SALARY = 1_000_000
MAX_SALARY = 45_000_000
SALARY_ROUNDING = 100_000  # Round to the nearest $100k for readability.

REQUEST_TIMEOUT_SECONDS = 10
REQUEST_RETRIES = 3
REQUEST_DELAY_SECONDS = 0.05


def fetch_prior_season_totals(mlb_id: int, season: str) -> dict:
    """
    Fetches a player's hitting + pitching season totals for the given year.
    Returns a dict with the raw counting stats the scoring formula needs;
    all zeros if the player has no stats for that season (rookie, etc.).

    Note: we hit the raw MLB Stats API directly (rather than the typed
    mlbstatsapi wrapper) because that wrapper's hitting-stat model silently
    drops the strikeOuts field, which we need for the formula.
    """
    totals = {
        'home_runs': 0,
        'rbi': 0,
        'stolen_bases': 0,
        'pitching_wins': 0,
        'strikeouts_batting': 0,
    }

    params = {'stats': 'season', 'group': 'hitting,pitching', 'season': season}
    last_error = None
    for attempt in range(REQUEST_RETRIES):
        try:
            response = requests.get(
                STATS_URL_TEMPLATE.format(mlb_id=mlb_id),
                params=params,
                timeout=REQUEST_TIMEOUT_SECONDS,
            )
            response.raise_for_status()
            data = response.json()
            break
        except requests.RequestException as exc:
            last_error = exc
            time.sleep(0.5 * (attempt + 1))
    else:
        print(f"  Warning: failed to fetch stats for player {mlb_id}: {last_error}")
        return totals

    for stat_group in data.get('stats', []):
        splits = stat_group.get('splits', [])
        if not splits:
            continue
        stat = splits[0].get('stat', {})
        group_name = stat_group.get('group', {}).get('displayName')
        if group_name == 'hitting':
            totals['home_runs'] += stat.get('homeRuns', 0) or 0
            totals['rbi'] += stat.get('rbi', 0) or 0
            totals['stolen_bases'] += stat.get('stolenBases', 0) or 0
            totals['strikeouts_batting'] += stat.get('strikeOuts', 0) or 0
        elif group_name == 'pitching':
            totals['pitching_wins'] += stat.get('wins', 0) or 0

    return totals


def compute_projected_points(totals: dict) -> float:
    """Applies the default scoring formula to a player's raw season totals."""
    return sum(totals.get(stat, 0) * weight for stat, weight in POINT_VALUES.items())


def scale_to_salary(projected_points: float, max_projected_points: float) -> int:
    """
    Linearly scales a non-negative projected point total into a salary between
    MIN_SALARY and MAX_SALARY, rounded to the nearest SALARY_ROUNDING.
    """
    clipped = max(projected_points, 0.0)
    if max_projected_points <= 0:
        normalized = 0.0
    else:
        normalized = min(clipped / max_projected_points, 1.0)

    raw_salary = MIN_SALARY + (MAX_SALARY - MIN_SALARY) * normalized
    return round(raw_salary / SALARY_ROUNDING) * SALARY_ROUNDING


def generate_salaries(season: str) -> None:
    conn = psycopg2.connect(DB_URL)
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT mlb_id, full_name FROM players")
        players = cursor.fetchall()
        print(f"Projecting {season} fantasy value for {len(players)} players...\n")

        projected_points_by_id = {}
        for i, (mlb_id, full_name) in enumerate(players, start=1):
            totals = fetch_prior_season_totals(mlb_id, season)
            projected_points_by_id[mlb_id] = compute_projected_points(totals)
            if i % 100 == 0:
                print(f"  Processed {i}/{len(players)} players...")
            time.sleep(REQUEST_DELAY_SECONDS)

        max_projected_points = max(projected_points_by_id.values(), default=0.0)
        print(f"\nTop projected point total: {max_projected_points:.1f}")

        update_query = "UPDATE players SET salary = %s WHERE mlb_id = %s"
        for mlb_id, points in projected_points_by_id.items():
            salary = scale_to_salary(points, max_projected_points)
            cursor.execute(update_query, (salary, mlb_id))

        conn.commit()
        print(f"\nFinished assigning fantasy salaries for the {season} projection.")
    except Exception as e:
        print(f"\nError generating salaries: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()
        conn.close()
        print("\nClosed connection to Supabase DB")


if __name__ == '__main__':
    season_arg = sys.argv[1] if len(sys.argv) > 1 else str(datetime.now().year - 1)
    generate_salaries(season_arg)
