package dev.still.dns

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PrivateBrowserTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val origin = "https://still-privacy-test.invalid"

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < end) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for private-browser state")
    }

    private fun evaluate(browser: WebView, script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync { browser.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue("JavaScript callback", done.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun launchReady(): Pair<ActivityScenario<PrivateBrowserActivity>, WebView> {
        var supported = false
        instrumentation.runOnMainSync { supported = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA) }
        assumeTrue("Requires WebView browsing-data deletion support", supported)
        val scenario = ActivityScenario.launch(PrivateBrowserActivity::class.java)
        var browser: WebView? = null
        waitFor { scenario.onActivity { browser = findWebView(it.window.decorView) }; browser != null }
        scenario.onActivity {
            assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        instrumentation.runOnMainSync {
            browser!!.loadDataWithBaseURL(origin, "<html><body>Private storage test<script>window.loaded=true;</script></body></html>", "text/html", "UTF-8", null)
        }
        waitFor { evaluate(browser!!, "window.loaded === true") == "true" }
        return scenario to browser!!
    }

    private fun verifyErase(background: Boolean) {
        val (scenario, browser) = launchReady()
        try {
            evaluate(browser, "localStorage.setItem('privateMarker','secret'); sessionStorage.setItem('privateMarker','secret'); document.cookie='privateMarker=secret; Secure; SameSite=Strict'; 'done'")
            assertEquals("\"secret\"", evaluate(browser, "localStorage.getItem('privateMarker')"))
            assertTrue(evaluate(browser, "document.cookie").contains("privateMarker=secret"))
            evaluate(browser, "window.dbReady=false; var r=indexedDB.open('privateDatabase',1); r.onupgradeneeded=function(){r.result.createObjectStore('values')}; r.onsuccess=function(){r.result.close();window.dbReady=true};")
            waitFor { evaluate(browser, "window.dbReady") == "true" }
            if (background) scenario.moveToState(Lifecycle.State.CREATED)
            else scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitFor { scenario.state == Lifecycle.State.DESTROYED }
            var cookies: String? = null
            instrumentation.runOnMainSync { cookies = CookieManager.getInstance().getCookie(origin) }
            assertTrue("Session cookies erased", cookies.isNullOrEmpty())
        } finally { scenario.close() }

        val (fresh, newBrowser) = launchReady()
        try {
            assertEquals("null", evaluate(newBrowser, "localStorage.getItem('privateMarker')"))
            assertEquals("null", evaluate(newBrowser, "sessionStorage.getItem('privateMarker')"))
            assertFalse(evaluate(newBrowser, "document.cookie").contains("privateMarker"))
            evaluate(newBrowser, "window.databaseCount=-1; indexedDB.databases().then(function(d){window.databaseCount=d.length;});")
            waitFor { evaluate(newBrowser, "window.databaseCount") != "-1" }
            assertEquals("0", evaluate(newBrowser, "window.databaseCount"))
        } finally { fresh.close() }
    }

    @Test fun closeErasesCookiesLocalStorageSessionStorageAndIndexedDb() = verifyErase(false)
    @Test fun backgroundingEndsAndErasesSession() = verifyErase(true)
}
