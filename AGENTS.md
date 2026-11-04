# AGENTS.md — Sustenance

A private, local nutrition tracker for Android backed by Health Connect, with optional
Gemini-powered photo food logging. Kotlin, Jetpack Compose, Material 3, single module.
Forked from GuyOnWifi/heartwood. GPL-3.0-or-later.

## Commands

```bash
./gradlew :app:assembleDebug :app:lintDebug   # build + lint (this is exactly what CI runs)
./gradlew :app:assembleRelease                # unsigned if no keystore.properties present
./gradlew :app:dependencies --write-locks     # regenerate gradle.lockfile after dependency changes
```

- JDK 17, Gradle 9.8.2, AGP 9.5.0, compileSdk/targetSdk 36, minSdk 30.
- CI (`.github/workflows/ci.yml`) runs `assembleDebug` + `lintDebug` on every push/PR to main.
  There are **no unit or instrumented tests in the project** — verification is build + lint.
- Releases are tag-driven (`git tag v1.0.0 && git push --tags`, `release.yml`).
- Dependency locking is enabled (`lockAllConfigurations`); never edit `gradle.lockfile` /
  `settings-gradle.lockfile` by hand.
- `docs/MANTRA.md` lists features that are **set in stone** (currently: the navigation pill's
  `Spring.DampingRatioMediumBouncy` animation). Do not change anything listed there without
  explicit user instruction.

## Architecture

Single activity (`MainActivity`), Compose-only, **no DI framework** — dependencies are
constructed manually:

- `SustenanceApp` (Application) is the DI container: `healthConnect`, `goals` (GoalsRepository),
  `settings` (SettingsRepository) as `by lazy` singletons. Background code (widget, workers)
  casts `applicationContext as SustenanceApp` to reach them.
- ViewModels get repos via hand-written factories: each ViewModel has
  `companion object { fun factory(...) = viewModelFactory { initializer { ... } } }`.
  Follow this pattern; don't introduce Hilt/Koin.
- `ui/SustenanceRoot.kt` is the app's largest, most stateful file (~800+ lines): it handles
  the availability check → permission flow → goal-setup gate → `MainNav` (NavHost), and hosts
  camera/analysis state. The camera is a **Compose overlay hoisted in `MainNav`**, not a nav
  destination, with predictive-back dismissal and a blur/tap-shield.
- Navigation routes: `today`, `insights`, `settings?scrollTo={scrollTo}`, `goals`,
  `detail/{metricKey}?offset={offset}`. Destinations live in `enum class Dest(route, labelRes, icon)`.
  Custom predictive-back handling (`PredictiveBackState`) with card-style transitions — don't
  replace with default nav animations.

### Data layer (`data/`)

- `HealthConnectManager` is a thin wrapper around one lazy `HealthConnectClient`. Permission
  sets: `metricPermissions` (derived from `Metric.entries`), `writePermissions` (NutritionRecord),
  and `extraPermissions` (`READ_HEALTH_DATA_IN_BACKGROUND` + history; raw string constants at
  the bottom of the file).
  - `readDashboard` fans out with `async` per metric and **swallows per-metric errors**
    (including string-matching "Security"/"permission" to catch revoked grants) so one missing
    record type never blanks the dashboard.
  - The `changes: SharedFlow<Unit>` flow is **self-emitted only on app-driven inserts** — there
    is no HC `ChangesCallback`. Cross-app writes surface only on the next app refresh or the
    30-min widget worker.
  - Writes use `Metadata.manualEntry()` with 1-second-duration NutritionRecords; failures
    propagate raw so callers can distinguish `SecurityException`.
- Three DataStore preference stores: `sustenance_goals` (per-metric `goal_<key>` floats),
  `sustenance_settings`, and `sustenance_secrets` (Gemini API key only, with a migration out of
  settings). Secrets store and the dashboard disk cache are **excluded from backups** via
  `data_extraction_rules.xml` — keep it that way.
- Repos expose `Flow<T>` per key + suspend setters, with defaults inlined at the call site
  (e.g. `GoalCatalog.defaults`). DataStore keys are private vals next to their flow.
