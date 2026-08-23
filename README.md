# CivicConnect

Civic reporting for Delhi NCR: a citizen files one report, the backend
routes it to the responsible authority, and the public ledger tracks it
until it is actually closed.

This repository is a **monorepo**. The Android app is a WebView client of
the existing website — it does not replace the frontend, backend, or AI
service.

```
Android app
    → WebView
        → CivicConnect website  (civicconnect-frontend, hosted at https://kashnet.online)
            → REST API          (civicconnect-backend, https://api.kashnet.online)
                → PostgreSQL / PostGIS
                → AI service    (civicconnect-ai — classify / verify / blur)
```

## Layout

| Path | What it is |
|---|---|
| `civicconnect-frontend/` | Static HTML/CSS/JS (the product UI) |
| `civicconnect-backend/` | Spring Boot + PostGIS jurisdiction engine and JWT API |
| `civicconnect-ai/` | FastAPI: classify, verify, blur faces/plates |
| `android/` | Native Java WebView wrapper around the website |
| `docs/` | Extra notes (Android, WebView limits) |

There is no `PROJECT_RULES.md`. Conventions live in comments next to the
code (JWT in `localStorage` as `civic_token`, never a second auth system).

## Website and API (how the Android app talks to the stack)

The frontend does **not** call the API with cookies. Each page embeds the
same helper:

- `API_BASE = https://api.kashnet.online`
- JWT in `localStorage` key `civic_token`; profile in `civic_user`
- `Authorization: Bearer …` on authenticated `fetch` calls
- Photos: `POST /api/uploads/photo` (`FormData` field `file`), then the
  returned URL is sent on `POST /api/complaints`
- Location: `navigator.geolocation.getCurrentPosition` only when the
  citizen taps **Use my location** (not on launch)
- File input: `<input type="file" accept="image/*">` (no `capture` attr)

Because auth is `localStorage` + Bearer tokens, a normal WebView with
JavaScript and DOM storage is enough. The Android app must not invent a
native login.

Public site: **https://kashnet.online**  
API: **https://api.kashnet.online**

CORS on the API already allows `https://kashnet.online` (and www). The
WebView origin is that site, so CORS behaves like mobile Chrome.

## Android application

Location: [`android/`](android/). Open that folder in Android Studio
(not the repo root).

- Language: **Java 17**
- UI: splash + WebView + progress + retry. No native dashboards.
- Default URL: `https://kashnet.online` via `BuildConfig.CIVICCONNECT_BASE_URL`

### Configure the URL

Do **not** ship a `localhost` production URL.

Priority (last wins among gradle properties / local file as implemented
in `android/app/build.gradle`):

1. Default in `android/gradle.properties`: `civicconnect.baseUrl=https://kashnet.online`
2. `android/local.properties` (not committed): `civicconnect.baseUrl=…`
3. CLI: `./gradlew assembleDebug -Pcivicconnect.baseUrl=https://kashnet.online`

### Local frontend on an emulator

The website is already hosted, so the default URL works on emulator and
device with internet.

If you point the WebView at a **local** copy of `civicconnect-frontend`:

| Where the site runs | URL from the app |
|---|---|
| Emulator → host machine | `http://10.0.2.2:<port>` |
| Physical device → your PC | `http://<LAN-IP>:<port>` (same Wi‑Fi; allow HTTP in debug only) |
| `localhost` inside the app | the **phone**, not your laptop — it will fail |

Debug builds allow cleartext only for `10.0.2.2`, `10.0.3.2`, `localhost`,
and `127.0.0.1`. Release builds are HTTPS-only.

The JS still calls `https://api.kashnet.online`. Serving the HTML locally
does not require a local API unless you change `API_BASE` in the frontend.

### Build and run

Android Studio Ladybug / Koala or newer, Android SDK 35, JDK 17.

```bash
cd android
./gradlew assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on a device or AVD
(API 26+). Grant **location** when the site asks; grant **camera** when
you take a photo from a file input.

### Permissions

Requested **when the page needs them**, not on launch:

- Location — WebView geolocation prompt (report form)
- Camera — only if the file chooser uses capture; gallery pick does not
  need the Camera permission

### WebView geolocation limits

- The site must be HTTPS (or localhost) or the Geolocation API is blocked
- Accuracy follows the OS location providers; it is not a native map SDK
- If the user denies the Android permission, the JS error callback runs
- Leaflet maps load OSM tiles from the network; they do not need GPS

### What this app does *not* do

- No second auth, no FCM, no native officer/citizen screens
- No API secrets in the APK
- No `setAllowUniversalAccessFromFileURLs`
- SSL errors are not ignored

External links (`tel:`, `mailto:`, Google Maps, other hosts) open outside
the WebView. Civic hosts stay inside: `kashnet.online`,
`*.kashnet.online`, `civicconnect-mvp.pages.dev`, plus whatever host is
in the configured base URL.

## Backend / AI (unchanged)

See `civicconnect-backend/README.md` and `civicconnect-ai/README.md`.

```bash
# API + Postgres
cd civicconnect-backend && docker compose up --build

# AI (if not already in that compose file)
cd civicconnect-ai && docker build -t civicconnect-ai . && docker run -p 8001:8001 civicconnect-ai
```

Frontend is static files; production is Cloudflare Pages / the kashnet.online host.
