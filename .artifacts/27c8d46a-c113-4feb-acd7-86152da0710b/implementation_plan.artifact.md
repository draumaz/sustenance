# Fix Debug Build Icon

The debug builds are not using the orange debug-specific icon. The current implementation attempts to override the icon in the debug `AndroidManifest.xml`, but it is better to use the standard Android resource merging mechanism by providing an `ic_launcher` resource in the `debug` source set.

## Proposed Changes

### [app]

#### [MODIFY] [colors.xml](file:///home/emma/android-repos/sustenance/app/src/debug/res/values/colors.xml)
- Override `ic_launcher_background` with the orange color (`#F57C00`) to ensure the adaptive icon background is orange in debug builds.

#### [NEW] [ic_launcher.xml](file:///home/emma/android-repos/sustenance/app/src/debug/res/mipmap-anydpi/ic_launcher.xml)
- Create `ic_launcher.xml` in the `debug` source set that uses the orange background.

#### [NEW] [ic_launcher_round.xml](file:///home/emma/android-repos/sustenance/app/src/debug/res/mipmap-anydpi/ic_launcher_round.xml)
- Create `ic_launcher_round.xml` in the `debug` source set that uses the orange background.

#### [MODIFY] [AndroidManifest.xml](file:///home/emma/android-repos/sustenance/app/src/debug/AndroidManifest.xml)
- Remove the `android:icon` and `android:roundIcon` overrides as they will now be handled by resource merging.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:mergeDebugResources` to verify resources are merged correctly (can't easily verify the final APK content here but we can check for build errors).
- Run `./gradlew :app:assembleDebug` to ensure the project still builds.

### Manual Verification
- The user can verify that the app icon is orange when running the debug build.
