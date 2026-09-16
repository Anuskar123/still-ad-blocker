# Still 2.0 validation

Validated 16 September 2026. Version code 6; compile and target API 36.

## Build and automated checks

- Gradle 8.11.1, AGP 8.10.1, JDK 17.
- Debug APK, signed R8 release APK, signed AAB and isolated releaseCheck APK built successfully.
- 50 unit tests passed, zero failures and zero skipped, including live-source filter snapshots and four lifetime-counter tests.
- Release lint: zero errors, 22 advisory warnings, primarily newer dependencies and Kotlin style suggestions. The base adaptive icon warning has an API 33 monochrome resource variant.
- APK v2 signature verified using apksigner; RSA 2048 release identity.
- APK zipalign 16 KB check passed. All shipped native ELF load segments use 16384-byte alignment.
- AAB validated with Google's bundletool. Jarsigner verifies its signature with self-signed certificate and streaming ZIP-order advisory messages; bundletool accepts the bundle.
- git diff --check passed.

## Infinix X6878, Android API 36

- Updated installed debug-signed 1.4 to debug-signed 2.0 without uninstalling the existing app.
- Confirmed versionName 2.0, versionCode 6, targetSdk 36.
- Separately installed releaseCheck using release shrinking and signing under a test package. Completed all three onboarding pages, opened About, bundled privacy policy and OSS licences, and restarted directly to dashboard. Removed only this temporary test package afterward.
- Confirmed full Settings screen and captured app screenshots.
- Added Still DNS Quick Settings tile. Toggled on and observed the connected dashboard and foreground VPN service. Toggled off and observed the disconnected dashboard with no VPN service remaining.
- Lifetime totals persisted across force-stop/relaunch. Observed stored blocked=2, queries=28 immediately after a restart; subsequent live traffic continued increasing totals.
- PrivateBrowserTest: both instrumentation tests passed in 8.888 seconds.
- Live FilterDownloadTest stalled without saving a list and was stopped after several minutes. This test did not pass. Daily background download completion and success notification have not been established on this phone's connection. Desktop parser/cache tests with downloaded feed snapshots passed separately.
- Final app was launched with protection off. Existing settings were retained.

## Release boundaries

No Play Console upload or public 2.0 publication was performed. Store text, artwork, screenshots and a local privacy-policy HTML page are prepared. Public policy hosting, developer contact details, Play declarations, rating questionnaire, required testing tracks and store review remain. See RELEASE.md and store/STORE_LISTING.md. Back up the private signing/ directory securely; it is excluded from Git and distributable files.
