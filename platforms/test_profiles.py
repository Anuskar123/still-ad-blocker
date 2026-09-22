"""Structural checks. These do not claim Apple device or network validation."""
from pathlib import Path
import ipaddress
import plistlib
import unittest
from apple.generate_profile import PROFILE

ROOT = Path(__file__).parent


class ProfilesTest(unittest.TestCase):
    def setUp(self):
        self.profile = plistlib.loads((ROOT / "apple/Still-Apple-DNS.mobileconfig").read_bytes())

    def test_generated_profile_matches_source(self):
        self.assertEqual(self.profile, PROFILE)

    def test_only_removable_dns_payload(self):
        self.assertFalse(self.profile["PayloadRemovalDisallowed"])
        self.assertEqual(self.profile["PayloadType"], "Configuration")
        self.assertEqual(len(self.profile["PayloadContent"]), 1)
        payload = self.profile["PayloadContent"][0]
        self.assertEqual(payload["PayloadType"], "com.apple.dnsSettings.managed")
        self.assertFalse(payload["ProhibitDisablement"])
        self.assertEqual(payload["OnDemandRules"], [{"Action": "Connect"}])

    def test_provider_consistent_across_companions(self):
        settings = self.profile["PayloadContent"][0]["DNSSettings"]
        self.assertEqual(settings["DNSProtocol"], "HTTPS")
        self.assertEqual(settings["ServerURL"], "https://dns.adguard-dns.com/dns-query")
        swift = (ROOT / "apple/StillApp.swift").read_text()
        windows = (ROOT / "windows/DnsControl.ps1").read_text()
        self.assertIn(settings["ServerURL"], swift)
        for address in settings["ServerAddresses"]:
            self.assertTrue(ipaddress.ip_address(address).is_global)
            self.assertIn(address, swift)
            self.assertIn(address, windows)

    def test_apple_entitlement_is_dns_settings_only(self):
        entitlements = plistlib.loads((ROOT / "apple/Still.entitlements").read_bytes())
        self.assertEqual(entitlements, {"com.apple.developer.networking.networkextension": ["dns-settings"]})


if __name__ == "__main__":
    unittest.main()
