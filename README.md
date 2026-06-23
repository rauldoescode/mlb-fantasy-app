# MLB Fantasy App

Private head-to-head points-based fantasy baseball app for friends and family.

## Repo Structure

```
MLB Fantasy App/
├── data-worker/              # Python — MLB stat ingestion
│   ├── sync_players.py       # Fetch and upsert active MLB players
│   ├── sync_daily_stats.py   # Fetch daily box scores
│   ├── requirements.txt
│   └── db/
│       ├── __init__.py
│       └── connection.py     # Postgres connection helpers
├── league-engine/            # Java Spring Boot — league logic & scoring
│   ├── pom.xml
│   └── src/main/java/com/mlbfantasy/
│       ├── LeagueEngineApplication.java
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

1. Copy `.env.example` to `.env` and set `DATABASE_URL`.
2. **Data worker:** `cd data-worker && pip install -r requirements.txt`
3. **League engine:** `cd league-engine && mvn spring-boot:run`
4. **Frontend:** `cd web && npm install && npm run dev`
