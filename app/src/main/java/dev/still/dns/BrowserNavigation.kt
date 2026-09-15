package dev.still.dns

import java.net.URI
import java.net.URLEncoder

object BrowserNavigation {
    fun isWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty() && uri.userInfo == null
    }.getOrDefault(false)

    fun destination(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (value.contains("://") || value.startsWith("javascript:", true) || value.startsWith("data:", true) ||
            value.startsWith("file:", true) || value.startsWith("intent:", true) || value.startsWith("content:", true)) {
            return value.takeIf(::isWebUrl)
        }
        val asUrl = "https://$value"
        if (!value.any(Char::isWhitespace) && value.substringBefore('/').contains('.') && isWebUrl(asUrl)) return asUrl
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(value, "UTF-8")
    }
}
