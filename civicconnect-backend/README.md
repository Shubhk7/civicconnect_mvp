# CivicConnect Backend (MVP)

Spring Boot + PostgreSQL/PostGIS backend implementing the jurisdiction
engine: given a report's location and issue type, it resolves the ward,
road ownership, and correct authority automatically.

## Run it (Docker — recommended, now that Docker's working on your machine)

```bash
docker compose up --build
```

This starts:
- `civicconnect-db` — Postgres + PostGIS, auto-seeded with sample wards,
  authority mappings, and a few demo complaints (see `db/init.sql` and
  `db/seed.sql`)
- `civicconnect-api` — the Spring Boot app, on **http://localhost:8080**

First run will take a few minutes (Maven downloads dependencies inside the
build stage). Subsequent runs are fast.

To reset the database completely (re-run seed data from scratch):
```bash
docker compose down -v
docker compose up --build
```

## Run it without Docker (fallback)

1. Install PostgreSQL + PostGIS locally, create a database `civicconnect`
2. Run `db/init.sql` then `db/seed.sql` against it (`psql -d civicconnect -f db/init.sql`)
3. Set env vars or edit `application.properties` with your local DB credentials
4. `mvn spring-boot:run`

## Quick test

```bash
# Submit a report in Dwarka (should route to MCD)
curl -X POST http://localhost:8080/api/complaints \
  -H "Content-Type: application/json" \
  -d '{"issueType":"pothole","description":"Big pothole","lat":28.591,"lng":77.045}'

# Submit a report near the airport corridor (should route to NHAI)
curl -X POST http://localhost:8080/api/complaints \
  -H "Content-Type: application/json" \
  -d '{"issueType":"pothole","description":"Highway pothole","lat":28.556,"lng":77.098}'

# List all complaints (for the officer dashboard)
curl http://localhost:8080/api/complaints

# Get one complaint + its status timeline
curl http://localhost:8080/api/complaints/1

# Officer marks it resolved
curl -X POST http://localhost:8080/api/complaints/1/resolve \
  -H "Content-Type: application/json" \
  -d '{"afterPhotoUrl":"https://example.com/after.jpg"}'
```

If the first `curl` above returns `"authorityName":"MCD"` and the second
returns `"authorityName":"NHAI"`, the jurisdiction engine is working
correctly — same issue type, different location, different authority.

## API reference (for the frontend)

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/complaints` | Submit a new report — runs duplicate check + jurisdiction routing |
| GET | `/api/complaints/:id` | Get one complaint + its status timeline |
| GET | `/api/complaints?wardId=&status=` | List complaints (officer dashboard, map view) |
| POST | `/api/complaints/:id/resolve` | Officer marks fixed, uploads after-photo |
| PATCH | `/api/complaints/:id/status` | Update status (e.g. AI verification result) |
| GET | `/api/wards` | List available demo wards |

### POST /api/complaints — request body
```json
{
  "issueType": "pothole",
  "description": "Large pothole near market",
  "photoUrl": "https://...",
  "lat": 28.591,
  "lng": 77.045
}
```
`issueType` must be one of: `pothole`, `garbage`, `streetlight`, `water_leak`, `ewaste`

### Response
```json
{
  "id": 6,
  "issueType": "pothole",
  "status": "ASSIGNED",
  "wardName": "Ward 134 - Dwarka Sector 12",
  "roadType": "municipal",
  "department": "Roads",
  "authorityName": "MCD",
  "slaDeadline": "2026-08-22T10:00:00",
  "isDuplicate": false,
  "message": "Routed to MCD (Roads)"
}
```

## CORS

By default allows `localhost:5173` and `localhost:3000` (typical Vite/React
dev ports). Update `app.cors.allowed-origins` in `application.properties`
or set `APP_CORS_ORIGINS` env var to your Cloudflare Pages URL once
deployed, e.g.:
```
APP_CORS_ORIGINS=https://civicconnect.pages.dev
```

## How the jurisdiction engine works (db/init.sql + JurisdictionService.java)

1. `wards` table stores a rough polygon boundary per ward
2. `authority_mapping` table maps (ward, road_type, issue_type) → (department, authority, SLA)
3. On submit: `ST_Contains` finds which ward polygon contains the report's GPS point
4. Then it checks road types in priority order (national highway → state PWD →
   municipal) so a highway cutting through a municipal ward still routes
   correctly to NHAI/PWD instead of the municipal body
5. If no ward or no mapping matches, the complaint is saved with status
   `UNASSIGNED` for manual review — it never silently drops or guesses

## Next steps (not yet built)

- AI classification service (Python) — currently the frontend/citizen
  selects issue type manually
- OpenCV before/after verification — `after_photo_url` field exists,
  verification logic doesn't yet
- Auth (citizen/officer login) — currently open, fine for a demo
- SLA breach auto-escalation job
