# Walkthrough - Reversed food item order

I have updated the Food detail view to show the most recently logged items at the top of their respective sections (Morning, Day, Night).

## Changes

### [HealthConnectManager.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/data/HealthConnectManager.kt)

In the `readDetail` function, I modified the sorting logic for today's food records:
- Changed `sortedBy { it.startTime }` to `sortedByDescending { it.startTime }`.

```diff
-                        section to recs.sortedBy { it.startTime }.map { r ->
+                        section to recs.sortedByDescending { it.startTime }.map { r ->
```

## Verification Results

### Automated Tests
- Ran `app:assembleDebug` to ensure the project still builds correctly. Result: **Success**.

### Manual Verification
- Verified that the grouping logic in `HealthConnectManager` still uses `listOf(Morning, Day, Night)`, ensuring the section order remains unchanged.
- Verified that within each group, the records are now sorted by `startTime` in descending order.
