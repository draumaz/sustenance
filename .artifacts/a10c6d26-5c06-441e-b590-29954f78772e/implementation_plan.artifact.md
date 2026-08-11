# Lint Fixes and Code Quality Improvements

This plan addresses a variety of lint warnings and potential bugs identified through static analysis. The changes include removing redundant code, improving performance, fixing deprecated API usages, and cleaning up unused imports and parameters.

## Proposed Changes

### [MainActivity](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/MainActivity.kt)

#### [MODIFY] [MainActivity.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/MainActivity.kt)
- Remove redundant `SDK_INT >= Build.VERSION_CODES.Q` check (minSdk is 30).
- Add parameter names to boolean literals in `mutableStateOf`.
- Move lambda argument out of parentheses for `onLogConsumed`.

### [UI Components]

#### [MODIFY] [SustenanceRoot.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/SustenanceRoot.kt)
- Remove unused import `TextButton`.
- Fix unused parameter `e` in catch block (log it or remove it).
- Add missing trailing commas.
- Remove redundant qualifier `androidx.compose.animation.core`.
- Use operator assignment `+=` for `capturedBitmaps`.
- Move lambda arguments out of parentheses.
- Use clarifying parentheses in complex boolean expressions.

#### [MODIFY] [InsightsScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/summary/InsightsScreen.kt)
- Remove unused import `PredictiveBackHandler`.
- Fix unused parameter `onBack`.
- Add parameter names to boolean literals.
- Move lambda arguments out of parentheses.
- Use clarifying parentheses in boolean expressions.

#### [MODIFY] [HistoryScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/history/HistoryScreen.kt)
- Fix deprecated `centerAlignedTopAppBarColors` by using `topAppBarColors`.
- Add parameter names to boolean literals.
- Convert collection call chain to `Sequence`.
- Move lambda arguments out of parentheses.
- Add missing trailing commas.

#### [MODIFY] [DashboardScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/dashboard/DashboardScreen.kt)
- Remove unused import `alpha`.
- Add missing trailing commas.
- Add parameter names to boolean literals.
- Use clarifying parentheses in complex expressions.

### [Data & Logic]

#### [MODIFY] [HealthConnectManager.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/data/HealthConnectManager.kt)
- Convert collection call chains to `Sequence`.
- Replace `Math.round` with Kotlin's `round`.
- Replace cascade `if` with `when`.
- Inline redundant variables `multiplier` and `now`.
- Add missing trailing commas.
- Use clarifying parentheses in boolean expressions.

#### [MODIFY] [InsightsViewModel.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/summary/InsightsViewModel.kt)
- Convert legacy `Long` overload to `Duration` in `delay`.
- Add parameter names to boolean literals.
- Add missing trailing commas.

## Verification Plan

### Automated Tests
- Run `:app:assembleDebug` to ensure the project still compiles.
- Run `:app:lintDebug` again to verify warnings are resolved.

### Manual Verification
- Deploy the app to a device/emulator to ensure UI and basic functionality (logging, insights, history) are still working as expected.
