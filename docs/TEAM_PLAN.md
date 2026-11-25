# Team plan: who owns what

Marks are adjusted by **individual contribution**, and in the **demo (20%)** each person is questioned on the code. So every student owns one clear slice, studies it until they can explain it without notes, commits it themselves, and makes at least one improvement of their own.

| Student | Owns | Files |
|---|---|---|
| **Student 1** | **Feature a**: session form, validation, confirmation dialog, plus the **GPS** extra | `ui/SessionFormActivity.java`, `layout/activity_session_form.xml`, `util/DateUtils.java`, `values/strings.xml`, `values/arrays.xml` |
| **Student 2** | **Feature b**: SQLite database, session list, edit/delete, search, plus the **dark mode** extra | `data/AstroDbHelper.java`, `model/*.java`, `ui/MainActivity.java`, `ui/SessionAdapter.java`, `ui/SessionDetailActivity.java`, `ui/SessionActions.java`, `util/ThemeHelper.java`, `AstroLogApp.java`, `values*/themes.xml`, `values*/colors.xml`, `layout/activity_main.xml`, `layout/item_session.xml`, `layout/activity_session_detail.xml` |
| **Student 3** | **Features c + d**: celestial objects, cloud upload, Supabase setup, plus the **photo** extra | `ui/ObservationFormActivity.java`, `ui/ObservationAdapter.java`, `util/PhotoUtils.java`, `network/CloudUploader.java`, `network/CloudConfig.java`, `network/HttpJson.java`, `backend/` (Supabase SQL + docs), `layout/activity_observation_form.xml`, `layout/item_observation.xml`, `xml/file_paths.xml`, `AndroidManifest.xml` |
| **Student 4** | **Feature e** and the hybrid extras: the whole .NET MAUI app | everything in `HybridApp/AstroExplorer/` |

Suggested contribution split for the CW Contribution Form: **25% each**, if that's what actually happens.

## Weekly commit plan (deadline 07/12/2026, demo 08/12/2026)

| When | What |
|---|---|
| **This week (by Sun 29 Nov)** | Student 2 creates the Azure DevOps repo, adds everyone and the tutor, and commits the Gradle project skeleton, models and database helper. The others each commit their files once they have read them and run them. |
| **Next week (30 Nov – 6 Dec)** | Everyone makes **at least one improvement of their own** to their part (ideas below), tests it, and commits. Student 3 runs the full end-to-end test: add sessions → upload → view in AstroExplorer. |
| **Sun 6 Dec** | Final bug fixes, take screenshots for the report, and tag the final commit. |

Commit early and often with clear messages, e.g. `Add required-field validation to session form`.

### Ideas for your own improvement (pick one each)
- **S1:** default the date to today; show the moon phase for the chosen date; add a "time zone" or "temperature" field.
- **S2:** sort options (by date or name); swipe-to-delete with an undo Snackbar; show statistics (total objects seen).
- **S3:** upload progress for many sessions; delete a session from the cloud when it is deleted on the phone; upload photos to Supabase Storage.
- **S4:** a map page with session pins; a "most observed objects" chart; caching favourites in SQLite instead of Preferences.

## AI reflection (Part B, 4%)

The brief says AI-generated content must be declared, referenced and commented, and your reflection must be honest. In yours, mention:
- **Tool:** Claude (Anthropic).
- **How it helped:** generating a first version of the code structure, which you then studied, ran, tested and changed.
- **Your prompts:** include the actual prompt(s) used.
- **How you checked it:** e.g. built and ran it on the emulator, tested every required field, found and fixed issues X/Y, verified the cloud data in the Supabase Table Editor.

Keep the one-line note at the top of the README, and add a short comment such as `// Initial version generated with AI assistance (Claude) and reviewed/modified by <name>` to the files you present.
