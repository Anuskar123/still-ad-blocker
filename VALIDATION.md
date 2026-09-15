# Validation

Validated on 15 September 2026 using JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3 and compile SDK 35.

Command: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

- Debug APK: built successfully.
- Pure Kotlin unit tests: 16 passed, zero failures, zero skipped.
- Android lint: zero errors, six advisory warnings about target SDK 34 and newer dependency versions.
- One service-level `ForegroundServicePermission` suppression is documented in the manifest. The static detector models exact-alarm permissions but does not recognize the documented VPN eligibility path for `systemExempted`. VPN consent is requested before starting this service. Unrelated alarm permissions are not added.
- No device or emulator was connected. VPN routing, service lifecycle, Android permission prompts, notification interaction, visual appearance, accessibility and animation behavior have not been verified on a running Android device.

Packet tests cover a published-style IPv4 checksum vector, odd-byte checksum math, valid query parsing, A and AAAA sinkhole answers, non-address NODATA, SERVFAIL, independent response checksum sums, truncated input, fragment rejection, invalid UDP checksum rejection, compressed-name cycles, valid name compression, subdomain matching boundaries, source/destination reversal and nonzero incoming UDP checksum acceptance.

This validation does not establish production readiness or universal ad-blocking coverage. See README.md for supported traffic and the device checklist.
