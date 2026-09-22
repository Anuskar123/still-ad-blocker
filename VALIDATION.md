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
- The earlier live FilterDownloadTest stalled, but the corrected 2.1 build was retested on the same phone. Multi Light and Threat domains refreshed successfully, followed by a separate successful Fake and scam sites download. Daily background completion and its success notification have not been established on this phone.
- Final app was launched with protection off. Existing settings were retained.

## Release boundaries

No Play Console upload or public 2.0 publication was performed. Store text, artwork, screenshots and a local privacy-policy HTML page are prepared. Public policy hosting, developer contact details, Play declarations, rating questionnaire, required testing tracks and store review remain. See RELEASE.md and store/STORE_LISTING.md. Back up the private signing/ directory securely; it is excluded from Git and distributable files.

## Version 2.1, 22 September 2026

Added eight optional HaGeZi feeds, mutually exclusive Multi choices, catalogue search, selected/downloaded counts and an offline domain-rule checker. Existing saved subscription identifiers remain compatible. All eight downloaded wildcard feed snapshots passed the existing bounded parser and domain-boundary checks. 53 unit tests passed with zero failures and zero skipped. Debug and signed R8 release APKs built; release lint has zero errors.

Installed the debug-signed 2.1 update on the connected Infinix X6878 without uninstalling the app. Android reported versionName 2.1, versionCode 7 and targetSdk 36. The catalogue showed eight choices and retained the existing Multi Light and Threat selections. A manual refresh completed on the phone, although the two sequential downloads took about 75 seconds. The app now identifies the active list, shows completed and total counts, explains that large mobile downloads may exceed one minute, allows 30 seconds between received bytes, and reports specific connection, timeout and TLS errors while preserving the last valid copy. A separate new-list test downloaded Fake and scam sites successfully at 17,257 rules and 331,603 bytes. The original Multi Light and Threat selections were restored after that test, and the Fake and scam sites copy remains cached but disabled. The local domain checker reported `ads.google.com: Blocked by the built-in Strict rules.` Automatic background completion and its notification remain unverified. Version 2.1 artifacts are published in the GitHub v2.1 release.
