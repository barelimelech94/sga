# Gym

A personal workout tracker for Android. Plan your sessions, run a workout with a built-in rest timer, log every set, and watch your progress over time. Everything is stored on the device. There is no account, no server, and no network access.

## Features

- **Sessions.** Build workout sessions (for example Push / Legs / Pull) from a reusable exercise bank. Each exercise has default weight, sets, reps, work time and rest time, which you can override per session. Reorder exercises by drag and drop.
- **Guided workouts.** Start a session and tick off sets as you go. The app shows the next set, the weight and reps from last time, and the estimated time remaining. You can add extra sets, undo the last set, finish early or abort.
- **Rest timer.** An exact-alarm rest countdown with +30s and Skip controls. It notifies you when rest is over, even if the app is in the background.
- **History.** A per-exercise chart of top-set weight per workout, with the full set log for each date.
- **Backup and restore.** Export all data to a JSON file and restore it later. Imports are validated and applied in one transaction, so a bad file never changes your data.
- **Hebrew and English.** Full right-to-left support and an in-app language switch.
- **Light and dark themes.** Follow the system or choose one in Settings. The UI uses the Rubik typeface.
- **Seed data.** On first launch the database is seeded with a three-day ABC split (chest/shoulders/triceps, legs, back/rear delts/biceps), defined in [Seed.kt](app/src/main/java/com/bar/gym/data/Seed.kt).

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2 (JVM 17) |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Persistence | Room (KSP) |
| Backup format | kotlinx.serialization (JSON) |
| Drag to reorder | [Reorderable](https://github.com/Calvin-LL/Reorderable) |
| Min / target SDK | 26 / 35 |

## Project structure

```
app/src/main/java/com/bar/gym/
├── GymApp.kt, MainActivity.kt   # Application and single-activity entry point
├── data/                        # Room entities, DAO, database, seed data, backup
├── domain/Estimates.kt          # Pure logic: time estimates, next set, history points
├── timer/                       # Rest timer and alarm receiver
└── ui/                          # Compose screens, navigation, theme, line chart
app/src/main/res/                # Strings (en, he), themes, Rubik font, icons
app/src/test/                    # Unit tests: backup, estimates, seed
```

## Getting started

### Requirements

- JDK 17
- Android SDK with platform 35 (Android Studio is the easiest way to get both)
- An Android 8.0+ (API 26) device or emulator

### Build and run

```sh
# Point Gradle at a JDK 17 if JAVA_HOME is not already set, e.g. on macOS with Homebrew:
export JAVA_HOME=/opt/homebrew/opt/openjdk@17

./gradlew :app:installDebug      # build and install on a connected device or emulator
```

Or open the project in Android Studio and press Run. Create `local.properties` with `sdk.dir=<path to your Android SDK>` if Android Studio has not done so.

### Make shortcuts

A [Makefile](Makefile) wraps the common commands and sets `JAVA_HOME` and the SDK paths (Homebrew defaults; override with e.g. `make run SDK=~/Library/Android/sdk`). Run `make help` for the list.

```sh
make emulator   # start the emulator and wait for boot
make run        # build, install and launch the app
make test       # unit tests
```

### Run on an emulator

**Android Studio:** open *Device Manager*, create a virtual device (a Pixel with API 26 or newer, API 35 recommended) if you have none, press ▶ to start it, then press Run.

**Command line:** set `SDK` to your Android SDK path (on macOS with Homebrew: `/opt/homebrew/share/android-commandlinetools`). `adb` and `emulator` are usually not on `PATH`.

```sh
SDK=/opt/homebrew/share/android-commandlinetools

# One-time: list or create a virtual device
$SDK/emulator/emulator -list-avds
$SDK/cmdline-tools/latest/bin/avdmanager create avd -n gym -k "system-images;android-35;google_apis;arm64-v8a"
# (install the image first if needed: sdkmanager "system-images;android-35;google_apis;arm64-v8a")
# On an Intel machine use x86_64 instead of arm64-v8a.

# Start it (drop -no-window to see the screen)
$SDK/emulator/emulator -avd gym -no-audio -no-boot-anim &

# Wait until it has booted, then install and launch the app
$SDK/platform-tools/adb wait-for-device
./gradlew :app:installDebug
$SDK/platform-tools/adb shell am start -n com.bar.gym/.MainActivity
```

Add `-read-only -no-snapshot` to the emulator command to discard all changes when it closes. On Android 13+, allow notifications when prompted so the rest-timer alert works.

### Test

```sh
./gradlew :app:testDebugUnitTest
```

## Data and privacy

All data lives in a local Room database on your phone. Use **Settings → Export backup** before uninstalling the app or switching phones, and **Import backup** to restore. Restoring replaces all current data.

## Permissions

| Permission | Why |
| --- | --- |
| `POST_NOTIFICATIONS` | Show the rest-timer notification |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Fire the "rest over" alert on time |
| `VIBRATE` | Vibrate when rest is over |
