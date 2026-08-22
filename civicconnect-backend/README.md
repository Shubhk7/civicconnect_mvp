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

## AI classification (now wired in)

When `photoUrl` is included in a `POST /api/complaints` request, the
backend downloads that image and forwards it to the AI service's
`/classify` endpoint before doing anything else. Behavior:

- If the AI service is unreachable, times out, or the image can't be
  downloaded, submission proceeds normally using the citizen's manually
  selected `issueType` — classification is a cross-check, not a hard
  dependency. Nothing breaks if the AI service is down.
- If the AI's classification disagrees with what the citizen picked, the
  complaint is still saved using the citizen's selection, but a note is
  added to the status history and included in the response `message` —
  useful for officers to spot mismatches, without silently overriding the
  citizen.

This requires the `ai-service` container to be running (it's included in
`docker-compose.yml` already if you merged in the snippet from the
`civicconnect-ai` project).

## User accounts (registration/login) — passwords are hashed with BCrypt

`db/init.sql` includes a `users` table. Passwords are **never** stored in
plaintext or with a fast hash like SHA-256/MD5 — they go through
`BCryptPasswordEncoder` (see `SecurityConfig.java`), which:
- Applies a unique random salt per password automatically (two users with
  the same password get completely different stored hashes)
- Is deliberately slow to compute, making brute-force attacks on a leaked
  database impractical
- Never round-trips the plaintext password back out — `UserResponse` DTO
  only exposes `id`, `username`, `email`, `fullName`, `role`; the
  `passwordHash` field is structurally excluded from every API response

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"password123","fullName":"Test User"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"testuser","password":"password123"}'
```

**Important limitation to know about:** login currently just verifies the
password and returns the user's profile — it does **not** issue a session
token or JWT yet. That means there's no way yet for the backend to know
"who" is making a later request (like submitting a complaint) beyond this
one login call. This is fine for a hackathon demo where you control all
traffic, but before any real deployment, this needs proper token-based
auth (JWT is the standard choice) so subsequent requests can be
authenticated too — flag this if you want it built next.

## Next steps (not yet built)

- OpenCV before/after verification — `after_photo_url` field exists and the
  AI service has a `/verify` endpoint ready; the backend doesn't call it
  automatically yet on `/resolve`
- Auth (citizen/officer login) — currently open, fine for a demo
- SLA breach auto-escalation job
