# Heartwood

A private, **read-only** [Health Connect](https://developer.android.com/health-connect)
viewer for Android, built with Jetpack Compose and Material You. Heartwood shows the health
and fitness data already on your device, beautifully, and without sending a single byte
anywhere.

> No accounts. No network permission. No trackers. Just your data, on your device.

## Features

- **Today**, Material You cards for steps, heart rate, sleep, energy, distance, exercise,
  SpO₂, weight, blood pressure and more, each with a sparkline.
- **Detail views**, full charts, statistics and recent records per metric.
- **Weekly summary**, daily averages vs. goals you set, with progress rings and
  week-over-week trends.
- **Home-screen widgets**, key metrics, dynamically themed (Glance + Material 3).
- **Export**, save your accessible data to CSV or JSON via the system file picker.

## Tech

- Kotlin, Jetpack Compose, Material 3 (dynamic color)
- `androidx.health.connect:connect-client` for all reads
- `androidx.glance` widgets, WorkManager for periodic refresh
- DataStore for goals
- `minSdk 30`, `targetSdk 36`

## Build

```sh
git clone https://github.com/GuyOnWifi/heartwood
cd heartwood
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or with Nix (pins the whole toolchain):

```sh
nix develop
./gradlew :app:assembleRelease
```

Requires JDK 17 and the Android SDK (`platforms;android-36`, `build-tools;36.0.0`). The
Gradle wrapper pins everything else. See [docs/REPRODUCIBLE.md](docs/REPRODUCIBLE.md) for the
full reproducible-build toolchain and Nix notes.

### Signing a release (optional)

Create `keystore.properties` (gitignored) or set the matching `HEARTWOOD_*` env vars:

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without it, `assembleRelease` produces an unsigned APK, which is fine for F-Droid, since it
signs its own builds.

## CI

- **CI** (`.github/workflows/ci.yml`), builds + lints the debug APK on every push/PR.
- **Release** (`.github/workflows/release.yml`), on a `v*` tag, builds the release APK
  (signed if secrets are present) and attaches it to a GitHub Release.

## F-Droid

Descriptions and changelogs live under `fastlane/metadata/android/en-US/`. A recipe template
for the `fdroiddata` repo is in [docs/fdroid-metadata-template.yml](docs/fdroid-metadata-template.yml).

## Privacy

Heartwood requests only Health Connect **read** permissions, holds no network permission, and
never transmits data. You control which data types it can read from Health Connect at any time.

## License

[GPL-3.0-or-later](LICENSE) © Eason Huang
