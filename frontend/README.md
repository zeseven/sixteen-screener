# Sixteen Screener Android app

Android application (Kotlin, Jetpack Compose, Material 3, Android 8.0 / API 26 or newer). It calls the backend on
Cloud Run with an ID token minted from a service account key that is bundled into the APK at build time.
The APK is debug-signed and installed over USB; it is not published to a store.

One-time configuration (the app's service account, its access to the Cloud Run service and its key) is described in
`SETUP.md`. All commands below are run from the `frontend` folder.

## Prerequisites

- JDK 21 (the Gradle wrapper downloads Gradle itself)
- Android SDK with the platform set as `compileSdk` in `app/build.gradle.kts` (Android Studio installs it, or use
  `sdkmanager`); `ANDROID_HOME` points to the SDK folder, e.g.

  ```bash
  export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS default of Android Studio
  ```

- `adb` (from the SDK `platform-tools`) for installing the APK
- `gcloud` CLI for looking up the backend URL
- The service account key file created as described in `SETUP.md`

## Configuration

The build needs two settings. Neither is stored in the repository.

| Setting                  | Where                                                                                       | Description                                         |
|--------------------------|---------------------------------------------------------------------------------------------|-----------------------------------------------------|
| Service account key path | Gradle property `sixteen.serviceAccountKey` or environment variable `SIXTEEN_SERVICE_ACCOUNT_KEY` | Absolute path of the key JSON file outside the repo |
| Backend URL              | Environment variable `SIXTEEN_BACKEND_URL` only                                             | Cloud Run service URL; base URL and token audience  |

### Service account key path

Set it once per machine in the user-level `~/.gradle/gradle.properties` (not in the project's `gradle.properties`):

```properties
sixteen.serviceAccountKey=/Users/you/.sixteen-screener/sixteen-screener-app-key.json
```

or export it in the shell (the Gradle property wins when both are set):

```bash
export SIXTEEN_SERVICE_ACCOUNT_KEY="$HOME/.sixteen-screener/sixteen-screener-app-key.json"
```

The build copies the key into `app/build/generated/sixteen/assets/`; the source tree never contains it.

### Backend URL

The backend URL is passed only through the `SIXTEEN_BACKEND_URL` environment variable. It is never saved in
`gradle.properties`, `local.properties` or any other file of the repository and is never committed. Set it in the
shell before building, using the variables from `backend/SETUP.md` section 2:

```bash
export SIXTEEN_BACKEND_URL="$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)')"
```

### Build failures

Both settings are checked when Gradle configures the project, so every task fails without them, including
`./gradlew test`:

| Case                                          | Message starts with                                                   |
|-----------------------------------------------|-----------------------------------------------------------------------|
| Key path not set                              | `Service account key is not configured.`                              |
| Key path set, file missing                    | `Service account key file '...' does not exist.`                      |
| `SIXTEEN_BACKEND_URL` not set or empty        | `Backend URL is not configured.`                                      |
| `SIXTEEN_BACKEND_URL` not an absolute https URL | `Environment variable 'SIXTEEN_BACKEND_URL' must be an absolute https URL` |

## Tests

```bash
./gradlew test
```

Unit, Robolectric and Compose UI tests run on the JVM without a device. They never call the backend and never read
the real key (tests generate a fake key), but both settings above must still be present for the build to configure.

## Build the APK

```bash
./gradlew assembleDebug
```

The APK is debug-signed with the machine's debug keystore (`~/.android/debug.keystore`); no release keystore is
maintained. The result is:

```
app/build/outputs/apk/debug/app-debug.apk
```

The build number (`versionCode`) is the number of commits on `main`'s first-parent line, calculated from git on every
build; the build fails outside a git checkout. The settings screen shows it with the version and the commit hash, e.g.
`0.1.0 (build 156, ff90477)`. Build releases from a committed `main`: a build with uncommitted changes is marked
`-dirty`, and a build from another branch can repeat a number used on `main`. Don't rewrite commits of `main` that were
already built, as that can repeat or lower build numbers. Raise `versionName` in `app/build.gradle.kts` by hand.

`adb install -r` refuses an APK with a lower build number than the installed one; add `-d` to install an older build
on purpose.

## Install over USB

1. On the phone, enable Developer options and USB debugging, then connect it and accept the debugging prompt.
2. Check that the device is listed and install (or update) the app:

   ```bash
   adb devices
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

`-r` replaces an installed version and keeps its data (the stored profile).

An APK built on another machine is signed with a different debug key and can't replace the installed app. Uninstall
it first:

```bash
adb uninstall com.sixteenscreener.app
adb install app/build/outputs/apk/debug/app-debug.apk
```

Uninstalling deletes the locally stored profile. On the next start, enter the same profile name to get the same
watchlist back.

## Key rotation

After a new key has been created (`SETUP.md`), point the key path to the new file, rebuild the APK and install it on
every device before deleting the old key. An installed APK with a deleted key can no longer call the backend.
