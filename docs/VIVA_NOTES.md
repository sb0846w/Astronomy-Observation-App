# Demo / Q&A preparation notes

Each section covers what the tutor is likely to ask, with a short answer you should be able to give **in your own words** while pointing at the code.

---

## Feature a: session form (Student 1)

**How does validation work?**
`SessionFormActivity.validate()` checks each required field (`requireText`). If one is empty it calls `TextInputLayout.setError(...)`, which shows a red message under that field. The duration must also be between 0.1 and 24 hours. A `TextWatcher` clears the error as soon as the user types.

**Why date and time pickers instead of typing?**
They prevent invalid formats and reduce typing on a phone. The date is shown as "Fri 05 Dec 2026" but stored as `yyyy-MM-dd` (in the field's tag), so it sorts correctly in SQL.

**How does the confirmation step work?**
`showConfirmation()` builds a summary and shows a `MaterialAlertDialogBuilder`. "Go back & edit" just closes the dialog, so all values stay in the form. "Confirm & save" calls `saveSession()`.

**How does GPS work?**
1. Check or request the location permission with the Activity Result API (`RequestMultiplePermissions`).
2. Use `LocationManager`: a recent `getLastKnownLocation` fix if one is available, otherwise `getCurrentLocation` (API 30+) or `requestSingleUpdate` (older phones).
3. `Geocoder` turns the coordinates into a town name **on a background thread**, because it can use the network.

Alternative you could mention: Google Play Services' `FusedLocationProviderClient` is more battery-efficient but adds a dependency.

---

## Feature b: SQLite (Student 2)

**Why SQLiteOpenHelper?**
It handles creating and upgrading the database (`onCreate` / `onUpgrade`). The spec asked for SQLite. Room would be the modern alternative: less boilerplate and compile-time checked queries.

**Tables and relationship:** `sessions` 1 → many `observations`, linked by `session_id`, a FOREIGN KEY with `ON DELETE CASCADE`. Foreign keys are off by default in SQLite, so `onConfigure` turns them on.

**Why a singleton helper?** All screens share one database connection, and the helper uses the application context so it doesn't leak an Activity.

**How is the observation count shown without N+1 queries?** A sub-query: `SELECT s.*, (SELECT COUNT(*) ...) AS obs_count`.

**How does search work?** `SessionAdapter` keeps the full list and a filtered list. `filter()` runs on every keystroke from the toolbar `SearchView`.

**Dark mode:** `AppCompatDelegate.setDefaultNightMode()`. The colours live in `values/colors.xml` (light) and `values-night/colors.xml` (dark). The choice is saved in `SharedPreferences` and applied in `AstroLogApp.onCreate()` before any screen opens.

---

## Features c + d: observations and upload (Student 3)

**How are multiple observations per session stored?** Each row in `observations` has the `session_id` of its session. `SessionDetailActivity` loads them with `getObservationsForSession(id)`.

**Why auto-complete?** Less typing, consistent names (which helps searching in the hybrid app), and choosing a known object also fills in its type.

**Photos:** *Camera* uses `ActivityResultContracts.TakePicture` with a `FileProvider` URI, so the camera app writes straight into our private folder. *Gallery* uses the system Photo Picker (`PickVisualMedia`), which needs no storage permission. Images are loaded with `inSampleSize` to avoid `OutOfMemoryError`.

**Upload: how are all sessions sent at once?**
`CloudUploader` builds **one** JSON body `{ "p_device_key": "<this phone's secret UUID>", "p_sessions": [ session+observations, ... ] }` and sends **one** POST to `/rest/v1/rpc/sync_observing_sessions` with the publishable key (no login). That PostgreSQL function handles each session in its own sub-transaction: it *upserts* the session and its observations by their UUIDs (`INSERT … ON CONFLICT DO UPDATE`) and deletes observations removed on the phone. It returns which sessions were `synced` and which `failed` and why. Only the synced ones get the green cloud icon, retrying never creates duplicates, and nothing is deleted from SQLite.

**Why a UUID cloudId instead of the SQLite id?** SQLite ids are only unique on one phone. Two phones would both have session 1. A UUID is unique everywhere, and re-uploading replaces rather than duplicates.

**Why Supabase, and why a database function?** Supabase is a hosted PostgreSQL database with an automatic REST API, so the data is relational: `observing_sessions` 1 ──< many `celestial_observations` with a foreign key and `ON DELETE CASCADE`. There is no login (the coursework doesn't need one), so the publishable key may only **read** the tables. The only way to write is the upload function (`SECURITY DEFINER`), which validates every field. **Ownership without accounts:** session ids are public, so each phone sends a secret device key. The database stores only its SHA-256 hash, and a session can only be changed with the key that first uploaded it. The hybrid app reads everything in one request with `?select=*,celestial_observations(*)`, which embeds each session's observations through the foreign key.

**Threading:** network calls can't run on the main thread (`NetworkOnMainThreadException`), so the upload runs on an `ExecutorService`, and a `Handler(Looper.getMainLooper())` posts the result back to update the UI.

**Errors:** we check connectivity first (`ConnectivityManager`), handle non-2xx responses, and show a clear dialog. The data stays safe in SQLite.

Alternatives to mention: Retrofit/OkHttp (cleaner HTTP), the Supabase client libraries (supabase-kt / supabase-csharp), and WorkManager (retries the upload automatically when the network comes back).

---

## Feature e: hybrid app (Student 4)

**How does it get the data?** `CloudService.RefreshAsync()` uses `HttpClient.GetStringAsync("{url}/sessions.json")` and deserialises it with `System.Text.Json` into `Dictionary<string, ObservingSession>`. Each observation then gets a back-reference to its session, and they are flattened into one list.

**Search:** `CloudService.Search()` uses LINQ `Where` with case-insensitive `Contains` across the object name, type, sky location, session and observer. The Explore tab matches catalogue entries by name **or alias** (e.g. "M42" → Orion Nebula).

**Why Shell?** It gives tabs and URI-based navigation (`GoToAsync(route, parameters)`) with very little code. Detail pages receive data through `IQueryAttributable`.

**Dependency injection:** services are registered as singletons in `MauiProgram`, so every page shares the same downloaded data. Pages are transient.

**Offline mode:** each successful download is written to `FileSystem.AppDataDirectory/sessions_cache.json`. If `Connectivity` reports no internet, or the request fails, the cache is loaded and the status line says so.

**Persistence:** `Preferences` (key/value) stores the theme, the cloud URL and favourites.

**Theme:** `AppThemeBinding` in `App.xaml` with `Application.UserAppTheme`.

**Native vs hybrid, a likely question:**
- *Native:* full API access, best performance, platform look and feel, but one codebase per platform.
- *Hybrid (MAUI):* one C# codebase runs on Android, iOS, Windows and macOS, but there is a layer of abstraction, platform-specific bugs can appear, and apps are bigger.

---

## General questions

- **Why Java and not Kotlin?** Everyone in the group already knew Java, it's fully supported, and the code is explicit, which is easy to follow in a demo. Kotlin would be shorter (data classes, null safety, coroutines instead of an Executor).
- **Limitations / future work:** with no login, anyone who extracts the publishable key could upload new junk sessions (existing ones are protected by the device key; the fix would be Supabase anonymous sign-in, which still needs no login screen). Clearing the app's data creates a new device key, so old uploads can't be edited from that phone. Deleting a session on the phone doesn't delete it in the cloud, photos are not uploaded, and there is no automatic retry of uploads.
