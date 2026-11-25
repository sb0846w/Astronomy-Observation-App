# Test report (25 Nov 2026)

## Where testing was done
Neither the cloud workspace nor the VM on the Mac has internet access, so the Android SDK, Gradle libraries and .NET MAUI could not be downloaded. **No APK or MAUI build was produced.** Everything that can be checked without those SDKs was checked:

| Check | Result |
|---|---|
| Java syntax of all 16 Android source files (`javac`) | ✅ No syntax errors |
| Every call between our own classes resolves (models, DB helper, uploader, activities) | ✅ Only `finish()` is unresolved, which comes from the Android SDK |
| Every `R.id / R.string / R.layout / R.menu / R.array / R.drawable` used in Java exists in `res/` | ✅ 119 references, none missing |
| Every `@string / @drawable / @style / @id` used in XML exists | ✅ The only unmatched ones are Material library styles |
| All XML layouts, menus, the manifest and the MAUI XAML/csproj are well-formed | ✅ |
| Unit tests: `DateUtils` (storage/display/parse/time), `ObservingSession` (UUID unique, duration label, defaults, upload flag) | ✅ 15/15 passed |
| SQLite schema from `AstroDbHelper`, run in real SQLite: tables + index created, NOT NULL on required fields, UNIQUE `cloud_id`, list query sorts newest first with correct object counts, `ON DELETE CASCADE` removes observations | ✅ All passed |
| JSON contract: keys sent by `CloudUploader.java` = parameters of `sync_observing_sessions` = columns read by the MAUI models (`[JsonPropertyName]` + `SnakeCaseLower`). Changed Android classes (`network/*`, `data/*`, `model/*`) compile with `javac` against `android.jar` (API 35) | ✅ |
| Supabase backend (25/11/2026): `backend/supabase/tests/backend_tests.sql` run on the live project as the publishable-key role: bulk upload, no duplicates on retry, deletion sync, partial failure with error codes, device-key ownership (another phone gets `not_owner`), direct writes refused, everyone can read, search (Jupiter/Saturn/Moon/Orion Nebula/Andromeda, case-insensitive, partial, paginated), cascade delete | ✅ 25/25 passed |
| Live end-to-end over HTTPS (`backend/supabase/tests/live_api_test.py`, 25/11/2026), same calls as the apps, publishable key only: bulk upload, retry without duplicates, per-session failure report, second phone blocked, missing device key rejected, direct PATCH/DELETE/INSERT refused (401), MAUI download with embedded observations and matching JSON keys, searches with pagination | ✅ 21/21 passed |
| Every object suggested in the Android app links to an entry in the hybrid Explore guide | ✅ 20/20 |
| `docs/sample-data.json` is valid JSON | ✅ |

## Still to test on a real machine (with internet)
- [x] Build the Android APK: `gradlew assembleDebug` BUILD SUCCESSFUL (25/11/2026) → `AndroidApp/app/build/outputs/apk/debug/app-debug.apk`
- [ ] Build and run AstroExplorer in Visual Studio (Android emulator / Windows)
- [ ] End-to-end: add sessions → upload → see them in AstroExplorer → search "Jupiter"
- [ ] Manual checks: empty required fields show errors; confirm/edit dialog; edit/delete; rotation keeps form data; GPS on emulator; camera/gallery photo; dark mode toggle; offline mode in AstroExplorer
