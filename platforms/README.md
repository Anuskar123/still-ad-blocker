# Still on your devices

This adds DNS companion tools alongside the existing Android app. It does not turn the Android APK into an iPhone or desktop app, and the companions do not have Android feature parity.

## Downloads

- [Complete cross-device package](https://github.com/Anuskar123/still-ad-blocker/releases/download/companions-v0.1.0/Still-Cross-Device.zip)
- [Windows Intel/AMD x64](https://github.com/Anuskar123/still-ad-blocker/releases/download/companions-v0.1.0/Still-Windows-x64.zip)
- [Windows ARM64](https://github.com/Anuskar123/still-ad-blocker/releases/download/companions-v0.1.0/Still-Windows-arm64.zip)
- [iPhone, iPad and Mac DNS profile](https://github.com/Anuskar123/still-ad-blocker/releases/download/companions-v0.1.0/Still-Apple-DNS.mobileconfig)
- [Release notes and checksums](https://github.com/Anuskar123/still-ad-blocker/releases/tag/companions-v0.1.0)

This is a preview release. Read the validation column below before installing.

| Device | Deliverable | Filtering | Validation |
| --- | --- | --- | --- |
| Android 8.0+ phones and tablets | Existing Still 2.1 APK | Local rules and optional downloaded lists | Existing Android build, not modified in this change |
| Windows 10/11 x64 | `Still-Windows-x64.zip` | AdGuard Public DNS on selected adapters | Built; read-only integration and simulated change/restore tests passed |
| Windows 11 ARM64 | `Still-Windows-arm64.zip` | Same Windows companion | Cross-compiled; not run on ARM hardware |
| iPhone/iPad, iOS/iPadOS 14+ | `Still-Apple-DNS.mobileconfig` | AdGuard DNS over HTTPS | Generated and structurally validated; device installation not tested |
| Mac, macOS 11+ | Same DNS profile | AdGuard DNS over HTTPS | Generated and structurally validated; device installation not tested |
| iPhone/iPad 15+ and Mac 12+ | SwiftUI native companion source | Saves an encrypted DNS configuration through Apple's API | Source provided; not built or run on Apple hardware |

The Windows and Apple companions use AdGuard's own public blocklists. They do not share Android's custom allow/block rules, selected HaGeZi feeds, request counters or private browser. Still is independent of AdGuard. DNS filtering does not reliably block YouTube video ads or advertisements served from content domains. It also does not remove empty spaces in pages.

## Windows

1. Extract the ZIP for your processor. Most Intel and AMD PCs use x64. Snapdragon Windows PCs use ARM64. A separate .NET installation is not required.
2. Open `Still.exe`. Windows requests administrator access because changing adapter DNS requires it. This development build has no code-signing certificate.
3. Select your connected Wi-Fi or Ethernet adapter, read the provider disclosure, and choose **Enable filtering DNS**.
4. Repeat for other adapters you actually use. Changing networks on the same adapter retains the setting; a new adapter needs its own configuration.
5. To undo the change, select that adapter and choose **Restore previous DNS**. Do this before removing Still.

The app configures IPv4 and IPv6 DNS when those address families are available. It does not enable disabled protocols. Original automatic/static DNS modes are saved under `%LOCALAPPDATA%\Still\DnsBackup` for the Windows account that ran the app. Do not delete these backups until restoration succeeds. If you ran Still using another administrator account, restore using that same account.

Closing the window or restarting the PC does not restore DNS. The app does not need to keep running. Restore puts back the saved configuration, including overwriting any DNS changes you made manually after enabling Still. If an adapter is removed, reconnect it to restore its saved settings. Managed networks and private internal names may require their original DNS.

This version does not configure Windows DNS encryption. Browsers with their own secure DNS, another adapter, a VPN, or network policy can bypass the setting. The status is a configuration check, not a live blocked-ad count.

If the UI cannot start, open an administrator PowerShell window from the extracted folder. Run `powershell -NoProfile -ExecutionPolicy Bypass -File ./DnsControl.ps1 -Action Status`, then `powershell -NoProfile -ExecutionPolicy Bypass -File ./DnsControl.ps1 -Action Restore -AdapterId '<Id from Status>'`. These commands allow this supplied script for that process only, without changing the machine's saved execution policy. They use the same saved backups and do not reset every adapter indiscriminately.

## iPhone and iPad without a Mac

1. Transfer `Still-Apple-DNS.mobileconfig` to your device using a method you trust, such as Files or a private file transfer. Open the file on the device.
2. Open Settings and select the downloaded profile. Depending on the OS version, use **General > VPN & Device Management**.
3. Review the profile. It contains one encrypted DNS payload, with no certificate, VPN tunnel or device-management enrolment. It is unsigned and is labelled as such by Apple.
4. Install it, then check the DNS selection in system settings. Test ordinary browsing on Wi-Fi and cellular.
5. To remove it, open **General > VPN & Device Management**, select **Still - filtering DNS**, and remove the profile.

The profile can be manually installed without a developer account. It is a system DNS configuration, not an iPhone application. No Android APK is installed. Some managed devices may prohibit installing profiles. App-specific DNS and VPNs may override the resolver.

## Mac without compiling

Open the same `.mobileconfig` file, then review and install the downloaded profile in System Settings. Search settings for **Profiles** or **Device Management** if necessary; menu locations vary across macOS versions. Remove the Still profile there to undo it. No Mac app or background process is installed by this route.

## Building the native Apple apps later

The source is in `apple/StillApp.swift`. The native apps let the user save, inspect and remove their app-owned DNS configuration. The user must select the saved configuration in system settings. The app reports the actual `NEDNSSettingsManager.isEnabled` value instead of assuming that saving enables protection.

On a Mac with Xcode and XcodeGen installed, run `sh platforms/apple/build.sh check` for unsigned compiler checks, or `sh platforms/apple/build.sh open` to generate and open the Xcode project. Select your signing team, use bundle IDs owned by that team, and configure the Network Extensions `dns-settings` entitlement for installation/distribution. Distribution requires appropriate Apple signing and provisioning. Simulator compilation alone does not validate DNS filtering on a device.

Use either the manually installed profile or the native companion configuration. The native app cannot inspect or remove the separately installed profile. No `.ipa` or signed Mac `.app` is included because no Apple build environment is available here.

## Privacy

Windows and Apple queries handled by the configured resolver go to AdGuard Public DNS. Apple uses HTTPS; this Windows companion does not configure encryption. AdGuard processes queries under its [DNS privacy policy](https://adguard-dns.io/en/privacy.html). These companions add no analytics and store no DNS query history. Windows stores adapter identifiers and previous DNS server settings locally for restoration. Encrypted DNS does not hide your IP address or encrypt all network traffic.

## Build and validation

With a .NET 8 SDK installed, run `powershell -NoProfile -ExecutionPolicy Bypass -File platforms/windows/build.ps1`. This creates self-contained x64 and ARM64 ZIPs under `dist/`. The build does not enable DNS filtering. Use `-DotnetPath` to point to a locally installed SDK executable.

Run `powershell -NoProfile -ExecutionPolicy Bypass -File platforms/windows/Test-DnsControl.ps1` for isolated DNS mutation/rollback tests. All network-changing commands are mocked, so this does not reconfigure the host. `python platforms/test_profiles.py` verifies the Apple profile and provider consistency. `python platforms/apple/generate_profile.py` regenerates the profile deterministically.

Apple documents its [DNS configuration payload](https://developer.apple.com/documentation/devicemanagement/dnssettings) and [DNS settings manager](https://developer.apple.com/documentation/networkextension/nednssettingsmanager). Resolver addresses are from [AdGuard Public DNS](https://adguard-dns.io/en/public-dns.html).
