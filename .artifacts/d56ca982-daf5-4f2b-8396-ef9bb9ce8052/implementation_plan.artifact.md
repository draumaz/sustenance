# Reverse logged food order in Food detail view

Reverse the display order of logged food items so that the most recently logged items appear at the top of the list. This involves reversing both the section order (Night → Day → Morning) and the item order within each section.

## Proposed Changes

### [Data Layer]

#### [MODIFY] [HealthConnectManager.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/data/HealthConnectManager.kt)

- Update `readDetail` to reverse the order of `todaySections` for `Metric.FOOD`.
- Change the grouping order from `listOf(Morning, Day, Night)` to `listOf(Night, Day, Morning)`.
- Change the internal sorting of records from `sortedBy { it.startTime }` to `sortedByDescending { it.startTime }`.

## Verification Plan

### Manual Verification
- Deploy the app to a device or emulator.
- Navigate to the **Food** detail screen.
- Verify that the sections are ordered with **Night** at the top (if data exists), followed by **Day**, then **Morning**.
- Verify that within each section, the most recently logged items are at the top.
