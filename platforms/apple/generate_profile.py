"""Generate a removable DNS-only Apple configuration profile, without certificates or MDM."""
from pathlib import Path
import plistlib

OUT = Path(__file__).with_name("Still-Apple-DNS.mobileconfig")
PROFILE = {
    "PayloadContent": [{
        "PayloadType": "com.apple.dnsSettings.managed",
        "PayloadVersion": 1,
        "PayloadIdentifier": "dev.still.dns.profile.settings",
        "PayloadUUID": "b983755a-f889-4c1e-8a65-2d4fdb2c9286",
        "PayloadDisplayName": "Still - AdGuard Public DNS",
        "PayloadDescription": "Sends DNS queries to AdGuard Public DNS over HTTPS to filter ads and trackers. Does not reliably block YouTube video ads.",
        "DNSSettings": {
            "DNSProtocol": "HTTPS",
            "ServerURL": "https://dns.adguard-dns.com/dns-query",
            "ServerAddresses": ["94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff"],
        },
        "OnDemandRules": [{"Action": "Connect"}],
        "ProhibitDisablement": False,
    }],
    "PayloadType": "Configuration",
    "PayloadVersion": 1,
    "PayloadIdentifier": "dev.still.dns.profile",
    "PayloadUUID": "f7caa11b-42e4-432b-af09-f6a1fab66ceb",
    "PayloadDisplayName": "Still - filtering DNS",
    "PayloadDescription": "Encrypted DNS using AdGuard Public DNS. This is a DNS configuration, not the Android app. No custom Still rules or counters. See https://adguard-dns.io/en/privacy.html. Remove this profile to undo the configuration.",
    "PayloadOrganization": "Still (independent of AdGuard)",
    "PayloadRemovalDisallowed": False,
    "ConsentText": {"default": "This profile sends DNS queries to AdGuard's public filtering resolver over HTTPS. AdGuard operates the resolver and controls its blocklists. Still does not collect DNS logs. Some VPNs and apps can bypass DNS filtering. Internal network names may stop resolving. You can remove this profile in Settings. No certificates, VPN tunnel or device management enrolment are installed."},
}

if __name__ == "__main__":
    OUT.write_bytes(plistlib.dumps(PROFILE, sort_keys=False))
    print(OUT)
