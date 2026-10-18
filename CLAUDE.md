# SGA (Some Gym App): context for Claude

Personal Android workout tracker (sessions, guided workouts, rest timer, history, JSON backup). Single user: the repo owner. See README.md for features and structure.

## Stack
- Kotlin 2, Jetpack Compose + Material 3, Navigation Compose, Room (KSP), kotlinx.serialization. Single activity.
- Package `com.bar.sga`. Code in `app/src/main/java/com/bar/sga/{data,domain,timer,ui}`.
- Offline by design: no INTERNET permission, no server. Keep it that way unless asked.
- UI strings in both `res/values/strings.xml` (English) and `res/values-iw/strings.xml` (Hebrew). Layouts must work right-to-left.

## How the owner works
The owner often fixes bugs from his phone through Claude, away from his Mac. The loop:
1. Claude edits and pushes (any branch).
2. `.github/workflows/phone-build.yml` builds a debug APK (~4 min) and publishes it as the latest GitHub release (`build-N`, asset `sga.apk`).
3. He installs it from https://github.com/barelimelech94/sga/releases/latest/download/sga.apk (linked at the top of README.md).

So: always push finished changes, and check the "Phone build" run passes. Cloud sessions usually have no Android SDK, so CI is the build check. Add `[skip ci]` to commits that don't change the app (docs only).

## Don't break (each one wipes or locks out his workout data)
- **Signing:** CI signs with his Mac's debug keystore from the `DEBUG_KEYSTORE_B64` secret. The workflow passes it to Gradle via `SGA_DEBUG_KEYSTORE` and fails if the APK's certificate doesn't match. Never change the signing setup or `applicationId`; a different signature forces an uninstall.
- **Room schema:** `GymDb` is version 1, with no migrations and no destructive fallback. Any change to entities needs a version bump **and** a `Migration`, or the app crashes on launch for existing data.
- **Backup format:** `data/Backup.kt` must keep importing older backup files.
- `gradle.properties` pins `org.gradle.java.home` to his Mac's JDK; CI overrides it on the command line. Leave both as they are.

## Commands
- `./gradlew :app:assembleDebug`: build
- `./gradlew :app:testDebugUnitTest`: unit tests (`app/src/test`: backup, estimates, seed)
- `make run`: install and launch on a connected device (Mac only)
