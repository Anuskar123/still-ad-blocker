"""Bundle existing verified Android APKs and built platform companions. Does not build or sign apps."""
from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"

START = """STILL - CROSS-DEVICE PACKAGE

Android: Open Android/Still-2.1-update.apk to update the existing debug-signed
Still installation without removing your data. For a fresh installation, use
Still-2.1-fresh-install.apk. These APKs have different signing keys; the fresh
installation APK cannot update the debug-signed app. Both are Android 8.0+.

Windows: Extract Windows/Still-Windows-x64.zip for Intel/AMD PCs, or
Still-Windows-arm64.zip for ARM64 Windows PCs. Keep the included files together
and run Still.exe. Select your connected adapter and enable filtering DNS.
Use Restore previous DNS before removing the app. DNS settings persist when
the app is closed. These development executables are not code-signed.

iPhone/iPad/Mac: Transfer Apple/Still-Apple-DNS.mobileconfig to the device,
open it and review/install it in system settings. Remove the profile there
to undo it. This is a DNS configuration profile, not a native app.

Apple-App-Source: Native iPhone/iPad and Mac source is included for later
building with Xcode. No signed IPA or Mac app is included. Apple hardware
installation and filtering remain untested.

Read READ-ME.md for detailed instructions, privacy, requirements and limits.
Windows and Apple use AdGuard Public DNS, not Android's custom filter engine.
They do not have Android's list selections, counters or private browser.
Windows DNS encryption is not configured by the companion. Apple uses HTTPS.
YouTube video ads are not reliably blocked.

Windows tests used mocks for network changes plus a real read-only adapter
check. Actual host DNS activation/restoration and ARM64 runtime testing are
still outstanding. SHA256SUMS.txt verifies the files inside this package.
"""


def main():
    files = {
        "Android/Still-2.1-update.apk": ROOT / "app/build/outputs/apk/debug/app-debug.apk",
        "Android/Still-2.1-fresh-install.apk": ROOT / "app/build/outputs/apk/release/app-release.apk",
        "Windows/Still-Windows-x64.zip": DIST / "Still-Windows-x64.zip",
        "Windows/Still-Windows-arm64.zip": DIST / "Still-Windows-arm64.zip",
        "Apple/Still-Apple-DNS.mobileconfig": ROOT / "platforms/apple/Still-Apple-DNS.mobileconfig",
        "READ-ME.md": ROOT / "platforms/README.md",
    }
    for name in ["StillApp.swift", "Still.entitlements", "project.yml", "build.sh", "generate_profile.py"]:
        files["Apple-App-Source/" + name] = ROOT / "platforms/apple" / name
    for path in files.values():
        if not path.is_file():
            raise FileNotFoundError(path)
    package = DIST / "Still-Cross-Device.zip"
    checksums = []
    with zipfile.ZipFile(package, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, path in files.items():
            data = path.read_bytes()
            checksums.append(f"{hashlib.sha256(data).hexdigest()}  {name}")
            archive.writestr(name, data, compress_type=zipfile.ZIP_STORED if path.suffix in [".apk", ".zip"] else zipfile.ZIP_DEFLATED)
        archive.writestr("START-HERE.txt", START)
        checksums.append(f"{hashlib.sha256(START.encode()).hexdigest()}  START-HERE.txt")
        archive.writestr("SHA256SUMS.txt", "\n".join(checksums) + "\n")
    with zipfile.ZipFile(package) as archive:
        assert archive.testzip() is None
        for entry in archive.read("SHA256SUMS.txt").decode().splitlines():
            expected, name = entry.split("  ", 1)
            assert hashlib.sha256(archive.read(name)).hexdigest() == expected, name
    with package.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    package.with_suffix(".sha256").write_text(f"{digest}  {package.name}\n")
    print(f"Verified {len(files) + 1} packaged files: {package} ({package.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
