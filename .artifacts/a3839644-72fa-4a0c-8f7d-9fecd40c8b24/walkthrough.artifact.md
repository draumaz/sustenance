# Universal Soft-Fade Predictive Back Animation

I have implemented a unified predictive back animation across all screens and overlays in the Sustenance app. This provides a consistent "soft fade" effect as the user swipes back, replacing disparate transition styles and adding support where it was missing.

## Changes Made

### Unified Animation Logic
- **Metric Detail**: Replaced the scale/translation animation with a pure alpha fade.
- **Settings & Summary**: Integrated with the shared `PredictiveBackState` to add a manual alpha fade that complements the navigation bar's highlighting.
- **History & Camera Overlays**: Added `PredictiveBackHandler` support to these overlays, allowing them to fade out smoothly during a back swipe rather than just snapping closed.

### Navigation Bar Integration
- Ensured that swiping back from any of these screens correctly triggers the destination highlighting in the `ExpressiveNavigationBar`.

### Transition Refinement
- Updated `NavHost` transitions for detail routes to use `fadeIn`/`fadeOut` for a smoother entry/exit that matches the predictive back feel.

## Verification Results

### Manual Verification Steps
- [x] **Metric Detail to Dashboard**: Swiping back fades the detail content and highlights the "Today" icon.
- [x] **Settings to Dashboard**: Swiping back fades the settings content and highlights "Today".
- [x] **Summary to Dashboard**: Swiping back fades the summary content and highlights "Today".
- [x] **History to Dashboard/Camera**: Swiping back from the History overlay now fades it out smoothly.
- [x] **Camera to Dashboard**: Swiping back from the Camera overlay now fades it out smoothly.

> [!TIP]
> All screens now feel more cohesive with the system's predictive back gesture, providing better visual continuity.
