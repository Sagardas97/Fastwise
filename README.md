# Fastwise

A fasting tracker for Android. Tap **I just ate** when you finish a meal and your fast starts from that moment. The running time stays in your notification bar until your next meal.

## Get it on your phone

### Option A: build in the cloud with GitHub (nothing to install on your PC)
1. Put this folder in a GitHub repository, including the hidden `.github` folder. GitHub Desktop is the
   easiest way; the web uploader can skip hidden folders.
2. Add your signing key as two repository secrets (*Settings > Secrets and variables > Actions*), see
   [Signing key](#signing-key) below.
3. Open the repo's **Actions** tab. The **Build APK** job starts on every push; you can also click *Run workflow*.
4. When it finishes (about 15 minutes, including the emulator test), download **Fastwise-apk** under *Artifacts* and unzip it.
5. Copy the `.apk` to your phone, open it, and allow *Install unknown apps* when asked.

### Option B: Android Studio on Windows
1. Install Android Studio and open this folder. Let Gradle sync finish.
   If it offers to upgrade the Android Gradle Plugin, accepting is fine.
2. Create `keystore.properties` next to `settings.gradle.kts` (see [Signing key](#signing-key)).
3. On the phone: Settings > About phone > Software information, tap *Build number* 7 times,
   then turn on *USB debugging* in Developer options.
4. Plug in the phone, pick **release** under *Build Variants* (it runs much smoother than debug), and press Run.

### Signing key
Android only installs an update over the app you have if it's signed with the same key, which also keeps
your data. The key (`fastwise.p12`) is **not** in this repository. Keep a backup of it and its password somewhere
safe, like a password manager: without it, new versions can't install over the old one.

- **GitHub Actions** reads it from two repository secrets:
  - `FASTWISE_KEYSTORE_BASE64`: the key file as base64 (PowerShell:
    `[Convert]::ToBase64String([IO.File]::ReadAllBytes("fastwise.p12"))`)
  - `FASTWISE_KEYSTORE_PASSWORD`: its password
- **Your PC** reads it from `keystore.properties` (ignored by Git, never commit it):
  ```properties
  storeFile=C:/path/to/fastwise.p12
  storePassword=your password
  ```

Without a key, for example in someone else's copy of this repository, builds use Android's debug key. Those
APKs work, but they can't update an app signed with your key.

## After installing (Samsung / One UI)
- Allow notifications when asked. They power the live timer, the goal alert and encouragement nudges.
- Settings > *Battery usage* inside Fastwise: allow it, so One UI never puts the timer to sleep.
- Widget: long-press the home screen > Widgets > Fastwise.
- Quick Settings tile: pull down the panel > edit (pencil) > drag **I just ate** in.
- Pick a meal plan from the Timer screen card or Settings > Meal plan.

## Features
- **Timer**: a 24-hour dial shows your fast where it actually sits in your day, from your last meal to now, with the night shaded and your goal marked. Fasting stages and a today strip sit underneath.
- **Notification**: a pinned live timer with *I just ate* and *End fast* buttons. It comes back after a restart.
- **Meal plans**: templates for one meal a day (23:1), two meals (20:4, 18:6, 16:8 or an early 8am to 4pm window), two meals and a snack (16:8), and three meals (14:10). Pick one, slide its eating window to fit your day, and it sets your goal, shows the window on the dial and in a Today's plan card, and reminds you at meal times and 30 minutes before the window closes. While planned meals are still to come, logging lunch ends your fast without starting a new one; the new fast starts after your last meal.
- **Motivation**: a line of encouragement that changes as you progress, halfway and one-hour-to-go nudges, a celebration when you hit your goal, a "this week" chain with a weekly target, streaks, and achievements. If you go to log a meal close to your goal, a gentle note tells you how close you are. It never blocks you from eating.
- **Summary**: week and month views with average fasting, longest fast, meals per day, eating window, goal days, a bar chart, a daily rhythm chart and a calendar heatmap. You can share it as text.
- **History**: every meal and fast by day. Tap to fix times, delete with undo, or add past entries.
- **Export**: a zip of CSVs (`meals.csv`, `fasts.csv`, `daily_summary.csv`) for Excel or pandas, plus a full JSON backup and restore.
- **Sync**: link one file in Google Drive, then link the same file on another device. Changes merge both ways record by record, so nothing is overwritten.
- **Quit**: Settings > *Quit Fastwise* stops the timer notification, alerts, meal reminders and sync, and closes the app completely. Nothing starts again on its own, even after a restart, until you open Fastwise or tap the widget or tile. Your data and any running fast are kept; *End fast and quit* stops the fast too.

## Sync details
Settings > *Sync between devices* > **Create sync file**, and save it in Google Drive.
On the second device, install Fastwise, then choose **Use existing file** and pick that same file.
Fastwise syncs a few seconds after each change, when you open the app, and whenever you tap *Sync now*.
Google Drive can take a minute to pass a new version between devices.

## Known issues
To fix later:
- **Overlapping fasts**: you can still add or edit a fast so it overlaps another one. The stats handle that
  correctly (each hour counts once), but no warning is shown. The add and edit dialogs are in `ui/Sheets.kt`.
- **Sync duplicates**: two synced devices can each record the same meal or fast, so History may show both
  copies. Sync keeps every record it hasn't seen before (`Merge` in `data/Models.kt`), so near-identical entries
  from two devices both stay.
- **Widget refresh**: if the home screen widget is placed, Android still briefly starts the app every 30 minutes
  to refresh it, even after Quit. No timer, alarms or notifications come back. The interval is
  `updatePeriodMillis` in `res/xml/fast_widget_info.xml`.

## Project layout
- `app/src/main/java/app/fastwise/data`: models, storage, stats, motivation, sync, export
- `app/src/main/java/app/fastwise/service`: notification service, alarms, tile, receivers
- `app/src/main/java/app/fastwise/ui`: Compose screens and theme
- `app/src/main/java/app/fastwise/widget`: home screen widget

Kotlin, Jetpack Compose and Material 3. minSdk 26, targetSdk 35. No accounts, no internet permission, no tracking.
