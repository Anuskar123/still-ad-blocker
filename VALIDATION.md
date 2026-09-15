# Validation

## Version 1.4

- Final debug build and test APK build passed. Android lint completed with zero errors and ten dependency/target-SDK advisory warnings. Still 1.4 (versionCode 5) was installed successfully on the connected phone.
- All three on-device instrumentation tests passed: two private-session cleanup tests and one live filter-download/cache test. These complement the 46 passing JVM tests below.
- Added a private WebView browser and downloadable DNS filter controls. The app is independent of AdGuard and HaGeZi. AdGuard Content Blocker 2.8.0 was identified on the connected phone; its onboarding required a supported browser. The design comparison also used the publishers' documentation and public repositories.
- JVM tests: 46 passed, no failures or skipped tests with `STILL_FILTER_FIXTURES` pointing to the downloaded validation snapshots. These include URL handling, rejection of non-web schemes, private-session log suppression including late replies, filter parsing, allow precedence, persistence, partial/failed updates and cache recovery.
- Published-source snapshot checks parsed 36,154 Multi LIGHT rules (version 2026.0915.0823.09) and 183,052 TIF Mini rules (version 2026.0915.0757.07). YouTube content-domain checks passed for these snapshots. Counts and coverage will change with future updates; this does not prove video playback or video-ad removal.
- Device privacy checks used Android System WebView 153.0.8010.36 on the connected phone. Close and erase, and backgrounding the activity, were tested with real WebView cookies, localStorage, sessionStorage and IndexedDB. Reopening started with empty storage; the screenshot-protection window flag was also checked.
- The device filter-download test downloads both current feeds into an isolated test cache, validates them and reloads the saved copies without another network request. It leaves the user's selected filters and settings unchanged.
- Reproduce unit/build checks with `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest`. Run the device tests with the debug app and test APK installed using `adb shell am instrument -w -r dev.still.dns.test/androidx.test.runner.AndroidJUnitRunner`. The device download test requires internet access; the optional published-source JVM test is skipped if `STILL_FILTER_FIXTURES` is absent.
- Still cannot clear other browsers' data, prevent websites/networks from recording visits, or intercept every browser's encrypted DNS. Forced-stop cleanup occurs before the next private session. Broader testing across Android/WebView versions, keyboard behavior, playback, service-worker scenarios, forced stops and all browsers remains outstanding.

## Version 1.2

Validated on 15 September 2026 using the bundled JDK 17 and Gradle 8.9.

- Debug build and unit tests passed. All 31 tests passed: 16 packet parser, 5 upstream resolver, 6 filtering policy and 4 protection-state tests.
- Android lint completed with zero errors and six existing dependency/target-SDK advisory warnings.
- New tests verify increasing protection coverage, allow-rule priority, subdomain boundaries, custom rules, domain validation, YouTube content domains remaining unblocked, bounded unique history, history disabled/cleared behavior, and DNS health recovery after failure.
- Version code is 3 and version name is 1.2. Existing settings default to Basic with domain logging disabled.
- The new Compose controls, preference persistence and live service reload still require device validation. Unit tests verify policy and state behavior, not playback, visual layout or end-to-end device networking. The device observations below refer to version 1.1 only.

## Previous version 1.1

Version 1.1 validated on 15 September 2026 using JBR 21, Gradle 8.9, Android Gradle Plugin 8.7.3 and compile SDK 35. Java and Kotlin compilation target remains 17.

Command: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

- Debug APK: built successfully.
- Unit tests: 21 passed (16 packet parser and 5 upstream resolver tests), zero failures or errors.
- Android lint: zero errors, six advisory warnings about target SDK 34 and newer dependency versions.
- One service-level `ForegroundServicePermission` suppression is documented in the manifest. The static detector models exact-alarm permissions but does not recognize the documented VPN eligibility path for `systemExempted`. VPN consent is requested before starting this service. Unrelated alarm permissions are not added.
- Installed version 1.1 (versionCode 2) successfully on the connected Infinix X6878. Observed the connected dashboard and foreground VPN service, then used Turn off and confirmed Android reported no remaining app service. The updated settings controls were present in the device UI hierarchy.
- Full routing, all settings persistence cases, notification disconnect, permission denial, accessibility and visual layout across devices still require broader device testing. Build and unit-test success alone do not verify those behaviors.

Packet tests cover a published-style IPv4 checksum vector, odd-byte checksum math, valid query parsing, A and AAAA sinkhole answers, non-address NODATA, SERVFAIL, independent response checksum sums, truncated input, fragment rejection, invalid UDP checksum rejection, compressed-name cycles, valid name compression, subdomain matching boundaries, source/destination reversal and nonzero incoming UDP checksum acceptance.

This validation does not establish production readiness or universal ad-blocking coverage. See README.md for supported traffic and the device checklist.
