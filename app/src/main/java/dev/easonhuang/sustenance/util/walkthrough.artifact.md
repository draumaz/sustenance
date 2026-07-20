# Walkthrough - Serving Size Gram Chip Fix

I have fixed the implementation of the serving size gram chip to ensure it correctly updates the "Total Preview" when the serving size is adjusted via the button selector or manual input.

## Changes Made

### UI Logic Improvements

#### [FoodReviewDialog.kt](file:///home/emma/android-repos/sustenance/app/src/main/java/dev/easonhuang/sustenance/ui/components/FoodReviewDialog.kt)

- **Sanitized State Initialization**: The `servingSize` state is now initialized with only the numeric part of the input (e.g., "150" instead of "150g"). This prevents the chip from displaying redundant units and ensures numeric operations are predictable.
- **Robust Parsing in `EditableNutrientChip`**: I added a filter that strips non-numeric characters before parsing. This ensures that even if a unit suffix (like "g" or "kcal") is present in the string value, the "Total Preview" calculation still succeeds.
- **Multiplier Safety**: Added a safety check to ensure the nutrient multiplier (`m`) defaults to `1.0` if the base serving size is zero, preventing `Infinity` or `NaN` values.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:assembleDebug` and verified the build completes successfully.

### Manual Verification Path
1.  Launched the app and triggered a `FoodReviewDialog`.
2.  The "GRAMS" chip now displays the numeric base value (e.g., `100 g`).
3.  Increasing the serving size via the `+` button correctly updates the small "Total: ..." preview on the "GRAMS" chip.
4.  Manually editing the value in the "GRAMS" chip correctly adjusts the multiplier for all other nutrient chips.

> [!TIP]
> This fix makes the `EditableNutrientChip` reusable for other metrics that might accidentally contain unit strings in their state.
