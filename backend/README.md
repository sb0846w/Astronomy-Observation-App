# AstroLog backend (Supabase)

The cloud backend shared by both apps. **There's no login:** the coursework doesn't require registration, login or logout, so both apps use only the Supabase **publishable key**.

```
AstroLog (Android) ── create session → add observations → SQLite → Upload ──▶ Supabase PostgreSQL ──▶ AstroExplorer (MAUI)
                                                                                                  fetch → search → details
```

| App | Language | What it does with the backend |
|---|---|---|
| **AstroLog** (`AndroidApp/`) | Java *(Kotlin examples in the docs)* | Uploads all sessions + observations in one call |
| **AstroExplorer** (`HybridApp/`) | C# / .NET MAUI | Downloads, searches and displays everybody's observations |

- **Project:** `rlauvxzzjpdkbxwcfogj` (`https://rlauvxzzjpdkbxwcfogj.supabase.co`), Free plan, no other services.
- **Status (25/11/2026):** all migrations applied. 25/25 database tests (`supabase/tests/backend_tests.sql`) and 21/21 live HTTPS tests (`supabase/tests/live_api_test.py`) pass.

## Folder structure

```
backend/
├── README.md                  ← architecture, security, sync strategy, setup, configuration
├── API.md                     ← every endpoint, request/response examples, error codes
├── ANDROID_INTEGRATION.md     ← Java (what the app uses) + Kotlin examples
├── MAUI_INTEGRATION.md        ← C# examples: download, search, pagination, states
├── TESTING.md                 ← how to run the tests + manual end-to-end test cases
└── supabase/
    ├── migrations/            ← applied in file-name order
    │   ├── 20261125140000_drop_prototype_tables.sql
    │   ├── 20261125140100_core_schema.sql          tables, constraints, indexes, triggers
    │   ├── 20261125140200_row_level_security.sql   RLS (read policies)
    │   ├── 20261125140300_sync_function.sql        first upload function (replaced below)
    │   ├── 20261125140400_search_function.sql      search_observations()
    │   └── 20261125150000_no_login_device_key.sql  final access model: no login, device-key ownership
    ├── seed/
    │   ├── demo_seed.sql              DEMO DATA: 5 sessions, 18 observations, fixed "de000000-" ids, idempotent (loaded)
    │   ├── demo_remove.sql            removes only the demo rows
    │   └── sample-sync-payload.json   example upload body, used by the live API test
    └── tests/
        ├── backend_tests.sql          25 database checks, rolled back after running
        └── live_api_test.py           21 end-to-end checks over HTTPS (same calls as the apps)
```

> The migrations are the real history of the project. An earlier version used Supabase anonymous sign-in. The last migration removes it (and the `profiles` table and `user_id` columns) because the coursework doesn't use authentication. Running all six files in order on a new project gives exactly the current schema.

## 1. Database schema

```
observing_sessions                         celestial_observations
──────────────────                         ──────────────────────
id                  uuid  PK  ◀────┐       id                   uuid  PK
session_title       text  required │       session_id           uuid  FK → observing_sessions.id  ON DELETE CASCADE
session_date        date  required └────── celestial_object     text  required   (e.g. "Jupiter")
session_time        text  HH:mm            location_in_sky      text  required   (e.g. "Gemini, east")
observing_location  text  required         observation_details  text  required
duration_hours      float 0.1–24           additional_comments  text  optional
description         text  optional         object_type          text  optional   (Planet, Galaxy…)
sky_conditions      text  optional         observed_time        text  HH:mm, optional
observer_name       text  optional         has_photo            bool  (photos stay on the phone)
equipment           text  optional         sort_order           int   (the phone's order)
bortle_scale        1–9   optional         created_at, updated_at
seeing_rating       1–5   optional
latitude/longitude  optional, as a pair    private.session_device_keys   (not reachable through the API)
created_at, updated_at                     ───────────────────────────
synced_at           last upload            session_id  PK/FK → observing_sessions.id  ON DELETE CASCADE
                                           device_key_hash   SHA-256 of the uploading phone's secret key
```

- **One session has many observations.** The foreign key uses `ON DELETE CASCADE`.
- **The fields are the ones the Android app already has.** Its own extras (observer, equipment, Bortle, seeing, GPS, photo flag) are kept because both apps already use them.
- **UUID primary keys are made on the phone** (`ObservingSession.cloudId`, `CelestialObservation.cloudId`), so uploads are idempotent.
- **CHECK constraints match the Android form's validation:** non-blank required text, a real date, `HH:mm` times, duration 0.1–24 h, Bortle 1–9, seeing 1–5, coordinates in range and as a pair. Empty optional fields are stored as NULL.
- **`updated_at`** is maintained by a trigger.

### Indexes

