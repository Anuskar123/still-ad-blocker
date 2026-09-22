# Still

## Other devices

[Download the cross-device package](https://github.com/Anuskar123/still-ad-blocker/releases/download/companions-v0.1.0/Still-Cross-Device.zip) or choose individual files from the [companion preview release](https://github.com/Anuskar123/still-ad-blocker/releases/tag/companions-v0.1.0).

The existing app below is Android-only. New Windows DNS companions, an Apple DNS configuration profile, and native Apple companion source are in [`platforms/`](platforms/README.md). Windows and Apple use AdGuard Public DNS and do not have Android's custom rules, filter subscriptions, private browser or counters. Apple app builds and Apple device validation remain outstanding. See the platform guide for exact compatibility and installation instructions.

## Version 2.1 filter expansion

The local 2.1 build offers eight optional HaGeZi lists: Multi Light, Pro Mini, Pro++ Mini, TIF Mini, fake sites, pop-up ad domains, Gambling Mini and adult domains. Pick one Multi tier plus optional categories. Existing selections are preserved, and selecting another Multi tier replaces only the previous tier. Stronger tiers can break services; content categories are not guaranteed parental controls.

The filter catalogue includes search and selected/downloaded counts. Check a domain locally to see custom-rule, built-in or subscription matches, including allow-rule precedence and missing-download status. No query is sent to a lookup service. Live DNS and private-browser filtering use the selected lists.

## Version 2.0

Still 2.0 adds first-launch onboarding, persistent lifetime DNS totals, a Quick Settings tile, automatic daily filter updates, full Settings and About screens, adaptive launcher artwork and signed, optimized release builds. See [release instructions](RELEASE.md), [validation](VALIDATION.md) and [privacy policy](app/src/main/assets/privacy-policy.txt). Live filter downloading is verified on an Infinix X6878. Large lists can take more than a minute on a mobile connection, so version 2.1 shows the current list and progress while downloading.

## Download for Android

[Update existing Still installations to 2.1](https://github.com/Anuskar123/still-ad-blocker/releases/download/v2.1/Still-2.1-debug-update.apk)

Open this link on your Android phone, download the APK, and open it to install or update Still. Android may ask you to allow installation from your browser. This is a debug-signed testing build for Android 8.0 or newer; private browsing also requires a compatible, updated Android System WebView.

[Download the optimized 2.1 release APK for a fresh installation](https://github.com/Anuskar123/still-ad-blocker/releases/download/v2.1/Still-2.1-release.apk). This uses a different signing key and cannot replace the earlier debug-signed app. Use the update APK above to retain your existing installation and data.

[Release notes and APK checksums](https://github.com/Anuskar123/still-ad-blocker/releases/tag/v2.0)

A Kotlin and Material 3 local DNS ad blocker for Android 8.0 and later. Target and compile SDK are 36. Includes light/dark colors, an animated power control, statistics, permission handling, a foreground VPN service, protected upstream sockets and a pure Kotlin packet codec.

## Build

Open this folder in Android Studio. Use JDK 17, install Android SDK Platform 36, and let Gradle sync. The Gradle 8.11.1 wrapper is included with a pinned distribution checksum.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Routing correction

The requested `addRoute("0.0.0.0", 0)` would capture every IPv4 packet. A service that handles only DNS would discard ordinary TCP, HTTPS and non-DNS UDP traffic and break internet access. This implementation instead uses `addRoute("10.0.0.2", 32)`, with interface address `10.0.0.1/32` and DNS server `10.0.0.2`. Keeping those addresses distinct avoids local delivery of DNS queries to the interface itself. Replies reverse the original source/destination IPs and UDP ports. Full-device routing would require an additional userspace TCP/IP forwarding stack.

## Behavior and limits

- Version 1.4 adds Still's own private browser with HTTPS navigation, DuckDuckGo search, back/forward/reload, request filtering and an explicit Close and erase action. It uses Android System WebView with website-data deletion support; unsupported WebView versions must be updated before browsing is allowed.
- Leaving the private-browser screen ends the session. Closing or backgrounding destroys the page and deletes cookies, website storage and network cache through AndroidX WebStorageCompat. A fresh launch deletes residual website data before loading any page, covering prior forced stops or crashes. Activity state and URLs are not saved. Forced process termination can prevent immediate cleanup until the next launch; this is not a forensic secure-erasure guarantee.
- Private sessions disable third-party cookies, file/content access, downloads, location, camera/microphone requests and saved autofill. The private screen blocks screenshots and recents previews. Still does not retain a private browsing history. DNS domain logging is paused for all device queries during the private session and late replies from that session are excluded. Previously recorded non-private domains remain in the optional DNS log.
- Private browsing does not hide your IP address or erase records held by websites, networks, keyboards or other apps. Private-browser filtering applies selected domain rules to WebView requests. It does not implement a full browser extension or cosmetic page filtering.
- Open another browser presents Android's app chooser once DNS protection is on. System-DNS filtering can apply to other browsers without installing a browser extension. Still cannot erase another browser's history, control its private mode or guarantee filtering when it uses its own encrypted resolver.
- Downloadable lists add HaGeZi Multi LIGHT (ads/trackers) and TIF Mini (threat domains). Both are optional and disabled by default. Enabled lists update approximately daily when automatic updates are on; manual refresh is also available. Downloads use the publisher's GitHub repository over HTTPS. Sources and GPL-3.0 licence links are available in the app. No third-party list contents or AdGuard code are bundled in the APK.
- Downloads are limited to 8 MiB and 300,000 rules per list, validated against the declared entry count and atomically replace the previous cached file only after validation. Failed updates preserve the last working copy. Saved files load before the VPN tunnel starts. Indexed suffix matching avoids a linear scan of every downloaded rule per query. Allow rules override downloaded lists as well as built-in/custom rules.
- Version 1.2 adds Basic (4 rules), Balanced (9 rules) and Strict (14 rules). Basic remains the default. Balanced adds selected advertising and analytics domains; Strict includes selected crash reporting and usage analytics. These are bundled starter lists, not a maintained malware database. Strict can affect app features.
- Manage up to 100 custom blocked domains and 100 allowed domains from the dashboard. Domain rules include subdomains; allow rules override built-in and custom blocks. Entries accept plain domain names and international domains, not URLs, wildcards or IP addresses. Rules and levels persist and apply to new DNS requests while connected. Existing DNS caches may delay visible changes.
- Connection health reports the last successful upstream lookup duration and recent failures. This measures DNS lookup time, not bandwidth or a complete internet-health test.
- Recent domains is opt-in and defaults to off. It holds at most 30 unique domains in process memory with Allowed, Blocked or Failed results. Users can allow a blocked domain, clear the history, or disable it to clear the list. The history is never written to disk. Manually saved allow/block rules remain until removed.
- Version 1.1 adds Turn on, Turn off and Cancel controls. Disconnect explicitly closes the tunnel even while Android is bound to the VPN service. Nonblocking tunnel I/O prevents idle reads from holding up shutdown.
- Settings save System, Light or Dark appearance, device colors on Android 12+, and the DNS provider. Turn protection off before changing the resolver, then reconnect. Settings also includes a session statistics reset.
- Built-in rules live in `FilterPolicy`. Custom rules can be edited in the app without rebuilding.
- IPv4 UDP DNS queries for blocked A records receive `0.0.0.0`; AAAA receives `::`; other record types receive NOERROR with no answers.
- Allowed requests use protected sockets to the provider selected in Settings (Google by default), with an 800 ms socket timeout and response transaction/question validation. A second address at the same provider is tried on failure. TCP fallback handles truncated UDP replies and failed UDP attempts. If all attempts fail, the client receives SERVFAIL; multiple attempts can take longer than one socket timeout.
- Four upstream workers and a 128-request queue bound resource use. Overflow receives SERVFAIL.
- IPv4 and UDP checksums are validated and regenerated, including the UDP pseudo-header, odd lengths, carry folding and the computed-zero encoding of `0xffff`.
- Fragmented, invalid or unsupported packets are rejected. Upstream TCP fallback is supported; client-to-tunnel TCP DNS and IPv6 DNS transport are not implemented. IPv6 ordinary traffic is allowed outside the tunnel.
- Private DNS, DNS-over-HTTPS, app-specific resolvers and cached answers may bypass filtering. This small list cannot block all ads, especially ads served from content domains.
- Filtering is local; permitted DNS names are sent unencrypted to the selected DNS provider. This app is not an encrypted VPN and does not hide your IP address. The status label says "Filtering active" when the local VPN is connected.
- "Blocked requests" counts blocked queries, including retries and non-A queries, not distinct domains or confirmed removed ad impressions. Data Saved estimates 50 KB per blocked query. Session counters reset when the process ends. Lifetime blocked/query totals are saved locally and survive restarts; uninstalling or clearing app storage removes them.
- Android allows one active VPN. Always-on and lockdown mode are not supported. A persistent notification includes a disconnect action; notification denial does not prevent foreground service operation.
- This is a working implementation for the stated DNS subset, not a production-certified universal ad blocker. Device validation is required before distribution.

## Device verification

1. Install and open the debug APK on Android 8 and Android 14 test devices. Tap the power control and accept Android's VPN consent. Check denied consent leaves the UI disconnected.
2. With Private DNS and browser secure DNS disabled on the test device, use a DNS client to query `ads.google.com A` against `10.0.0.2`. Expect `0.0.0.0` and an increased blocked count. Query `AAAA` and expect `::`.
3. Query `example.com` through the virtual DNS server. Expect an upstream answer and unchanged blocked count. Confirm an HTTPS page still loads.
4. Disconnect from the app and from the notification. Rotate and reopen the activity while connected. Start another VPN to check revocation updates the UI.
5. Change Wi-Fi/mobile connectivity and test resolver failure. Check both system themes, large font sizes and TalkBack labels.
6. Switch from Basic to Balanced while connected and query `google-analytics.com`. Add an allow rule for that domain and verify a fresh DNS query is forwarded. Remove the allow rule and verify blocking resumes. Confirm settings persist after reopening the app.
7. Add a custom block for `example.com` and query a subdomain. Add an allow rule for that subdomain and verify the exception takes priority. Remove both test rules afterward.
8. Enable Recent domains, generate queries, allow a blocked domain, clear the list and disable logging. Confirm the list stays empty with logging disabled. Check the DNS health message with an unreachable upstream.
9. Select a downloadable filter, update it, check its count and version, then restart the app offline to verify the saved copy loads. Disable a list and check that its rules no longer apply. Confirm failed updates keep the previous list.
10. Open Still private, browse an HTTPS site and close the session. Reopen and verify cookies and website storage are absent. Repeat using the Home button and by force-stopping/reopening the app. Confirm screenshots are blocked and private domains do not appear in the DNS log.

## References

- [Android VPN guide](https://developer.android.com/develop/connectivity/vpn)
- [VpnService.Builder routing and address families](https://developer.android.com/reference/android/net/VpnService.Builder)
- [Foreground service types, including systemExempted VPN apps](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [AdGuard Content Blocker architecture and browser limitations](https://adguard.com/kb/adguard-content-blocker/overview/)
- [HaGeZi DNS lists and source](https://github.com/hagezi/dns-blocklists)
- [HaGeZi list licence](https://github.com/hagezi/dns-blocklists/blob/main/LICENSE)
- [AndroidX website-data deletion API](https://developer.android.com/reference/androidx/webkit/WebStorageCompat)

## Source files

The nine requested files are in `app/`, with all Kotlin files under `app/src/main/java/dev/still/dns/`. Root Gradle settings, a wrapper, the notification icon and unit tests complete the project. `COMPLETE_CODE.md` contains the source as separate Markdown code blocks.