- `Metric` (in `Metrics.kt`) is the central enum: key, `titleRes`/`unitRes` string resources,
  `MetricKind` (DAILY_TOTAL sums vs LATEST point samples), fixed `accent: Color`, icon,
  `moreIsBetter`. Enum order = dashboard order. `CALORIC_BALANCE` is special: it reads both
  calories-burned and nutrition and derives the FOOD goal dynamically (`foodGoalForBalance`).

### Background work

- **Widget**: Glance (`SizeMode.Exact`). `provideGlance` reads live HC data via the app
  singletons (not the cached summaries) and re-computes `foodGoalForBalance`. One-time refresh
  work named `sustenance_widget_refresh_now` (REPLACE) is enqueued on permission changes,
  dashboard refreshes, and food logs; a 30-min periodic worker (`sustenance_widget_refresh`,
  KEEP) is scheduled in `Application.onCreate` and needs the background-read permission.
  Tiles deep-link into `MainActivity` with `EXTRA_METRIC`.
- **Fasting notifications**: `FastingNotificationScheduler` enqueues one-time WorkManager work
  (delay = last food log + fasting goal hours). All scheduling logic lives in
  `DashboardViewModel.init`, combining settings flows with last-log time; a fast is notified
  only once per period (`lastFastingNotificationTime`).

### Gemini food logging (`util/GeminiManager.kt`)

- Constructed **per analysis** with the key from the secrets store — not a singleton. Uses the
  legacy `com.google.ai.client.generativeai` SDK.
- Structured output via `responseMimeType = "application/json"` + full `Schema.obj` (every
  field required, including `explanation`); safety thresholds `NONE`; images downscaled to
  1024px; default model `DEFAULT_MODEL = "3.5-flash-lite"` with `"gemini-"` prefixed
  automatically.
- Blank key → toast `R.string.api_key_missing` and abort; the camera stays open on failure so
  the user can retry.

## Conventions

- Strings always come from `res/values/strings.xml` via `@StringRes` references (e.g. `Metric`
  carries `titleRes`); there's a `values-ru` translation — keep both in sync when adding strings.
- Screens are `XxxScreen.kt` composables in feature folders (`dashboard/`, `detail/`,
  `summary/`, `history/`, `onboarding/`, `settings/`, `components/`, `theme/`); ViewModels are
  `XxxViewModel` with the `factory()` companion pattern. State hoisting throughout: screens take
  raw repos/params; `MainNav` collects settings flows with `collectAsStateWithLifecycle`.
- Loading states use "null until DataStore answers" to avoid flashing the dashboard.
- Comments explain *why*, and there are many of them — read them before changing surrounding
  logic (e.g. "HC won't allow it in the same request").
- Always rethrow `CancellationException` before generic catch blocks.

## Gotchas

- **Health Connect permissions are chained**: data+write first, then background+history in a
  *separate* request (HC won't grant them together). Setup advances on a `setupResults` counter,
  not the grant set, because declining leaves `granted` unchanged.
- **Grants are re-checked on every resume** (`LifecycleResumeEffect`) since users can revoke
  them in HC settings; `manageAccess()` falls back through request → add background permission →
  deep-linking into HC's per-app settings screen.
- **Test data must be `autoRecorded`**: HC aggregates exclude manual entries for steps/distance/
  calories. `SeedActivity` (debug-only source set) seeds ~14 days of realistic watch-recorded
  data via `adb shell am start -n xyz.draumaz.sustenance.debug/xyz.draumaz.sustenance.debug.SeedActivity`;
  records are chunked between 08:00–20:00 because the emulator's HC won't aggregate across
  midnight.
- `MainActivity` consumes intents only when `savedInstanceState == null` to avoid re-triggering
  the camera on rotation.
- Refresh-per-offset jobs in ViewModels are cancelled-and-replaced so older reads can't
  overwrite newer data.
- Release builds minify + shrink resources; check `proguard-rules.pro` when adding
  reflection-based code. `dependenciesInfo` is stripped for F-Droid reproducible builds — don't
  re-enable.
- Signing is optional: provide `keystore.properties` (see `keystore.properties.template`) or the
  `SUSTENANCE_*` env vars; otherwise release APKs are unsigned by design.
