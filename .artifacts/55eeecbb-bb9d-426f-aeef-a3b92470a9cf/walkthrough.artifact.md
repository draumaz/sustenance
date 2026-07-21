# Walkthrough - Food Category Dynamic Sorting

I have updated the Food metric detail view to dynamically sort the "Morning", "Day", and "Night" sections. The section corresponding to the current time now appears at the top for today's logs, making it easier to view and log items for the current period.

## Changes Made

### Data Layer

#### [HealthConnectManager.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/data/HealthConnectManager.kt)

- Modified `readDetail` to calculate the current time category based on the system clock.
- Implemented a rotation logic for the category list:
    - If it's **Morning** (5 AM - 11:59 AM): `Morning, Day, Night`
    - If it's **Day** (12 PM - 5:59 PM): `Day, Night, Morning`
    - If it's **Night** (6 PM - 4:59 AM): `Night, Morning, Day`
- This dynamic sorting is applied only when viewing today's data (`dateOffset == 0`).

## Verification Results

### Automated Tests
- Ran `:app:assembleDebug` to ensure no syntax errors or build regressions. The build finished successfully.

### Manual Verification
- Verified the rotation logic:
    - The `defaultOrder` is `[Morning, Day, Night]`.
    - `defaultOrder.drop(idx) + defaultOrder.take(idx)` correctly rotates the list so that the item at `idx` becomes the first element.

```kotlin
// Logic check:
val defaultOrder = listOf("Morning", "Day", "Night")
// If Day (idx = 1):
// drop(1) -> ["Day", "Night"]
// take(1) -> ["Morning"]
// result -> ["Day", "Night", "Morning"]
```

> [!NOTE]
> This change improves UX by surfacing the most relevant food category for the current time of day at the top of the detail screen.
