# What is pending

## Needs you (I can't do these from here)
1. ~~Create the cloud database~~ **Done:** Supabase backend with **no login** (publishable key only), RLS, bulk upload with device-key ownership, and search. 25/25 database tests and 21/21 live HTTPS tests pass (`backend/README.md`, `backend/TESTING.md`).
2. ~~Build the Android app~~ **Done (25/11/2026):** `AndroidApp/app/build/outputs/apk/debug/app-debug.apk` (5.9 MB). Rebuild with Run ▶ in Android Studio or `gradlew assembleDebug`.
3. ~~Build the MAUI app~~ **Done in the cloud (26/11/2026):** GitHub Actions builds the AstroExplorer APK on every push that changes `HybridApp/`, or on demand (repo → **Actions** → *Build Android APK (AstroExplorer)* → **Run workflow**). Download it from the run's **Artifacts** section. No Visual Studio or .NET needed on your laptop.
4. **Run the end-to-end test** and the manual checks in `TEST_REPORT.md`.
5. **Azure DevOps Git repo**: create it, add all 4 members and Markus Wolf, and have each member commit their own part every week (`TEAM_PLAN.md`).
6. **Fill in `Group List.txt`** with names, IDs and the repo link.
7. **Each student:** an own improvement plus study for the viva (`VIVA_NOTES.md`).
8. **Part B report** (each student): feature table, AI reflection, contribution form.
9. **Screenshots** of both apps for the report.

## Backend (Supabase): small things left
- [ ] **Before the demo:** open the Supabase dashboard so the free project isn't paused (click **Restore project** if it is).
- [x] **Demo data loaded** (25/11/2026): 5 sessions and 18 observations, marked "(Demo)", ids starting `de000000-`. Re-run `backend/supabase/seed/demo_seed.sql` to restore it, or `demo_remove.sql` to remove only the demo rows. Client test checklist: `docs/CLIENT_TESTING.md`.
- [ ] **Optional:** the Security Advisor warns about `rls_auto_enable`, which was in the project before ours. If nobody made it on purpose, remove public access to it. Our own tables and functions have no warnings.
- [ ] **Confirmed in the real apps:** upload from the Android app, then see the data in the Supabase Table Editor and in AstroExplorer (part of item 4).

## Deadlines
- Report PDF: **07/12/2026, 17:00 UK time** (21:30 IST)
- Demo: **08/12/2026**
