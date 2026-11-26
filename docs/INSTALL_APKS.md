# How to install the two apps (APKs)

| App | What it does | APK file | Needs |
|---|---|---|---|
| **AstroLog** | record sessions and observations, upload them to the cloud | `app-debug.apk` (about 6 MB) | Android **8.0** or newer |
| **AstroExplorer** | browse and search everyone's observations from the cloud | `com.astrolog.astroexplorer-Signed.apk` (inside a zip, about 28 MB) | Android 5.0 or newer |

Both apps need an internet connection to talk to the cloud database. No login or account is needed.

---

## 1. Get the APK files

### AstroLog
It's on the laptop where it was built:
```
D:\fl_projects\AstroLog\AndroidApp\app\build\outputs\apk\debug\app-debug.apk
```
(It isn't on GitHub, because APK files are kept out of the repository. To rebuild it: Android Studio → open `AndroidApp` → **Build → Build APK(s)**.)

### AstroExplorer
It's built in the cloud by GitHub Actions:
1. Sign in to GitHub and open **https://github.com/sb0846w/Astronomy-Observation-App/actions** (the repo is private, so use an account that has access).
2. Click the latest **Build Android APK (AstroExplorer)** run with a green ✓.
3. Scroll to **Artifacts** and click **AstroExplorer-android-apk**. A zip file downloads.
4. Unzip it. The file to install is the one ending in **`-Signed.apk`**.

> Artifacts are kept for 30 days. If it has expired, click **Run workflow** on that Actions page and download the new one about 15 minutes later.

---

## 2. Install on an Android phone

### Step A: copy the APK to the phone (choose one)
- **USB cable:** connect the phone, choose **File transfer** on the phone, and copy the APK into its **Download** folder.
- **Google Drive / OneDrive / email to yourself:** upload the APK, then open it on the phone and download it.
- **Directly on the phone (AstroExplorer):** do the "Get the APK files" steps above in the phone's browser, then unzip the download with the **Files** app.

### Step B: allow installing apps from this source (once)
Android blocks apps that don't come from the Play Store until you allow it:
1. Open the APK from the **Files** app (or from your browser's downloads).
2. Android says *"For your security, your phone is not allowed to install unknown apps from this source"*. Tap **Settings**.
3. Turn on **Allow from this source**, then go back.

(Menus differ slightly by brand. On Samsung: **Settings → Apps → ⋮ → Special access → Install unknown apps → My Files → Allow**.)

### Step C: install
1. Tap **Install**.
2. If **Google Play Protect** says *"Unsafe app blocked"* or *"App scan recommended"*, tap **More details → Install anyway** (or **Install without scanning**). This happens because the app is a test build signed with a development key, not from the Play Store.
3. Tap **Open**.

Do the same for the second APK.

### Step D: first launch
- **AstroLog:** when you use the GPS button, allow **Location** ("While using the app") so it can fill in the coordinates. Photos use your normal camera app or the photo picker.
- **AstroExplorer:** opens on the **Observations** tab and loads the cloud data. It should say *"18 observations from 5 sessions"* (the demo data), plus anything uploaded since.

---

## 3. Install on the Android emulator (Android Studio)

1. Android Studio → **Device Manager** → start a virtual phone (Android 8.0 or newer).
2. **Drag and drop** the APK file onto the emulator window. It installs and appears in the app list.

Or from a terminal, if the Android SDK tools are installed:
```powershell
adb install -r "D:\fl_projects\AstroLog\AndroidApp\app\build\outputs\apk\debug\app-debug.apk"
```

---

## 4. Updating to a newer version

- Install the new APK over the old one. Your saved sessions are kept.
- If Android says **"App not installed"** or **"package conflicts with an existing package"**, the new APK was signed with a different key (for example, built on a different computer). Uninstall the old app first (long-press the icon → **Uninstall**), then install the new one.
  - ⚠ Uninstalling **AstroLog** deletes the sessions saved on that phone. Upload them first (cloud icon) so they are safe in the cloud.

---

## 5. Problems and fixes

| Problem | Fix |
|---|---|
| "There was a problem parsing the package" | The download is incomplete or you opened the zip, not the APK. Download again and unzip AstroExplorer first. Check the phone has Android 8.0+ for AstroLog. |
| "App not installed" | See section 4: uninstall the old version first. Also check free storage space. |
| Play Protect blocks it | **More details → Install anyway**. The app is a test build. |
| AstroExplorer shows "Could not reach the cloud service" | Check the internet connection. The free Supabase project may be **paused** after a week without use: open the Supabase dashboard and click **Restore project**, then pull down to refresh. |
| AstroLog upload says "You appear to be offline" | Turn Wi-Fi or mobile data on and tap the cloud icon again. Nothing is lost; the data stays on the phone. |
| Uploaded data doesn't show in AstroExplorer | In AstroExplorer, pull down on the Observations list to refresh. |

---

## 6. Quick check after installing

1. **AstroExplorer:** search **Jupiter**. You should see at least 3 results (demo data).
2. **AstroLog:** create a session, add two observations, tap the **cloud icon**. You should see the *"…now saved in the cloud…"* message.
3. **AstroExplorer:** pull down to refresh. Your new session should appear.

The full test checklist is in `docs/CLIENT_TESTING.md`.
