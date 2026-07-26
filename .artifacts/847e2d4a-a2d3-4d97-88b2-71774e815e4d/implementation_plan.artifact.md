# Implementation Plan - Modify LineChart Y-Axis and Highlights

The user wants to simplify the Y-axis of the `LineChart` and update the highlighting of specific data points. Specifically, the highest value label and lowest value label should be removed from the Y-axis, leaving only the goal value label (if a goal is set). Additionally, the dots on the chart for the highest and lowest values should be tinted red and green, respectively.

## Proposed Changes

### [Charts Component](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/components/Charts.kt)

#### [MODIFY] [Charts.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/components/Charts.kt)

- **Remove Y-axis labels for max and min**: Delete the `Text` composables that currently display `max` and `min` values.
- **Add Y-axis label for Goal**: Add a `Text` composable that displays the `goal` value at its corresponding Y position on the chart.
- **Update Peak and Low colors**:
    - Change `peakColor` to be reddish (for the highest value).
    - Change `lowColor` to be greenish (for the lowest value).
- **Ensure Dot Highlights**: Verify that dots for the highest day, lowest day, and today remain highlighted and use the updated colors.

## Verification Plan

### Manual Verification
- Since there are no automated previews, I will rely on code analysis to ensure the logic is correct.
- If possible, I will try to find a way to run the app or a test, but given the constraints, I will perform a very careful review of the changes.
- I will check if I can create a temporary `@Preview` in a new file to verify the UI changes if the environment supports it.

### Automated Tests
- I'll check if there are any existing tests for `LineChart` and run them if they exist.
