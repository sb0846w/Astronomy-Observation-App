# AstroLog: COMP1424 Mobile Application Development coursework

An app for amateur astronomers to record observing sessions and the celestial objects they see. It comes in two parts:

| Folder | App | Technology | Features |
|---|---|---|---|
| `AndroidApp/` | **AstroLog** (native) | Android, **Java**, SQLite | a, b, c, d + extras |
| `HybridApp/AstroExplorer/` | **AstroExplorer** (hybrid) | **.NET MAUI**, C# | e + extras |
| `docs/` | Setup guide, team plan, viva notes, sample cloud data | | |

Both apps talk to the same **Supabase** (PostgreSQL) database through its REST API (plain HTTPS + JSON). Everything about the backend (schema, security, API, tests) is in [`backend/`](backend/README.md).

---

## Feature checklist (for the Part B table)

| # | Feature | Status | Where in the code |
|---|---|---|---|
| a | Enter session details, required-field validation, confirmation screen with "go back & edit" | ✅ | `ui/SessionFormActivity.java`, `res/layout/activity_session_form.xml` |
| b | Store in SQLite, list all sessions, edit, delete | ✅ | `data/AstroDbHelper.java`, `ui/MainActivity.java`, `ui/SessionAdapter.java`, `ui/SessionDetailActivity.java` |
| c | Add multiple celestial objects per session (required/optional fields), stored in SQLite, list per session | ✅ | `ui/ObservationFormActivity.java`, `ui/ObservationAdapter.java` |
| d | Upload all sessions + observations at once to the cloud, with a friendly confirmation | ✅ | `network/CloudUploader.java`, `network/CloudConfig.java` |
| e | Hybrid app: downloads observations from the cloud, searches by object (e.g. Jupiter), and has information about celestial objects | ✅ | `HybridApp/AstroExplorer/Services/CloudService.cs`, `Views/*` |
| f | Extra features (both apps, see below) | ✅ | |

### Extra features (f)

**Android (AstroLog)**
- Dark mode toggle that is remembered between launches (`util/ThemeHelper.java`)
- GPS location: tap the target icon in the Location field to fill in your town automatically (`SessionFormActivity.fetchLocation`)
- Attach a photo to an observation from the camera or gallery (`ObservationFormActivity`, `util/PhotoUtils.java`)
- Search sessions by name, location or observer from the toolbar
- Extra session fields: observer name, equipment (chips), Bortle sky-darkness scale (slider), seeing rating (stars)
- Extra observation fields: object type (auto-filled when you pick a known object), time observed, auto-complete for objects and constellations
- Cloud status icon on each session, and the session is marked "needs re-upload" after any edit
- Re-uploading never creates duplicates: each session has a stable UUID used as its cloud key

**Hybrid (AstroExplorer)**
- Explore tab: a built-in guide to 20 popular objects (distance, brightness, best season, observing tips)
- Favourites saved on the device
- Works offline: the last download is cached to a file
- Light / dark / system theme
- Filter observations by object type, pull to refresh
- Open the observing site in the Maps app, and share an observation
- Cloud URL can be changed in Settings without rebuilding

---

## Quick start

See **[docs/SETUP.md](docs/SETUP.md)** for step-by-step instructions. In short:

1. The Supabase database is already set up, and its URL and key are in both apps (see `docs/SETUP.md`).
2. Paste the URL into `AndroidApp/app/src/main/java/com/astrolog/app/network/CloudConfig.java` and `HybridApp/AstroExplorer/Services/SettingsService.cs` (or enter it later on the app's Settings tab).
3. Open `AndroidApp/` in **Android Studio** and click Run.
4. Open `HybridApp/AstroExplorer/AstroExplorer.csproj` in **Visual Studio 2022/2026** (with the .NET MAUI workload) and run it on the Android emulator or Windows Machine.

## Academic integrity note

These apps were developed with help from an AI assistant (Claude, by Anthropic). The coursework brief says borrowed or AI-assisted code must be **referenced and commented**, and each student's **AI reflection** must describe this honestly. Each member must also be able to explain every line of the part they present at the demonstration. See `docs/TEAM_PLAN.md`.
