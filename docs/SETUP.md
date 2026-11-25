# Setup guide

## 1. Cloud backend (Supabase): already set up

Full details: [`backend/README.md`](../backend/README.md) (architecture, security, setup), `backend/API.md`, `backend/TESTING.md`.

- **Project URL:** `https://rlauvxzzjpdkbxwcfogj.supabase.co`. Both apps already contain it and the **publishable** key.
- **Tables:** `observing_sessions` (1) and `celestial_observations` (many). View them under **Table Editor**.
- **No login:** both apps use only the **publishable** key. Nothing needs switching on in the dashboard.
- **Security:** everyone can *read* (that's how AstroExplorer shows everybody's observations). Writing is only possible through the upload function, and a session can only be changed by the phone that first uploaded it (checked with a secret device key stored on the phone). Never put the **secret** / `service_role` key in the apps.
- **Free plan note:** Supabase pauses free projects after about a week with no activity. If the apps say they can't reach the cloud, open the dashboard and click **Restore project**. Open it the day before the demo to be safe.

## 2. Android app (AstroLog, Java)

**Needs:** Android Studio (Ladybug 2024.2 or newer) and an emulator or phone running Android 8.0+ (API 26).

1. The Supabase URL and publishable key are already in `AndroidApp/app/src/main/java/com/astrolog/app/network/CloudConfig.java`. The first upload after installing upgrades the phone's database to version 2 (existing sessions are kept, and are uploaded again once).
2. In Android Studio, choose **File → Open** and select the `AndroidApp` folder.
3. Wait for Gradle sync. It downloads Gradle 8.9 and the libraries the first time. If Studio offers to upgrade the Android Gradle Plugin, it is safe to accept.
4. Press **Run ▶**.

**Testing tips**
- **GPS on the emulator:** open the emulator's **⋯ → Location**, set a point, click *Set location*, then tap the target icon in the Location field.
- **Camera on the emulator:** the emulator's virtual camera works. *Gallery* uses the system photo picker.
- **Upload:** use the cloud icon in the toolbar on the main screen. The green cloud next to a session means it has been uploaded.

## 3. Hybrid app (AstroExplorer, .NET MAUI)

**Needs:** Visual Studio 2022 17.14+ or Visual Studio 2026 with the **.NET Multi-platform App UI development** workload, and the **.NET 10 SDK**.

1. Open `HybridApp/AstroExplorer/AstroExplorer.csproj`.
2. The Supabase URL and key are already in `Services/SettingsService.cs`. The URL can also be changed on the app's **Settings** tab at run time.
3. Choose **Android Emulator** or **Windows Machine** as the target and press **Run ▶**.

**If your lab machine only has .NET 8 or 9:** in `AstroExplorer.csproj`, replace every `net10.0` with `net9.0` (or `net8.0`). No code changes are needed. You'll see a few "obsolete" warnings on .NET 10 (for example for `DisplayAlert`). They are only warnings, and the code is kept compatible with older versions on purpose.

**Android emulator and the internet:** the emulator reaches Supabase normally over HTTPS. No extra configuration is needed.

## 4. Git (Azure DevOps), required for 10% of the marks

1. One member creates the Azure DevOps project and repo (instructions on Moodle) and adds **all group members** and the tutor (**Markus Wolf**, read access).
2. Clone the repo, copy the `AstroLog` folder contents into it, and commit (see `docs/TEAM_PLAN.md` for who commits what).
3. Each member must commit **at least once a week**. Each one works on and commits their own feature.

The `.gitignore` files already exclude build folders (`build/`, `bin/`, `obj/`).
