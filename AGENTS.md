# AGENTS.md — Sustenance

A private, local nutrition tracker for Android backed by Health Connect (HC), with optional
Gemini-powered photo food logging. Kotlin, Jetpack Compose, Material 3, single `:app` module.
Forked from GuyOnWifi/heartwood. GPL-3.0-or-later.

## Hard rules

- `docs/MANTRA.md` lists features that are **set in stone** (currently: the navigation pill's
  `Spring.DampingRatioMediumBouncy` springs). Never change them without explicit user instruction.
- Never edit `gradle.lockfile` / `settings-gradle.lockfile` by hand (dependency locking is on via
  `lockAllConfigurations`); regenerate them with `--write-locks`.
- No DI framework (no Hilt/Koin): follow the manual pattern below.
- Always rethrow `CancellationException` before a generic `catch`. `runCatching` swallows it too,
  so don't wrap suspend calls in it unless you rethrow.
- Keep the Gemini secrets store and the dashboard cache out of backups (see Data layer).
- Every new string goes in **both** `res/values/strings.xml` and `res/values-ru/strings.xml`.

## Commands and verification

```bash
./gradlew :app:assembleDebug :app:lintDebug   # build + lint: run locally before every push
./gradlew :app:assembleRelease                # unsigned unless signing is configured
./gradlew :app:dependencies --write-locks     # after any dependency change
```

- JDK 17, Gradle 9.8.2, AGP 9.5.0, compileSdk/targetSdk 36, minSdk 30.
- **There are no unit or instrumented tests.** "Done" means build + lint pass, and for behavior
  changes, the debug build checked on a device:
  ```bash
  adb install -r app/build/outputs/apk/debug/*.apk
  adb shell am start -n xyz.draumaz.sustenance.debug/xyz.draumaz.sustenance.MainActivity
  adb logcat -d --pid=$(adb shell pidof xyz.draumaz.sustenance.debug)
  ```
  The debug build (`applicationId` suffix `.debug`, version suffix `-debug`) installs alongside
  the user's store copy. The device is usually the user's own phone, so screenshots
  (`adb exec-out screencap -p`) are fine, but don't send input taps without asking.
- There is no CI build/lint on push (the workflow was removed on purpose): the agent runs build +
  lint locally and reads `app/build/reports/lint-results-debug.html` for new warnings, not just
  the pass/fail. Don't re-add a GitHub lint workflow.

## Releases

