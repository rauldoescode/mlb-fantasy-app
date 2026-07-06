# MLB Fantasy App

Private head-to-head points-based fantasy baseball app for friends and family.

## Repo Structure

```
MLB Fantasy App/
├── data-worker/              # Python — MLB stat ingestion
│   ├── sync_players.py       # Fetch and upsert active MLB players
│   ├── sync_daily_stats.py   # Fetch daily box scores
│   ├── generate_salaries.py  # Derive fantasy salaries from projected value
│   ├── requirements.txt
│   └── db/
│       ├── __init__.py
│       └── connection.py     # Postgres connection helpers
├── league-engine/            # Java Spring Boot — league logic & scoring
│   ├── pom.xml
│   └── src/main/java/com/mlbfantasy/
│       ├── LeagueEngineApplication.java
│       ├── config/           # Security, CORS, DATABASE_URL normalization
│       ├── controller/       # REST endpoints
│       ├── service/          # Scoring, matchups, roster logic
│       ├── model/            # JPA entities
│       └── repository/       # Data access
├── web/                      # Next.js PWA frontend
│   ├── app/
│   │   ├── page.tsx          # Personal dashboard (home)
│   │   ├── matchup/
│   │   ├── league/
│   │   ├── players/
│   │   ├── roster/
│   │   └── login/
│   └── package.json
├── supabase/
│   ├── config.toml
│   └── migrations/           # Postgres schema
├── .github/workflows/
│   └── sync-stats.yml        # Nightly stat sync cron
└── .env.example
```

## Services

| Service | Tech | Role |
|---------|------|------|
| Data worker | Python | Nightly MLB API fetch → Postgres |
| League engine | Java Spring Boot | Auth, rosters, H2H scoring API |
| Frontend | Next.js PWA | Dashboard and league UI |
| Database | PostgreSQL (Supabase) | Shared source of truth |

Services communicate through PostgreSQL only — no direct Python-to-Java calls.

## Getting Started

1. Copy `.env.example` to `.env` and set `DATABASE_URL` and `JWT_SECRET`.
2. **Database:** apply the SQL files in `supabase/migrations/` in order (`001` → `005`).
3. **Data worker:** `cd data-worker && pip install -r requirements.txt`, then run
   `python sync_players.py` followed by `python generate_salaries.py` to seed the
   `players` table and its fantasy salaries.
4. **League engine:** `cd league-engine && mvn spring-boot:run` (needs JDK 21+). Reads
   `DATABASE_URL` and `JWT_SECRET` from the environment — unlike the Python scripts,
   Spring Boot doesn't auto-load `.env`, so export both first, e.g.:
   ```bash
   eval "$(python3 -c "from dotenv import dotenv_values; [print(f\"export {k}='{v}'\") for k, v in dotenv_values('.env').items()]")"
   cd league-engine && mvn spring-boot:run
   ```
   `DATABASE_URL` can stay in the same psycopg2 form the data worker uses
   (`postgresql://user:pass@host:port/db`) — a `DatabaseUrlEnvironmentPostProcessor`
   auto-converts it to the JDBC form Spring needs at startup.
5. **Frontend:** `cd web && npm install && npm run dev`

### League engine API (Phase 2)

JWT-secured Spring Boot service. Public endpoints: `POST /api/auth/register`,
`POST /api/auth/login`. All others require an `Authorization: Bearer <token>` header.

| Area | Endpoints |
|------|-----------|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| Leagues | `POST /api/leagues`, `GET /api/leagues`, `GET /api/leagues/{id}`, `POST /api/leagues/{id}/members`, `PUT /api/leagues/{id}/scoring-rules`, `GET /api/leagues/{id}/standings` |
| Roster | `GET/POST /api/leagues/{id}/roster`, `PATCH /api/roster/{slotId}`, `DELETE /api/roster/{slotId}` |
| Players | `GET /api/players`, `GET /api/players/{mlbId}` |
| Matchups | `GET /api/leagues/{id}/matchups`, `POST /api/leagues/{id}/matchups/generate`, `GET /api/matchups/{id}`, `POST /api/matchups/{id}/finalize` |

Rosters enforce a per-league salary cap, and lineup changes lock once a player's
game for the day has started (driven by `player_scheduled_games`). Weekly H2H scoring
runs Monday–Sunday using each league's configurable `scoring_rules`.

### Player salaries

There's no free/reliable source of real MLB payroll data, so `generate_salaries.py`
derives a synthetic "fantasy salary" per player instead — the same approach
DraftKings/FanDuel/Yahoo use for their salary-cap games. For each player it pulls
their most recent completed season from the MLB Stats API, runs the counting stats
through the league-engine's default scoring formula (HR ×4, RBI ×1, SB ×2, pitching
W ×5, batting K ×-1) to get a projected point total, then linearly scales that into a
salary between `MIN_SALARY` ($1M) and `MAX_SALARY` ($45M). Players with no prior-season
stats (rookies, etc.) land at the floor. Re-run it each offseason to refresh pricing:

```bash
python generate_salaries.py        # projects off last calendar year
python generate_salaries.py 2025   # or pass an explicit season
```
