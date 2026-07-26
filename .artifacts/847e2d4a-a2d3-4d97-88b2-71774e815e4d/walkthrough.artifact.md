# Walkthrough - LineChart UI Refinement

I have updated the `LineChart` component in `Charts.kt` to simplify the Y-axis and enhance the visualization of peak and low values.

## Changes Made

### [Charts.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/components/Charts.kt)

- **Simplified Y-Axis**: Removed the labels for the highest and lowest values.
- **Goal Visibility**: Added a Y-axis label for the user's goal value. This label is positioned dynamically to align with the dotted goal line.
- **Enhanced Highlights**:
    - The highest value dot (peak) is now tinted **red** (`#AB6161`).
    - The lowest value dot is now tinted **green** (`#709E73`).
    - The "Today" dot and selection highlights remain consistent with the overall theme.

## Verification

- **Code Review**: Verified that the peak and low detection logic correctly uses the new red and green colors.
- **Layout Alignment**: The goal label is calculated using the same coordinate system as the goal line, ensuring they are perfectly aligned.

> [!NOTE]
> The highest and lowest value dots are still highlighted with a larger touch target and concentric circles, but they no longer have their own labels on the Y-axis, keeping the chart cleaner.
