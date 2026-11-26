# Client testing checklist

The full data flow:

**AstroLog (Android)** → SQLite on the phone → **Supabase** (PostgreSQL) → **AstroExplorer (MAUI)** → search and display

## Before testing

| What | Status | Notes |
|---|---|---|
| Supabase schema (tables, keys, indexes, RLS, functions) | ✅ applied | `backend/supabase/migrations/` (6 files, already run) |
| Demo data: 5 sessions, 18 observations | ✅ loaded | `backend/supabase/seed/demo_seed.sql`. Safe to re-run; it never duplicates |
| Configuration | ✅ already in both apps | URL `https://rlauvxzzjpdkbxwcfogj.supabase.co` + **publishable** key, in `AndroidApp/.../network/CloudConfig.java` and `HybridApp/AstroExplorer/Services/SettingsService.cs` |
| Login | none needed | no accounts, by design |
| Supabase project awake | ⚠ check on the day | free projects pause after about a week idle: Dashboard → **Restore project** |
| Build AstroLog | Android Studio → open `AndroidApp/` → Run ▶ | or `gradlew assembleDebug` |
| Build AstroExplorer | GitHub Actions (cloud) ✅ | repo `sb0846w/Astronomy-Observation-App` → **Actions** → latest *Build Android APK (AstroExplorer)* run → **Artifacts → AstroExplorer-android-apk** (a zip containing the `-Signed.apk`) |

**Demo data** (shown with "(Demo)" in the title and observer "AstroLog Demo"):

| Session | Date | Observations |
|---|---|---|
| Night Sky Observation – Jaipur | 18 Nov 2026 | Jupiter, Saturn, Moon, Pleiades |
| Planet Evening – Greenwich Park | 12 Nov 2026 | Venus, Saturn, Moon, Mars |
| Dark Sky Trip – Galloway Forest | 7 Nov 2026 | Andromeda Galaxy, Orion Nebula, Pleiades, Jupiter |
| Winter Nebulae – Mount Abu | 30 Oct 2026 | Orion Nebula, Mars, Andromeda Galaxy |
| Morning Planets – Pune | 24 Oct 2026 | Venus, Jupiter, Moon |

To remove only the demo data: run `backend/supabase/seed/demo_remove.sql` in the SQL Editor. Real data isn't touched.

---

## Test 1: Preloaded data (AstroExplorer)

| # | Step | Expected | ✔ |
|---|---|---|---|
| 1.1 | Open AstroExplorer (with internet) | Observations tab loads; status line "18 observations from 5 sessions • updated just now" | |
| 1.2 | Search **Jupiter** | 3 results (Jaipur, Galloway, Pune) | |
| 1.3 | Search **Saturn** | 2 results | |
| 1.4 | Search **Moon** | 3 results | |
| 1.5 | Search **Orion Nebula** (also try `orion`) | 2 results | |
| 1.6 | Tap the Jaipur Jupiter result | Detail page: Jupiter, "Eastern sky, in Taurus", the details text, comment "Clear sky with good visibility.", session "Night Sky Observation – Jaipur (Demo)", 18 Nov 2026, Nahargarh Fort | |
| 1.7 | Search **Pluto** | empty-state message, no crash | |
| 1.8 | Explore tab → Jupiter | the catalogue entry shows the 3 Jupiter observations | |

## Test 2: Data entry (AstroLog)

| # | Step | Expected | ✔ |
|---|---|---|---|
| 2.1 | Tap **+**, fill in a session (e.g. "Client test session", today, your location, 1 h) → Save → confirm | the session appears in the list with a grey (not uploaded) cloud icon | |
| 2.2 | Open it → add **two or more** observations (e.g. Jupiter + a new object such as "Uranus") | both are listed under the session | |
| 2.3 | Try saving an observation with an empty required field | the field shows an error and nothing is saved | |
| 2.4 | Close the app completely and reopen it | session and observations still there (SQLite) | |

## Test 3: Cloud upload

| # | Step | Expected | ✔ |
|---|---|---|---|
| 3.1 | Main screen → cloud **Upload** icon | "N session(s) and M celestial object observation(s) are now saved in the cloud…"; the session's cloud icon turns green | |
| 3.2 | Supabase → **Table Editor → observing_sessions** | your session is there with the same values | |
| 3.3 | **celestial_observations**, filter `session_id` = your session's `id` | your observations, linked to that session | |

## Test 4: Retrieval in AstroExplorer

| # | Step | Expected | ✔ |
|---|---|---|---|
| 4.1 | Observations tab → pull down to refresh | the count goes up; your new observations appear at the top (newest session first) | |
| 4.2 | Search your new object (e.g. **Uranus**) | your observation, with your session name, date and details | |
| 4.3 | Search **Jupiter** | the 3 demo results plus yours | |

## Test 5: Duplicate prevention

| # | Step | Expected | ✔ |
|---|---|---|---|
| 5.1 | Tap **Upload** 2–3 more times | the same success message each time | |
| 5.2 | Check the Table Editor / refresh AstroExplorer | still exactly one copy of your session and observations | |
| 5.3 | Edit one observation in AstroLog → Upload | the cloud icon goes grey after editing, green after upload; the cloud row is **updated**, not duplicated | |

## Test 6: Offline handling

| # | Step | Expected | ✔ |
|---|---|---|---|
| 6.1 | Turn on aeroplane mode, create a session with two observations | saved normally on the phone | |
| 6.2 | Tap **Upload** | "You appear to be offline… Your data is safe on this phone." Nothing marked uploaded | |
| 6.3 | Turn the internet back on → **Upload** | success; the session appears in Supabase and in AstroExplorer | |
| 6.4 | AstroExplorer in aeroplane mode → restart it | "You are offline. Showing saved data from …"; search still works on the saved data | |

## Extra checks

| # | Step | Expected | ✔ |
|---|---|---|---|
| 7.1 | AstroExplorer → Settings → **Test connection** | "Connected ✓ N session(s) found in the cloud." | |
| 7.2 | Settings → enter a wrong URL → Test connection | a clear "Connection failed: …" message, no crash | |

---

## Automated tests already run (25/11/2026, live project)

| Suite | Result |
|---|---|
| Database tests: `backend/supabase/tests/backend_tests.sql` (upload, retry, partial failure, ownership, permissions, search, cascade) | ✅ 25/25 |
| Live HTTPS API test: `backend/supabase/tests/live_api_test.py` (same calls as both apps) | ✅ 21/21 |
| Demo data through the public API (5 sessions, 18 observations, searches for Jupiter 3, Saturn 2, Moon 3, Orion Nebula 2, Andromeda 2, Mars 2, Venus 2, Pleiades 2, partial and case-insensitive, Pluto 0) | ✅ 15/15 |
| Demo seed run twice → still 5 sessions and 18 observations (idempotent) | ✅ |

Not run here: the tests on a real phone or emulator, which need the built apps (see "Before testing").
