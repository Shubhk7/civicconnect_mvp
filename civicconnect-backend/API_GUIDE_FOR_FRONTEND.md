# CivicConnect API — Frontend Integration Guide

This is what your WordPress site needs to call. Base URL will be
`https://api.kashnet.online` once the tunnel is running (for now, test
locally against `http://localhost:8080` if you're both on the same
network, or ask for a temporary tunnel URL).

All requests/responses are JSON, except photo upload which is a separate
concern (see note at the bottom).

---

## 1. Submit a report

**`POST /api/complaints`**

```json
{
  "issueType": "pothole",
  "description": "Large pothole near market entrance",
  "photoUrl": "https://example.com/photo.jpg",
  "lat": 28.591,
  "lng": 77.045
}
```

- `issueType` — one of: `pothole`, `garbage`, `streetlight`, `water_leak`, `ewaste`
- `photoUrl` — optional. If provided, the backend runs AI classification
  automatically. If you don't have image hosting set up yet, you can omit
  this field entirely and just send issueType manually.
- `lat` / `lng` — required. Use the browser's Geolocation API to get these:
  ```js
  navigator.geolocation.getCurrentPosition(pos => {
    const lat = pos.coords.latitude;
    const lng = pos.coords.longitude;
  });
  ```

**Response:**
```json
{
  "id": 6,
  "issueType": "pothole",
  "status": "ASSIGNED",
  "wardName": "Ward 134 - Dwarka Sector 12",
  "roadType": "municipal",
  "department": "Roads",
  "authorityName": "MCD",
  "slaDeadline": "2026-08-22T18:37:11.606222",
  "createdAt": "2026-08-20T18:37:11.606222",
  "isDuplicate": false,
  "message": "Routed to MCD (Roads)"
}
```

Show `authorityName`, `department`, and `slaDeadline` to the citizen right
after submission — this is the "here's who's responsible" moment that's
the whole point of the platform.

If `isDuplicate: true`, show a message like "A similar report already
exists nearby — we've added your report to it" instead of treating it as
an error.

---

## 2. Get one report's status + timeline

**`GET /api/complaints/{id}`**

```json
{
  "complaint": { ...same shape as above... },
  "timeline": [
    { "status": "ASSIGNED", "note": "Auto-routed to MCD", "createdAt": "..." },
    { "status": "IN_PROGRESS", "note": null, "createdAt": "..." }
  ]
}
```

Use `timeline` to render the step-by-step status view (Reported →
Acknowledged → Assigned → In Progress → Resolved).

---

## 3. List reports (for a map view or public dashboard)

**`GET /api/complaints`**
**`GET /api/complaints?wardId=1`**
**`GET /api/complaints?status=ASSIGNED`**

Returns an array of complaint objects (same shape as the submit response).

---

## 4. List available demo wards

**`GET /api/wards`**

```json
[
  { "id": 1, "name": "Ward 134 - Dwarka Sector 12", "city": "Delhi" },
  { "id": 2, "name": "Ward 22 - IGI Airport Corridor", "city": "Delhi" }
]
```

Useful if you want a ward filter dropdown on the officer/dashboard view.

---

## Status values you'll see

`REPORTED`, `ACKNOWLEDGED`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`,
`VERIFIED`, `CLOSED`, `REOPENED`, `UNASSIGNED` (jurisdiction couldn't be
determined — rare, but handle it gracefully in the UI, e.g. "under
review").

---

## Sample locations for testing (matches seeded demo data)

Use these lat/lng pairs while building, to see different authorities in
the response without needing real GPS:

| Location | lat | lng | Expected authority |
|---|---|---|---|
| Dwarka Sector 12, Delhi | 28.591 | 77.045 | MCD |
| Near IGI Airport (highway) | 28.556 | 77.100 | NHAI |
| Sector 29, Gurugram | 28.462 | 77.096 | MCG |
| Dwarka Expressway | 28.520 | 77.040 | PWD |

---

## CORS

The backend already allows requests from `https://kashnet.online` and
`https://www.kashnet.online`, so calling the API from your WordPress
Custom HTML block's JavaScript should work without extra configuration,
once the tunnel is live.

## Example fetch call for WordPress

```html
<script>
async function submitReport(issueType, description, lat, lng) {
  const res = await fetch('https://api.kashnet.online/api/complaints', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ issueType, description, lat, lng })
  });
  const data = await res.json();
  console.log(data);
  return data;
}
</script>
```

## Photo upload — not wired up yet

The API currently expects `photoUrl` to already be a hosted image link
(e.g. uploaded to WordPress media library first, then pass that URL). We
haven't built a direct file-upload endpoint yet — for the demo, either:
(a) skip photos and just submit issueType manually, or
(b) upload to WordPress media library first via its own upload mechanism,
then pass the resulting URL into `photoUrl`.

Flag this to me once you're ready to wire up photos and we'll figure out
the simplest path.
