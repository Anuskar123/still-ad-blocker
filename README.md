# Still

A Kotlin and Material 3 local DNS ad blocker for Android 8.0 and later. Target SDK is 34; compile SDK is 35. Includes dynamic light/dark colors, an animated power control, session statistics, permission handling, a foreground VPN service, protected upstream sockets and a pure Kotlin packet codec.

## Build

Open this folder in Android Studio. Use JDK 17, install Android SDK Platform 35, and let Gradle sync. The Gradle 8.9 wrapper is included with a pinned distribution checksum.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Routing correction

The requested `addRoute("0.0.0.0", 0)` would capture every IPv4 packet. A service that handles only DNS would discard ordinary TCP, HTTPS and non-DNS UDP traffic and break internet access. This implementation instead uses `addRoute("10.0.0.2", 32)`, retaining the requested address `10.0.0.2/24` and DNS server `10.0.0.2`. Replies reverse the original source/destination IPs and UDP ports. Full-device routing would require an additional userspace TCP/IP forwarding stack.

## Behavior and limits

- Four built-in domain rules, including their subdomains. Edit `AdBlockerService.BLOCKLIST` to extend them.
- IPv4 UDP DNS queries for blocked A records receive `0.0.0.0`; AAAA receives `::`; other record types receive NOERROR with no answers.
- Allowed requests use a protected, connected UDP socket to `8.8.8.8:53`, with a three-second timeout and response transaction/question validation. Failures receive SERVFAIL.
- Four upstream workers and a 128-request queue bound resource use. Overflow receives SERVFAIL.
- IPv4 and UDP checksums are validated and regenerated, including the UDP pseudo-header, odd lengths, carry folding and the computed-zero encoding of `0xffff`.
- Fragmented, invalid or unsupported packets are rejected. TCP DNS fallback and IPv6 DNS transport are not implemented. IPv6 ordinary traffic is allowed outside the tunnel.
- Private DNS, DNS-over-HTTPS, app-specific resolvers and cached answers may bypass filtering. This small list cannot block all ads, especially ads served from content domains.
- Filtering is local; permitted DNS names are sent unencrypted to Google DNS. This app is not an encrypted VPN and does not hide your IP address. The requested UI label "Secure" means the DNS filter is active only.
- "Ads Blocked" counts blocked queries, including retries and non-A queries, not distinct domains or confirmed removed ad impressions. Data Saved estimates 50 KB per blocked query. Counters survive activity recreation but reset when the process ends.
- Android allows one active VPN. Always-on and lockdown mode are not supported. A persistent notification includes a disconnect action; notification denial does not prevent foreground service operation.
- This is a working implementation for the stated DNS subset, not a production-certified universal ad blocker. Device validation is required before distribution.

## Device verification

1. Install and open the debug APK on Android 8 and Android 14 test devices. Tap the power control and accept Android's VPN consent. Check denied consent leaves the UI disconnected.
2. With Private DNS and browser secure DNS disabled on the test device, use a DNS client to query `ads.google.com A` against `10.0.0.2`. Expect `0.0.0.0` and an increased blocked count. Query `AAAA` and expect `::`.
3. Query `example.com` through the virtual DNS server. Expect an upstream answer and unchanged blocked count. Confirm an HTTPS page still loads.
4. Disconnect from the app and from the notification. Rotate and reopen the activity while connected. Start another VPN to check revocation updates the UI.
5. Change Wi-Fi/mobile connectivity and test resolver failure. Check both system themes, large font sizes and TalkBack labels.

## References

- [Android VPN guide](https://developer.android.com/develop/connectivity/vpn)
- [VpnService.Builder routing and address families](https://developer.android.com/reference/android/net/VpnService.Builder)
- [Foreground service types, including systemExempted VPN apps](https://developer.android.com/develop/background-work/services/fgs/service-types)

## Source files

The nine requested files are in `app/`, with all Kotlin files under `app/src/main/java/dev/still/dns/`. Root Gradle settings, a wrapper, the notification icon and unit tests complete the project. `COMPLETE_CODE.md` contains the source as separate Markdown code blocks.
