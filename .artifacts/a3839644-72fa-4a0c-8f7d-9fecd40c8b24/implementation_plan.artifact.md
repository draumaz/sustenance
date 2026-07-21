# Implement Universal Predictive Back Animation

The goal is to implement a consistent "soft fade" predictive back animation across all screens in the app, including Metric Detail, Settings, Summary, and History screens. This will ensure that as the user swipes back, the current screen fades out smoothly, and the navigation bar provides visual feedback (highlighting the destination screen).

## User Review Required

> [!NOTE]
> The "soft fade" will be achieved by animating the `alpha` of the screen content based on swipe progress. We will also ensure the custom navigation bar's predictive highlighting works for all these screens.

## Proposed Changes

### Navigation Transitions

#### [MODIFY] [SustenanceRoot.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/SustenanceRoot.kt)
- Update `NavHost` transitions for the `detail/{metricKey}` route to use a simple `fadeIn`/`fadeOut` (matching Settings and Summary) instead of the current `scaleIn`/`scaleOut`.
- Replace the `BackHandler` for the History screen with a `PredictiveBackHandler` to capture swipe progress.
- Pass `pbState` to `SettingsScreen` and `SummaryScreen`.

---

### Screen Implementations

#### [MODIFY] [DetailScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/detail/DetailScreen.kt)
- Simplify the `graphicsLayer` animation. Instead of scale, translation, and corner radius changes, it will now only animate `alpha` from `1f` down to `0f` based on the predictive back progress.

#### [MODIFY] [SettingsScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/settings/SettingsScreen.kt)
- Add `pbState: PredictiveBackState` parameter.
- Implement `PredictiveBackHandler` to update `pbState.progress` and `pbState.isSwipeActive`.
- Wrap the main content in a container with a `graphicsLayer` that animates `alpha` based on `pbState.progress`.

#### [MODIFY] [SummaryScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/summary/SummaryScreen.kt)
- Add `pbState: PredictiveBackState` parameter.
- Implement `PredictiveBackHandler` to update `pbState.progress` and `pbState.isSwipeActive`.
- Wrap the main content in a container with a `graphicsLayer` that animates `alpha` based on `pbState.progress`.

#### [MODIFY] [HistoryScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/history/HistoryScreen.kt)
- Add `predictiveBackProgress: Float` parameter (defaulting to `0f`).
- Apply `graphicsLayer { alpha = 1f - predictiveBackProgress }` to the `Scaffold` or main container.

## Verification Plan

### Manual Verification
1. **Metric Detail to Dashboard**: Swipe back from a metric detail screen. Verify it soft-fades out and the "Today" icon in the nav bar highlights as you swipe.
2. **Settings to Dashboard**: Swipe back from the Settings screen. Verify it soft-fades out and the "Settings" icon fades while "Today" highlights.
3. **Summary to Dashboard**: Swipe back from the Summary screen. Verify the same soft-fade behavior.
4. **History to Camera/Dashboard**: Swipe back from the History screen overlay. Verify it now supports predictive back swiping with a soft fade.
5. **Universal Consistency**: Ensure the fade duration and feel are identical across all transitions.
