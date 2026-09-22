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
## Platform companions, 22 September 2026

Added a separate Windows DNS configuration companion and Apple SwiftUI companion source/profile. The Android implementation was not modified. Windows saves per-adapter static/automatic settings for the available IPv4/IPv6 families, applies AdGuard Public DNS, and restores its saved settings. It does not configure encrypted DNS or use the Android filtering engine.

Windows builds compile without warnings or errors. Read-only integration inspected four physical adapter records on this host and rendered the WinForms interface. This caught and fixed unavailable DNS-family records on absent adapters. Isolated tests cover backup preservation, IPv4/IPv6 configuration, DHCP/static restoration, repeated enable, partial failure rollback, persistent failure recovery backups, disconnected adapters, invalid identifiers and IPv4-only adapters. No host DNS settings were changed during these checks. End-to-end enable/restore on real adapters and ARM64 execution remain unverified.

The backend tests also pass under Windows PowerShell 5.1, which the executable uses. Android package metadata was rechecked before bundling: both existing APKs are `dev.still.dns`, version 2.1/code 7, minimum API 26 and target API 36, with arm64-v8a, armeabi-v7a, x86 and x86_64 libraries. Both APK signatures pass `apksigner verify`. No new Android runtime test was performed for the companion-only changes.

The generated Apple profile is a removable DNS-over-HTTPS configuration with one DNS payload and no certificates or device-management enrolment. Structural checks verify generation consistency, provider addresses, removability and the native app's DNS-only entitlement. Neither the profile nor the native apps were tested on Apple devices. No signed IPA or macOS application is provided. The SwiftUI source and XcodeGen configuration require a Mac with Xcode and appropriate signing for device delivery.

A direct HTTPS DNS smoke check against the configured public resolver returned ordinary A records for `example.com` and a blocked `0.0.0.0` answer for `doubleclick.net`. This verifies those two upstream requests only; it does not verify system DNS activation or device-wide ad removal.
