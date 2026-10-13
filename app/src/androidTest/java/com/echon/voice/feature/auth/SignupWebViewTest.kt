package com.echon.voice.feature.auth

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SignupWebViewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun onlyTrustedTopFrameCanReturnTokenAndWebViewHasNoFileAccess() {
        val tokens = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<String>()
        // An explicit local diagnostic may use Cloudflare's public always-pass fixture.
        // The normal CI path stays offline and tests the native origin boundary.
        val smoke = InstrumentationRegistry.getArguments().getString("turnstileSmokeKey")
        require(smoke == null || smoke == "1x00000000000000000000AA")
        compose.setContent { MaterialTheme { TurnstileWidget(smoke ?: "0xExample", { tokens.add(it) }, { errors.add("error") }) } }
        if (smoke != null) {
            compose.waitUntil(30_000) { tokens.any { it.isNotBlank() } }
            assertTrue(tokens.any { it.contains("DUMMY.TOKEN") })
            return // No account request or production secret is used.
        }
        lateinit var web: WebView
        var supported = false
        compose.runOnIdle {
            web = checkNotNull(findWebView(compose.activity.window.decorView))
            supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
            assertFalse(web.settings.allowFileAccess); assertFalse(web.settings.allowContentAccess)
            web.stopLoading()
            if (supported) {
                web.loadDataWithBaseURL("https://echon-voice.com/", """
                    <html><body><script>EchonCaptcha.postMessage('trusted');</script>
                    <iframe srcdoc="&lt;script&gt;EchonCaptcha.postMessage('subframe');&lt;/script&gt;"></iframe>
                    </body></html>
                """.trimIndent(), "text/html", "utf-8", null)
            }
        }
        if (!supported) {
            compose.waitUntil(5_000) { errors.isNotEmpty() }
            assertTrue(tokens.isEmpty()) // Older WebViews fail closed with a retry/update prompt.
            return
        }
        compose.waitUntil(5_000) { "trusted" in tokens }
        compose.runOnIdle {
            web.loadDataWithBaseURL("https://foreign.example/", """
                <html><script>
                  if(typeof EchonCaptcha !== 'undefined') EchonCaptcha.postMessage('foreign');
                  document.title='foreign-loaded';
                </script></html>
            """.trimIndent(), "text/html", "utf-8", null)
        }
        compose.waitUntil(5_000) { var loaded = false; compose.runOnIdle { loaded = web.title == "foreign-loaded" }; loaded }
        assertEquals(listOf("trusted"), tokens.toList())
    }
}