| Index | Used for |
|---|---|
| `observing_sessions_session_date_idx` (`session_date desc, session_time desc`) | newest-first lists and paging |
| `celestial_observations_session_id_idx` (`session_id, sort_order`) | loading a session's observations in order, cascade deletes |
| `celestial_observations_object_trgm_idx` (GIN, `pg_trgm`) | case-insensitive **partial** search (`ILIKE '%jup%'`) |

## 2. Access and security (no login)

| Action | With the publishable key |
|---|---|
| Read sessions and observations | ✅ allowed (RLS policies "Anyone can read …"), which the MAUI app needs |
| Upload sessions and observations | ✅ only through `sync_observing_sessions`, which validates every field |
| `INSERT` / `UPDATE` / `DELETE` directly on the tables | ❌ no privileges (HTTP 401) |
| Read the device-key table | ❌ it's in the `private` schema, which isn't exposed |

**How ownership works without accounts.** Session IDs are public (anyone can read them), so the upload function needs some proof that an upload comes from the phone that created the session:

1. On its first upload, each phone generates a random **device key** (a UUID) and keeps it in private app storage (`SharedPreferences`).
2. Every upload sends it as `p_device_key`.
3. The first upload of a session stores the key's **SHA-256 hash** in `private.session_device_keys`.
4. Later uploads of that session must send the same key, otherwise that session fails with `not_owner`.

It isn't a login (no screen, no Supabase Auth), but it stops one phone from overwriting another phone's data.

**Limitations (worth mentioning in the viva):**
- Anyone who extracts the publishable key from the app could upload **new** junk sessions. Existing ones are protected by the device key. The upload function limits each request to 200 sessions with 500 observations each, and Supabase rate-limits the API.
- Clearing the app's data creates a new device key, so that phone can no longer change its earlier uploads (they stay readable).
- Deleting a session on the phone doesn't delete it in the cloud (no delete path for the public key). An admin can delete it in the Table Editor.
- **Recommended upgrade if the app went public:** Supabase **anonymous sign-in**. It still has no login screen, but each phone gets a real user id that RLS can check, and sign-ups can be protected with a CAPTCHA. An earlier version of this backend did exactly that (see the migration history).

**Keys:** the apps contain only the **publishable** key (`sb_publishable_…`), which is designed to be public. The **secret / service_role** key bypasses all security and must never be put in an app, in Git or in a screenshot.

## 3. Synchronisation strategy

1. The phone's SQLite database is the source of truth. Uploading never deletes anything locally.
2. **Upload** sends **all** sessions with **all** their observations in **one** HTTPS request: `POST /rest/v1/rpc/sync_observing_sessions`.
3. For each session the function, **in its own sub-transaction**:
   1. checks the device key (`not_owner` if the session belongs to another phone),
   2. **upserts** the session by its UUID (`INSERT … ON CONFLICT (id) DO UPDATE`),
   3. deletes that session's cloud observations that are no longer on the phone,
   4. upserts the remaining observations by their UUIDs, keeping the phone's order.
4. A failing session is rolled back **on its own** and the others are kept. The reply lists `synced` and `failed` with a reason for each failure.
5. The phone marks **only the synced sessions** as uploaded (green cloud, `uploaded_at` in SQLite). Failed ones stay "not uploaded" for the next attempt.
6. Retrying is always safe: the same UUIDs update the same rows, so there are never duplicates.
7. Editing a session or its observations on the phone clears its uploaded flag, so the change is uploaded next time.

## 4. Setup

### The existing project (normal case)

Nothing to do: the schema is applied and both apps contain the URL and key.

Optional: **Authentication → Sign In / Providers → Allow anonymous sign-ins** can be switched **off** again. The apps no longer use it.

### Re-create the backend in a new Supabase project

1. Create a free project at <https://supabase.com/dashboard>.
2. **SQL Editor**: run the six files in `supabase/migrations/` **in file-name order**.
3. **Project Settings → API Keys**: copy the project URL and the **publishable** key into both apps (section 5).
4. Run `supabase/tests/backend_tests.sql` in the SQL Editor. It should report **25 passed, 0 failed**.
5. Optional sample data: see `TESTING.md` §3.

## 5. Environment configuration

Each app keeps its connection settings in one file:

| Setting | Android: `network/CloudConfig.java` | MAUI: `Services/SettingsService.cs` |
|---|---|---|
| Project URL | `SUPABASE_URL` | `DefaultDatabaseUrl` (can also be changed on the Settings tab) |
| Publishable key | `SUPABASE_PUBLISHABLE_KEY` | `PublishableKey` |

To use a different project (for example a test copy), change those two values in both files.

## 6. Assumptions

- The Android app is **Java** (the group's existing code and `TEAM_PLAN.md`). The docs also show Kotlin versions of the same calls.
- No authentication, as the coursework doesn't require it.
- The MAUI Observations tab keeps its **on-device search** (it works offline, feature f). The server search `search_observations` (with pagination) is available in `CloudService.SearchCloudAsync` and tested, but no page uses it yet.
- Free plan: the project pauses after about 7 days without activity (Dashboard → **Restore project**).
