package dev.still.dns

import android.content.Context

enum class Appearance { System, Light, Dark }
enum class DnsProvider(val address: String) {
    Google("8.8.8.8"), Cloudflare("1.1.1.1"), Quad9("9.9.9.9")
}

data class AppSettings(
    val appearance: Appearance = Appearance.System,
    val dynamicColors: Boolean = true,
    val dnsProvider: DnsProvider = DnsProvider.Google,
    val protectionLevel: ProtectionLevel = ProtectionLevel.Basic,
    val allowedDomains: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val keepRecentDomains: Boolean = false,
    val enabledSubscriptions: Set<String> = emptySet(),
    val autoUpdateFilters: Boolean = true,
    val notifyFilterUpdates: Boolean = true
) {
    fun save(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("appearance", appearance.name)
            .putBoolean("dynamicColors", dynamicColors)
            .putString("dnsProvider", dnsProvider.name)
            .putString("protectionLevel", protectionLevel.name)
            .putStringSet("allowedDomains", allowedDomains)
            .putStringSet("blockedDomains", blockedDomains)
            .putBoolean("keepRecentDomains", keepRecentDomains)
            .putStringSet("enabledSubscriptions", enabledSubscriptions)
            .putBoolean("autoUpdateFilters", autoUpdateFilters)
            .putBoolean("notifyFilterUpdates", notifyFilterUpdates).apply()
    }

    companion object {
        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return AppSettings(
                Appearance.entries.firstOrNull { it.name == prefs.getString("appearance", null) } ?: Appearance.System,
                prefs.getBoolean("dynamicColors", true),
                DnsProvider.entries.firstOrNull { it.name == prefs.getString("dnsProvider", null) } ?: DnsProvider.Google,
                ProtectionLevel.entries.firstOrNull { it.name == prefs.getString("protectionLevel", null) } ?: ProtectionLevel.Basic,
                prefs.getStringSet("allowedDomains", emptySet())!!.toSet(),
                prefs.getStringSet("blockedDomains", emptySet())!!.toSet(),
                prefs.getBoolean("keepRecentDomains", false),
                prefs.getStringSet("enabledSubscriptions", emptySet())!!.toSet(),
                prefs.getBoolean("autoUpdateFilters", true),
                prefs.getBoolean("notifyFilterUpdates", true)
            )
        }
    }
}
