# Revamp Summary to Insights Page

Revamp the Summary page into an Insights page that provides feedback on nutrient goals, highlighting which specific food(s) contributed to exceeding a goal or congratulating the user if goals are met.

## Proposed Changes

### Data & Logic Layer

#### [MODIFY] [WeeklyStat.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/data/WeeklyStat.kt)
- Add an `insight: String?` field to the `WeeklyStat` data class.

#### [MODIFY] [HealthConnectManager.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/data/HealthConnectManager.kt)
- Make the `read` method `internal` or add a public helper `readTodayNutritionRecords()` to allow fetching individual food logs for insight generation.

#### [MODIFY] [SummaryViewModel.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/summary/SummaryViewModel.kt) (Rename to `InsightsViewModel.kt` eventually)
- In the `refresh` method, fetch all `NutritionRecord`s for today.
- In the `state` flow, implement logic to generate insights for each nutrient metric:
    - If `todayValue > goal`: Identify the `NutritionRecord` with the highest contribution to that specific nutrient and format an insight string (e.g., "You're over your goal. **[Food Name]** contributed **[Value]** [Unit] today.").
    - If `todayValue <= goal` (and goal > 0): Set insight to "Good job! You've stayed within your goal."
- Rename class and file to `InsightsViewModel`.

### UI Layer

#### [MODIFY] [strings.xml](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/res/values/strings.xml)
- Rename `summary_title` to "Insights".
- Rename `summary_subtitle` to "Your daily feedback".
- Add new strings for insight templates (e.g., `insight_over_goal`, `insight_good_job`).

#### [MODIFY] [SummaryScreen.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/summary/SummaryScreen.kt) (Rename to `InsightsScreen.kt`)
- Update UI to display the `insight` text within the `InsightCard`.
- Update header text and icons to reflect the "Insights" theme.
- Rename class and file to `InsightsScreen`.

#### [MODIFY] [SustenanceRoot.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/io/github/draumaz/sustenance/ui/SustenanceRoot.kt)
- Update `Dest.SUMMARY` to `Dest.INSIGHTS`.
- Update navigation routes and references.

## Verification Plan

### Automated Tests
- N/A (Unit tests for insight logic could be added if test infrastructure exists).

### Manual Verification
1. Open the app and navigate to the new "Insights" page (formerly Summary).
2. Verify that the title and subtitle have changed.
3. Log a food item that exceeds a nutrient goal (e.g., high saturated fat).
4. Verify that the Insight card for that nutrient now shows which food sent you over.
5. Verify that nutrients within the goal show a "Good job!" message.
