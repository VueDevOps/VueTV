# Development and release maintenance

## Build

Use JDK 17 or 21, Android SDK platform 35 and the checked-in Gradle 8.11.1 wrapper. Set `ANDROID_HOME`, or add `sdk.dir` to ignored `local.properties`.

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

On Windows, use `./gradlew.bat`. CI runs the same tasks plus the six pure-Java checks and showcase JavaScript syntax check. Lint warnings are retained in the report; errors fail the build.

## JVM checks

These checks run without an emulator. From the repository root, using JDK 17+:

```sh
mkdir -p build/checks
javac -d build/checks app/src/main/java/je/vu/tvhome/TimedDiskCache.java app/src/main/java/je/vu/tvhome/MotionSpring.java app/src/main/java/je/vu/tvhome/AppOrder.java app/src/main/java/je/vu/tvhome/ArtworkColors.java app/src/main/java/je/vu/tvhome/AvatarStyle.java app/src/main/java/je/vu/tvhome/PickRotation.java app/src/main/java/je/vu/tvhome/TwitchSession.java tests/*.java
java -cp build/checks je.vu.tvhome.CacheCheck
java -cp build/checks je.vu.tvhome.MotionCheck
java -cp build/checks je.vu.tvhome.HomeFeaturesCheck
java -cp build/checks je.vu.tvhome.AvatarCheck
java -cp build/checks je.vu.tvhome.PickRotationCheck
java -cp build/checks je.vu.tvhome.TwitchSessionCheck
```

## Signed releases

The public APK uses a dedicated release key. Keep that keystore and its passwords backed up privately: future in-place updates must use the same key. Never commit them or upload them as release assets. An existing debug-signed development install must be removed before installing the public release; that removes local data.

To use your own signing key, create an ignored `signing.properties` at the repository root:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_PRIVATE_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_PRIVATE_KEY_PASSWORD
```

Use forward slashes for Windows paths. Build with `./gradlew :app:assembleRelease :app:lintRelease`. Without `signing.properties`, release output is unsigned. Debug builds remain independently debug-signed.

Every completed app update must be published to GitHub and accompanied by a dated website update. This is the project release workflow.

For each release:

1. Increment `versionCode` and `versionName` in `app/build.gradle`; update the changelog, setup/download filename references and showcase version.
2. Run build/lint and JVM checks. Exercise changed connection flows and remote navigation on a TV when available; state any unverified behavior in release notes.
3. Verify the APK with Android SDK `apksigner verify --verbose --print-certs`. Ensure the release manifest is not debuggable and the key matches the previous release.
4. Name the signed output `VueTV-VERSION.apk`; generate `SHA256SUMS.txt`. Publish those assets with the release tag pointing to the tested commit. Include install instructions and preview limitations.

5. Push the tested source and showcase update to `main`; publish the signed assets as a GitHub release from the same commit. Verify Android checks, GitHub Pages deployment, the website update and release downloads.

CI intentionally does not have the private release key and uploads only a development APK artifact. Publishing a signed release is a maintainer operation.

## Showcase hosting

The site is buildless; publish `showcase/dist/`. Relative asset links support the `/VueTV/` project path. `.github/workflows/pages.yml` deploys via GitHub Actions. In repository Settings / Pages, the publishing source must be **GitHub Actions**. The canonical URL is `https://vuedevops.github.io/VueTV/`.

Keep `twitch-setup.html` at the root and its hosted copy in `showcase/dist/` in sync. Local reference APKs, device logs, raw captures, tools, signing files and personal configuration are ignored and excluded from publication.
