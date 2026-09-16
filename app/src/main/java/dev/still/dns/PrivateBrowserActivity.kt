package dev.still.dns

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/** One disposable WebView session. No URL, page state or form is saved by the activity. */
class PrivateBrowserActivity : ComponentActivity() {
    private var browser: WebView? = null
    private var ready by mutableStateOf(false)
    private var ending by mutableStateOf(false)
    private var address by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)
    private var pageProgress by mutableIntStateOf(100)
    private var pageTitle by mutableStateOf("")
    private var canGoBack by mutableStateOf(false)
    private var canGoForward by mutableStateOf(false)
    private var blocked by mutableIntStateOf(0)
    private var cleanupSupported = false
    private var sessionStarted = false
    private var browserDestroyed = false
    private lateinit var preferences: AppSettings
    private lateinit var repository: FilterRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preferences = AppSettings.load(this)
        repository = FilterLibrary.get(this)
        ProtectionStore.beginPrivateSession()
        sessionStarted = true
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { closeAndErase() }
        })
        setContent {
            StillTheme(preferences) {
                Scaffold(topBar = {
                    Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Still private", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = ::closeAndErase, enabled = !ending) { Text("Close and erase") }
                        }
                        OutlinedTextField(value = address, onValueChange = { address = it.take(4000) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = ready && !ending,
                            placeholder = { Text("Search or enter HTTPS address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onGo = { navigate() }),
                            trailingIcon = { TextButton(onClick = ::navigate, enabled = ready && !ending) { Text("Go") } })
                        if (pageTitle.isNotBlank()) Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { browser?.goBack() }, enabled = ready && !ending && canGoBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                            IconButton(onClick = { browser?.goForward() }, enabled = ready && !ending && canGoForward) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Forward") }
                            IconButton(onClick = { browser?.reload() }, enabled = ready && !ending) { Icon(Icons.Outlined.Refresh, "Reload") }
                            Text("$blocked blocked", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Leaving this screen ends and erases the session.", style = MaterialTheme.typography.labelSmall)
                        if (pageProgress < 100 && ready) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets).imePadding()) {
                        if (ready && !ending) {
                            AndroidView(factory = { createBrowser() }, modifier = Modifier.fillMaxSize())
                            if (address.isEmpty()) {
                                Column(Modifier.align(Alignment.Center).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("A fresh private session", style = MaterialTheme.typography.headlineSmall)
                                    Text("No saved history, passwords or downloads. Cookies and website storage are erased when you leave. The DNS domain log is paused during this session.")
                                    Text("Private browsing does not hide your IP address from websites or your internet provider. Searches go to DuckDuckGo.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text(when {
                                ending -> "Erasing browsing data..."
                                message != null -> message!!
                                else -> "Preparing a clean session..."
                            }, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                        }
                    }
                }
            }
        }
        cleanupSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)
        if (!cleanupSupported) {
            message = "Update Android System WebView to use private browsing with complete website-data cleanup."
            return
        }
        // Erase leftovers from a crash or forced stop before opening any website.
        eraseWebData {
            lifecycleScope.launch {
                repository.load()
                if (!ending) ready = true
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser(): WebView = WebView(this).also { view ->
        browser = view
        view.isSaveEnabled = false
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        view.setDownloadListener { _, _, _, _, _ -> message = "Downloads are not saved in private sessions." }
        view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) { pageTitle = title.orEmpty().take(200) }
            override fun onProgressChanged(view: WebView?, newProgress: Int) { pageProgress = newProgress }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!BrowserNavigation.isWebUrl(request.url.toString())) {
                    message = "Only HTTPS pages open in this private session. External app links stay closed."
                    return true
                }
                return false
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host?.lowercase() ?: return null
                if (FilterPolicy.blocked(host, preferences, repository.state.value)) {
                    runOnUiThread { blocked++ }
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked by Still", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageTitle = ""
                if (url != null && url != "about:blank") address = url
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) message = "Page could not load. Check your connection or allow rules."
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: android.net.http.SslError?) {
                handler.cancel()
                message = "The site's secure connection could not be verified."
            }
        }
    }

    private fun navigate() {
        if (!ready || ending) return
        val destination = BrowserNavigation.destination(address)
        if (destination == null) { message = "Enter a search or a valid HTTPS address."; return }
        message = null
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        currentFocus?.clearFocus()
        browser?.loadUrl(destination)
    }

    private fun eraseWebData(done: () -> Unit) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            WebStorageCompat.deleteBrowsingData(WebStorage.getInstance(), mainExecutorCompat(), Runnable {
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()
                    done()
                }
            })
        } else {
            message = "Update Android System WebView for complete website-data cleanup."
            done()
        }
    }

    private fun mainExecutorCompat() = java.util.concurrent.Executor { runOnUiThread(it) }

    private fun destroyBrowser() {
        if (browserDestroyed) return
        browserDestroyed = true
        browser?.let {
            it.stopLoading()
            (it.parent as? ViewGroup)?.removeView(it)
            it.clearHistory()
            it.clearFormData()
            it.destroy()
        }
        browser = null
        address = ""
        pageTitle = ""
    }

    private fun closeAndErase() {
        if (ending) return
        ending = true
        ready = false
        destroyBrowser()
        if (cleanupSupported) eraseWebData {
            finishSession()
            finish()
        } else {
            finishSession()
            finish()
        }
    }

    private fun finishSession() {
        if (sessionStarted) { sessionStarted = false; ProtectionStore.endPrivateSession() }
    }

    override fun onStop() {
        super.onStop()
        closeAndErase()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Run lifecycle bookkeeping, but retain no page or address state for restoration.
        outState.clear()
    }

    override fun onDestroy() {
        destroyBrowser()
        if (!ending && cleanupSupported) eraseWebData { finishSession() }
        else if (!cleanupSupported) finishSession()
        super.onDestroy()
    }
}
