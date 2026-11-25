# Testing the backend

## 1. Automated database tests (2 minutes)

1. Supabase dashboard → **SQL Editor** → **New query**.
2. Paste `supabase/tests/backend_tests.sql` and click **Run**.
3. Expected result. It shows as an *error* on purpose, because the error rolls back every test row:
   ```
   ERROR: BACKEND TESTS: 25 passed, 0 failed
   [PASS] T01 bulk sync: 2 sessions, 6 observations, 0 failures
   …
   ```

Every check runs as the role the apps use (the publishable key), with two device keys acting as two phones:

| # | What is checked |
|---|---|
| T01 | bulk upload of 2 sessions + 6 observations in one call |
| T02 | repeating the upload creates **no duplicates** |
| T03 | an observation deleted on the phone is deleted in the cloud |
| T04 | partial failure: the good session is saved, 4 bad ones are reported (`missing_field`, `invalid_format`, `invalid_date`, `invalid_value`) |
| T05 | failed sessions leave nothing behind |
| T06–T07 | duplicate observation id, and an observation id from another session, are rejected |
| T08–T10 | empty upload and invalid device key rejected; the device key isn't case-sensitive |
| T11 | another phone **can't overwrite** a session (`not_owner`) |
| T12–T14 | direct UPDATE, DELETE and INSERT with the publishable key are refused |
| T15 | device-key hashes can't be read with the publishable key |
| T16 | everyone can **read** all observations (the MAUI app) |
| T17–T20 | search: Jupiter (2), case-insensitive and partial, Saturn, Moon, Orion Nebula, Andromeda Galaxy, nothing for Pluto; results include session details |
| T21–T23 | pagination; `%` isn't a wildcard; empty search text rejected |
| T24 | only the **hash** of the device key is stored |
| T25 | deleting a session deletes its observations and key (CASCADE) |

**Last run: 25/11/2026, 25 passed, 0 failed.**

## 2. Live end-to-end API test

```bash
python backend/supabase/tests/live_api_test.py backend/supabase/seed/sample-sync-payload.json
```

It makes the same HTTPS calls as the apps: bulk upload, repeat upload, partial failure, a second phone trying to overwrite, missing device key, direct writes (refused), the MAUI download with embedded observations, searches (Jupiter, "jup", Saturn, Moon, Orion Nebula, Andromeda Galaxy, Pluto), pagination and empty search text.

**Last run: 25/11/2026, 21 passed, 0 failed.**

It leaves 2 test sessions (ids starting with `d0d0d0d0-`). Remove them in the SQL Editor:
```sql
delete from public.observing_sessions where id::text like 'd0d0d0d0-%';
```

## 3. Demo data

`supabase/seed/demo_seed.sql` holds 5 sessions and 18 observations (Jupiter, Saturn, Moon, Mars, Venus, Orion Nebula, Andromeda Galaxy, Pleiades). It is **already loaded**. Run it again in the SQL Editor at any time to restore it: it uses fixed ids (`de000000-…`) and `ON CONFLICT … DO UPDATE`, so it never creates duplicates and never touches real data.

To remove only the demo data, run `supabase/seed/demo_remove.sql`.

## 4. Manual end-to-end test with both apps

The full client checklist, with expected results for the demo data, is in [`docs/CLIENT_TESTING.md`](../docs/CLIENT_TESTING.md).

| # | Step | Expected |
|---|---|---|
| E1 | AstroLog: create a session | it appears in the list |
| E2 | Add several observations (Jupiter, Saturn, Moon…) | listed under the session |
| E3 | Close and reopen the app | everything is still there (SQLite) |
| E4 | Tap the cloud **Upload** icon | "N session(s) and M … observation(s) are now saved in the cloud…", green cloud icons |
| E5 | Supabase → **Table Editor** → `observing_sessions` / `celestial_observations` | the same rows and values |
| E6 | Tap Upload again | same message; still the same number of rows (no duplicates) |
| E7 | Edit an observation, then upload | the cloud icon goes grey after editing and green after upload; the change appears in the Table Editor |
| E8 | Aeroplane mode, then upload | "You appear to be offline…", nothing marked uploaded |
| E9 | AstroExplorer: Observations tab, pull to refresh | the uploaded observations appear |
| E10 | Search "Jupiter", then "jup" | every Jupiter observation, with session name, date and observer |
| E11 | Search "Saturn", "Moon", "Orion", "Andromeda" | the matching observations |
| E12 | Tap an observation | detail page: object, location in sky, details, comments, session info and date |
| E13 | Search "Pluto" | empty-state message |
| E14 | AstroExplorer in aeroplane mode, restart | "You are offline. Showing saved data from …" |
| E15 | Settings tab → Test connection | "Connected ✓ N session(s) found in the cloud." |
