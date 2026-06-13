{
  description = "Heartwood: An open-source Material themed Health Connect dashboard";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    systems.url = "github:nix-systems/default";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      ...
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        android = pkgs.androidenv.composeAndroidPackages {
          # Core SDK pieces (see docs/REPRODUCIBLE.md)
          platformVersions = [ "36" ];
          buildToolsVersions = [ "36.0.0" ];
          includeEmulator = false;
          includeSystemImages = false;
          includeNDK = false;
        };

        sdkRoot = "${android.androidsdk}/libexec/android-sdk";
        aapt2 = "${sdkRoot}/build-tools/36.0.0/aapt2";
      in
      {
        devShells.default = pkgs.mkShell {
          nativeBuildInputs = [
            pkgs.jdk17
            pkgs.gradle
            android.androidsdk
          ];

          JAVA_HOME = "${pkgs.jdk17}";
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          # Gradle's bundled aapt2 isn't built for NixOS; use the SDK's.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2}";

          shellHook = ''
            echo "heartwood · jdk17 + android sdk 36 · ./gradlew :app:assembleRelease"
          '';
        };

        # Reproducible build. Gradle still resolves deps from the network, so this runs in a
        # relaxed sandbox; for a fully sealed build, vendor deps via gradle2nix and feed them in
        # (app/gradle.lockfile pins the versions).
        packages.default = pkgs.stdenv.mkDerivation {
          pname = "heartwood";
          version = "1.0.0";
          src = ./.;
          __noChroot = true;
          nativeBuildInputs = [ pkgs.jdk17 pkgs.gradle android.androidsdk ];
          JAVA_HOME = "${pkgs.jdk17}";
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          buildPhase = ''
            export GRADLE_USER_HOME=$TMPDIR/gradle
            ./gradlew --no-daemon \
              -Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2} \
              :app:assembleRelease
          '';
          installPhase = ''
            mkdir -p $out
            cp app/build/outputs/apk/release/*.apk $out/
          '';
        };

        formatter = pkgs.nixpkgs-fmt;
      }
    );
}