- `release.yml` fires only on **`v`-prefixed** tags (`git tag v1.9.1 && git push --tags`).
  Older tags (`1.4`–`1.7`) are bare and never triggered it.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` and add
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤500 chars, the Play Store limit).
- Signing is optional. Gradle reads `keystore.properties` (see `keystore.properties.template`) or
  `SUSTENANCE_*` env vars; without either, release APKs are unsigned by design. In CI those env
  vars come from GitHub secrets still named `HEARTWOOD_*` (fork legacy).
- Release builds minify + shrink resources: check `proguard-rules.pro` when adding
  reflection-based code. `dependenciesInfo` is stripped for F-Droid reproducible builds; don't
  re-enable it.

## Architecture

Single activity (`MainActivity`), Compose-only, dependencies constructed by hand:

- `SustenanceApp` (Application) is the DI container: `healthConnect`, `goals`
  (`GoalsRepository`), `settings` (`SettingsRepository`) as `by lazy` singletons. Background code
  (widget, workers) reaches them by casting `applicationContext as SustenanceApp`.
- Each ViewModel has `companion object { fun factory(...) = viewModelFactory { initializer { ... } } }`.
- `ui/SustenanceRoot.kt` (~975 lines) is the largest, most stateful file: availability check →
  permission flow → goal-setup gate → `MainNav` (NavHost), plus all camera/analysis state.
  - The camera is a **Compose overlay hoisted in `MainNav`**, not a nav destination. One
    `PredictiveBackHandler` drives it: from batch mode, back first drops to single capture
    (`exitBatchMode()`); otherwise it closes the camera via `clearCapture()`. Captured `Bitmap`s
    are recycled manually, so never recycle a list an analysis job may still be reading.
- Routes: `today`, `insights`, `settings?scrollTo={scrollTo}`, `goals`,
  `detail/{metricKey}?offset={offset}`; top-level destinations are `enum class Dest(route,
  labelRes, icon)`. Pops use custom card-style predictive-back transitions
  (`PredictiveBackState`, `ui/components/ScreenTransitions.kt`); don't swap in default nav animations.

### Data layer (`data/`)

- `HealthConnectManager` wraps one lazy `HealthConnectClient`. Permission sets:
  `metricPermissions` (from `Metric.entries`), `writePermissions` (NutritionRecord),
  `extraPermissions` (background read + history; raw string constants at the bottom of the file).
  - `readDashboard` fans out with `async` per metric and **swallows per-metric errors**. A tile
    is marked revoked only on a `SecurityException` *and* a confirmed missing grant, so one bad
    record type never blanks the dashboard.
  - `changes: SharedFlow<Unit>` emits on the app's own writes and from a 30-second poll of HC's
    changes feed (HC has no push API). The poll's token covers only *granted* record types (HC
    refuses a token if any requested type is unreadable), it backs off exponentially while reads
    fail, and only a successful `getChanges` resets the backoff.
  - Writes use `Metadata.manualEntry()` with 1-second NutritionRecords. Write failures propagate
    raw so callers can distinguish `SecurityException`.
  - Many read helpers (e.g. `readLastFoodLogTime`) return `null` on failure, so **null means
    "unknown", not "none"**. Don't take destructive action on it.
- DataStore stores: `sustenance_goals` (per-metric `goal_<key>` floats), `sustenance_settings`,
  `sustenance_secrets` (Gemini API key only, migrated out of settings). Plus SharedPreferences
  `sustenance_dashboard_cache`. Secrets and cache are excluded in **both**
  `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`.
- Repos expose a `Flow<T>` per key + suspend setters, with defaults inlined at the call site
  (e.g. `GoalCatalog.defaults`). DataStore keys are private vals next to their flow.
- `Metric` (`Metrics.kt`) is the central enum: key, `titleRes`/`unitRes`, `MetricKind`
  (DAILY_TOTAL sums vs LATEST samples), fixed `accent`, icon, `moreIsBetter`. Enum order is
  dashboard order. Display names go through `metric.title()` (Compose) or `titleResFor(locales)`,
  **not** `stringResource(metric.titleRes)`: FIBER is spelled "Fibre" in British-spelling regions.
  `CALORIC_BALANCE` reads calories burned + nutrition and derives the FOOD goal
  (`foodGoalForBalance`).

### Background work

- **Widget**: Glance (`SizeMode.Exact`). `provideGlance` reads live HC data through the app
  singletons (not cached summaries) and recomputes `foodGoalForBalance`. One-time refresh work
  `sustenance_widget_refresh_now` (REPLACE) is enqueued on permission changes, dashboard
  refreshes, and food logs. A 30-min periodic `sustenance_widget_refresh` (KEEP) is scheduled in
  `Application.onCreate` and needs the background-read permission. Tiles deep-link into
  `MainActivity` with `EXTRA_METRIC`.
- **Fasting notifications**: one-time WorkManager work at last food log + fasting goal hours,
  notified once per fast (`lastFastingNotificationTime`). The schedule/cancel decision is
  `FastingNotificationScheduler.apply()`, fed by `DashboardViewModel.init` while the app runs
  and by `syncWithHealthConnect()` from the widget worker in the background. An unknown
  (null) last log leaves pending work untouched.

### Gemini food logging (`util/GeminiManager.kt`)

- Constructed **per analysis** with the key from the secrets store, not a singleton. Uses the
  legacy `com.google.ai.client.generativeai` SDK.
- Structured output: `responseMimeType = "application/json"` + a full `Schema.obj` (every field
  required, including `explanation`); safety thresholds `NONE`; images downscaled to 1024px;
  `DEFAULT_MODEL = "3.5-flash-lite"`, with `"gemini-"` prefixed automatically.
- Blank key → toast `R.string.api_key_missing` and abort. On failure the camera stays open so
  the user can retry.

## Conventions

- Strings come from resources via `@StringRes` references; no hardcoded UI text.
- Screens are `XxxScreen.kt` composables in feature folders (`dashboard/`, `detail/`,
  `summary/`, `history/`, `onboarding/`, `settings/`, `components/`, `theme/`); ViewModels are
  `XxxViewModel` with the `factory()` companion. State is hoisted: screens take repos/params, and
  `MainNav` collects settings flows with `collectAsStateWithLifecycle`.
- Loading states are "null until DataStore answers" so the dashboard doesn't flash defaults.
- Comments explain *why* and are dense. Read them before changing nearby logic.
- Compose BOM 2026.05.01 resolves material3 to **1.4.0 stable**, where the M3 Expressive APIs
  (`LoadingIndicator`, wavy progress, `MaterialShapes`, `MotionScheme.expressive()`, custom
  `motionScheme`) are internal and won't compile. Build Expressive-style UI from stable
  components and explicit springs.
- Commits: imperative, user-facing subject (e.g. "Keep the detail header content vertically
  centred"); the body explains what was wrong and why the fix works. One logical change per commit.

## Gotchas

- **HC permissions are chained**: data + write first, then background + history in a *separate*
  request (HC won't grant them together). Setup advances on a `setupResults` counter, not the
  grant set, because declining leaves `granted` unchanged.
- **Grants are re-checked on every resume** (`LifecycleResumeEffect`) because users can revoke
  them in HC settings. `manageAccess()` falls back through request → add background permission →
  deep link into HC's per-app settings.
- **Test data must be `autoRecorded`**: HC aggregates exclude manual entries for steps/distance/
  calories. The debug-only `SeedActivity` seeds ~14 days of watch-like data:
  `adb shell am start -n xyz.draumaz.sustenance.debug/xyz.draumaz.sustenance.debug.SeedActivity`.
  Records are chunked 08:00–20:00 because the emulator's HC won't aggregate across midnight.
- `MainActivity` consumes intents only when `savedInstanceState == null`, so rotation doesn't
  re-trigger the camera.
- Per-offset refresh jobs in ViewModels are cancelled and replaced so older reads can't
  overwrite newer data.
- ViewModels collect `changes` for their whole lifetime, so the HC poll keeps running while the
  process is alive in the background (throttled by backoff when reads are refused).
