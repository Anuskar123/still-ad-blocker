# Still 2.0 release

Version code 6, version name 2.0. Compile and target SDK 36; minimum SDK 26.

## Build

Use JDK 17, Android SDK Platform 36 and the included Gradle 8.11.1 wrapper:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:lintRelease
```

Signed APK: `app/build/outputs/apk/release/app-release.apk`.

Play upload bundle: `app/build/outputs/bundle/release/app-release.aab`.

R8 mapping: `app/build/outputs/mapping/release/mapping.txt`. Archive this with every release for crash deobfuscation.

## Signing material

`scripts/New-ReleaseKey.ps1` creates the release key once using cryptographically random credentials and restricts the Windows signing folder's access. It refuses to overwrite existing material. The key and credentials live in `signing/`, which is ignored by Git. Back up that entire folder to a secure location outside this computer. Do not include it in source archives or publish it. Protect the password file as carefully as the key.

Without local signing material, release output is unsigned; debug builds still work. Reuse the same signing identity for future direct APK updates. With Play App Signing, distinguish your upload key from the Play-managed app signing key; losing an upload key has a reset procedure and is not always permanent loss of update access.

## Updating the phone

A release key cannot overwrite a debug-signed app with the same application ID. Use the updated debug APK to preserve the installed debug app and its data. Do not uninstall the existing app solely to test a release without first deciding how to retain its settings. The signed release should be tested on a clean emulator or spare device, or installed after a deliberate migration.

For a non-destructive R8 smoke test on the same phone, build `:app:assembleReleaseCheck`. This uses release optimization and signing but a separate `dev.still.dns.releasecheck` application ID and label. It does not replace the installed app. It is a test artifact, not the Play upload. Remove that test package after verification.

## Behavior notes

- First launch shows onboarding; Skip or Get started marks it complete. A separate disclosure appears before first VPN activation.
- Lifetime DNS totals are separate from AppSettings to avoid stale preference snapshots overwriting them. SharedPreferences apply writes asynchronously, so sudden power loss can lose the latest pending writes. Session reset does not reset lifetime totals.
- Automatic updates are unique daily WorkManager jobs requiring connectivity. Scheduling is approximate. Disabling updates or all subscriptions cancels the job. Failed downloads keep valid cached lists and retry with backoff.
- The Quick Settings tile reflects the shared service state and opens the dashboard when consent is required. A locked device must be unlocked to toggle.
- GitHub source and HaGeZi links were verified. The privacy policy is bundled locally; publish `store/privacy-policy.html` before supplying a public policy URL to Play.

See `store/STORE_LISTING.md` for publication details still requiring the developer's Play Console account.
