# Implementation Plan - Food Logging History

Implement a history view for logged food items, accessible from the Camera view's navigation pill. Users can view their past logs and quickly re-log them by opening the `FoodReviewDialog` with pre-filled data.

## User Review Required

> [!IMPORTANT]
> The history view will fetch data from Health Connect, filtered by records created by the Sustenance app. This ensures privacy and consistency with the rest of the app's data management.

## Proposed Changes

### 1. Resources & Strings

#### [MODIFY] [strings.xml](file:///home/emma/android-repos/sustenance/app/src/main/res/values/strings.xml)
- Add `history` string.
- Add `no_history` string.

### 2. Data Layer

#### [MODIFY] [Models.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/data/Models.kt)
- Add `HistoryItem` data class containing `FoodNutrients` and a timestamp.

#### [MODIFY] [HealthConnectManager.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/data/HealthConnectManager.kt)
- Implement `readHistory()` to query recent `NutritionRecord`s from Health Connect.
- Add a private helper method to convert `NutritionRecord` back to `FoodNutrients`.

### 3. UI Components

#### [MODIFY] [ExpressiveNav.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/components/ExpressiveNav.kt)
- Update `ExpressiveNavigationBar` to accept `onHistoryClick: () -> Unit`.
- Add the History button to the navigation pill in `isCameraMode`.
- Position it to the left of the "Analyze" button.

#### [NEW] [HistoryScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/history/HistoryScreen.kt)
- Create a new screen to display a list of `HistoryItem`s.
- Each item shows the food name, calories, time, and other nutrients.
- Provide a callback for when an item is selected.

### 4. Integration

#### [MODIFY] [SustenanceRoot.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/SustenanceRoot.kt)
- Add `isHistoryActive` state in `MainNav`.
- Pass `onHistoryClick` to `ExpressiveNavigationBar`.
- Render `HistoryScreen` as an overlay when `isHistoryActive` is true.
- When a history item is selected, set `pendingNutrients` to that item's nutrients and close the history view.

## Verification Plan

### Automated Tests
- N/A (Project currently focuses on manual UI verification).

### Manual Verification
1. Log a new food item.
2. Open Camera view.
3. Tap the "History" button in the navigation pill.
4. Verify that the newly logged item appears in the list with correct details.
5. Tap the history item and verify that `FoodReviewDialog` opens with pre-filled data.
6. Log the item again and verify it works without Gemini interaction.
