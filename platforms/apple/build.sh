#!/bin/sh
set -eu
cd "$(dirname "$0")"
command -v xcodegen >/dev/null || { echo 'Install XcodeGen on your Mac: brew install xcodegen'; exit 1; }
xcodegen generate
case "${1:-check}" in
  check)
    xcodebuild -project StillApple.xcodeproj -scheme StillIOS -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
    xcodebuild -project StillApple.xcodeproj -scheme StillMac -configuration Debug CODE_SIGNING_ALLOWED=NO build
    ;;
  open) open StillApple.xcodeproj ;;
  *) echo 'Usage: ./build.sh [check|open]'; exit 1 ;;
esac
