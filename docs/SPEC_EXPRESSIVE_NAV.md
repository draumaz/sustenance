# SPEC: Material 3 Expressive Pill Navigation

This specification defines a high-fidelity, interactive bottom navigation bar for Jetpack Compose. It features a glass-morphic pill design, fluid spring animations, a "melting" dynamic item for detail screens, and full synchronization with Android's **Predictive Back** gesture.

## 1. Core State & Shapes

### PredictiveBackState
Tracks the real-time progress of a system back-swipe.
```kotlin
@Stable
class PredictiveBackState {
    var progress by mutableFloatStateOf(0f)
    var isSwipeActive by mutableStateOf(false)
}
```

### ScallopedPillShape
A custom `Shape` that provides a standard pill or a wavy "scalloped" edge.
*   **Math**: Uses `sin` wave displacements along the normal vectors of the pill's path.
*   **Usage**: Clip the Navigation Bar `Surface` with this.

## 2. ExpressiveNavigationBar Component

The main container should be a `Surface` wrapped in a `Box` with `navigationBarsPadding()`.

### Key Features:
1.  **Glass-morphism**: Use `surfaceContainerHighest` with ~95% alpha and a `Blur` if supported.
2.  **Layout**: Horizontal `Row` with small spacing (e.g., `4.dp`).
3.  **Melting Logic**: Use `AnimatedContent` with `SizeTransform(clip = false)` to transition between standard items and a "Detail" item.
4.  **Selection Overrides**: Calculate `selectionAlphaOverride` for each item:
    *   If `isSwipeActive` AND `previousBackStackEntry` matches item: `alpha = progress`.
    *   If `isSwipeActive` AND `currentDestination` matches item: `alpha = 1f - progress`.
    *   Otherwise: `null` (use internal animated selection).

## 3. ExpressiveNavItem Component

An individual interactive pill.

### Behavior:
*   **Scale Animation**: Use `Animatable` to scale down (e.g., `0.88f`) on `isPressed` using a stiff spring.
*   **Label Visibility**: Show the `Text` label only when `selectionAlpha > 0.8f`.
*   **Internal Layout**: Use `animateContentSize` on the item container to smoothly expand/contract when the label appears.
*   **Haptics**: Perform `TextHandleMove` on click and `LongPress` on a 2-second hold.

## 4. Interaction Patterns

### Predictive Back Sync
Navigation highlighting transitions between tabs in real-time as the user swipes, synchronized via `PredictiveBackState`.

### Scroll-to-Hide
The navigation bar hides when scrolling down and reveals when scrolling up.
*   **Implementation**: Use `NestedScrollConnection` to update a `bottomBarOffsetHeightPx` state based on `onPreScroll` deltas.
*   **Animation**: Drive the visual offset with `animateIntAsState` using a medium spring for a fluid, physical feel.

## 5. Integration Contract

### Root Navigation
1.  Initialize one `PredictiveBackState` at the root.
2.  Initialize `bottomBarOffsetHeightPx` and a `NestedScrollConnection` to track scroll deltas.
3.  Apply `nestedScroll` to the main container.
4.  Wrap `ExpressiveNavigationBar` in a `Box` with an `offset` driven by the animated scroll offset.
5.  Pass `PredictiveBackState` to `ExpressiveNavigationBar` and every screen destination.

### Screen Implementation
Every screen must synchronize its visual state with the gesture:

1.  **PredictiveBackHandler**:
    ```kotlin
    PredictiveBackHandler(enabled = true) { progress ->
        pbState.isSwipeActive = true
        try {
            progress.collect { event -> pbState.progress = event.progress }
            pbState.isSwipeActive = false
            pbState.progress = 0f
            onBack()
        } catch (e: Exception) {
            pbState.isSwipeActive = false
            pbState.progress = 0f
        }
    }
    ```
2.  **Visual Transformation**: Wrap screen content in a `Box` with `graphicsLayer`:
    *   `scale = 1f - (progress * 0.08f)`
    *   `translationX = progress * 400f`
    *   `alpha = 1f - (progress * 0.2f)`
    *   `clip = true`, `shape = RoundedCornerShape(progress * 28.dp)`

## 5. Visual Constants
*   **Item Height**: `56.dp`
*   **Pill Corner Radius**: `CircleShape`
*   **Springs**: `DampingRatioNoBouncy`, `StiffnessMedium` (Transitions); `StiffnessHigh` (Press scale).
