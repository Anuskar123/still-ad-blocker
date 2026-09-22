package dev.still.dns

import java.net.IDN
import java.util.Locale

enum class ProtectionLevel(val title: String, val detail: String) {
    Basic("Basic", "Four ad-domain rules. Lowest risk of breaking sites."),
    Balanced("Balanced", "Adds a small set of advertising and analytics domains."),
    Strict("Strict", "Also blocks selected crash reporting and usage analytics. Some app features may stop working.")
}

/** Small, bundled starter lists, not a malware or complete advertising database. */
object FilterPolicy {
    val ads = setOf("ads.google.com", "doubleclick.net", "googlesyndication.com", "googleadservices.com")
    private val tracking = setOf("google-analytics.com", "adnxs.com", "criteo.com", "scorecardresearch.com", "quantserve.com")
    private val telemetry = setOf("app-measurement.com", "crashlytics.com", "bugsnag.com", "mixpanel.com", "amplitude.com")

    fun rules(level: ProtectionLevel): Set<String> = when (level) {
        ProtectionLevel.Basic -> ads
        ProtectionLevel.Balanced -> ads + tracking
        ProtectionLevel.Strict -> ads + tracking + telemetry
    }

    fun blocked(domain: String, settings: AppSettings, library: FilterLibraryState = FilterLibraryState()): Boolean {
        if (PacketParser.blocked(domain, settings.allowedDomains)) return false
        return PacketParser.blocked(domain, settings.blockedDomains) ||
            PacketParser.blocked(domain, rules(settings.protectionLevel)) ||
            library.blocked(domain, settings.enabledSubscriptions)
    }

    fun normalizeDomain(input: String): String? = runCatching {
        val domain = IDN.toASCII(input.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        require(domain.length <= 253 && '.' in domain)
        require(domain.split('.').all { label ->
            label.length in 1..63 && label.first() != '-' && label.last() != '-' &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        })
        require(!domain.all { it.isDigit() || it == '.' })
        domain
    }.getOrNull()

    /** Uses the same precedence as live filtering; performs no network lookup. */
    fun explain(domain: String, settings: AppSettings, library: FilterLibraryState): String {
        if (PacketParser.blocked(domain, settings.allowedDomains)) return "Allowed by your allow rule. Allow rules override all blocklists."
        if (PacketParser.blocked(domain, settings.blockedDomains)) return "Blocked by your custom block rule."
        if (PacketParser.blocked(domain, rules(settings.protectionLevel))) return "Blocked by the built-in ${settings.protectionLevel.title} rules."
        val matches = DownloadableFilter.entries.filter { it.name in settings.enabledSubscriptions && library.entries[it]?.list?.matches(domain) == true }
        if (matches.isNotEmpty()) return "Blocked by: " + matches.joinToString { it.title }
        val missing = DownloadableFilter.entries.any { it.name in settings.enabledSubscriptions && library.entries[it]?.list == null }
        return if (missing) "No match in available rules. Some selected lists have not been downloaded."
        else "No block rule matches. This does not establish that the site is safe."
    }
}
