# Implementation Plan - Reduce Food View Graph to 7 Days

The user wants the Food view line graph (and other daily total detail views) to show seven days instead of the current fourteen days (which displays eight labels).

## Proposed Changes

### [Component Name] - HealthConnectManager

#### [MODIFY] [HealthConnectManager.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/data/HealthConnectManager.kt)
- Update `readDetail` to use 7 days instead of 14 for `DAILY_TOTAL` metrics.
- Update the statistics list to show a "7-day total" instead of a "14-day total".

### [Component Name] - Resources

#### [MODIFY] [strings.xml](file:///home/emma/android-repos/sustenance/app/src/main/res/values/strings.xml)
- Add `seven_day_total` string resource.

#### [MODIFY] [strings.xml](file:///home/emma/android-repos/sustenance/app/src/main/res/values-ru/strings.xml)
- Add `seven_day_total` string resource in Russian.

### [Component Name] - Insights

#### [MODIFY] [InsightsViewModel.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/summary/InsightsViewModel.kt)
- Update `refresh` to fetch 7 days instead of 14 for consistency.

## Verification Plan

### Automated Tests
- Run existing tests to ensure no regressions in data fetching.

### Manual Verification
- Deploy the app.
- Navigate to the Food detail view.
- Verify the graph now shows 7 labels (days) instead of 8.
- Verify the statistics show "7-day total".
