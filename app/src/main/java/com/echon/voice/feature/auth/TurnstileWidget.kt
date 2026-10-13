package com.echon.voice.feature.auth

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

internal fun validSiteKey(key: String) = key.matches(Regex("[A-Za-z0-9_-]{1,256}"))

internal fun turnstileHtml(siteKey: String): String {
    require(validSiteKey(siteKey)) // Never interpolate server-controlled HTML/JavaScript.
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com; connect-src https://challenges.cloudflare.com; style-src 'unsafe-inline'; img-src data: https://challenges.cloudflare.com; base-uri 'none'; form-action 'none'">
        <style>body{margin:0;background:transparent;display:flex;justify-content:center}</style>
        </head><body><div id="cf" aria-label="Signup verification"></div><script>
        function report(t){EchonCaptcha.postMessage(t);}
        setTimeout(function(){if(!window.turnstile)report('!error');},30000);
        function go(){turnstile.render('#cf',{sitekey:'$siteKey',action:'signup',theme:'auto',
          callback:function(t){report(t);},'expired-callback':function(){report('');},
          'timeout-callback':function(){report('');},'error-callback':function(){report('!error');}});}
        </script><script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=go&render=explicit" async defer onerror="report('!error')"></script>
        </body></html>
    """.trimIndent()
}

/** An isolated, credential-free WebView. Only the Echon top frame may return a token. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun TurnstileWidget(siteKey: String, onToken: (String) -> Unit, onError: () -> Unit, modifier: Modifier = Modifier) {
    val tokenCallback = rememberUpdatedState(onToken)
    val errorCallback = rememberUpdatedState(onError)
    AndroidView(
        modifier = modifier.height(85.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // No top-level navigation can replace our controlled document.
                        if (request.isForMainFrame) return true
                        val url = request.url
                        return !(url.toString() in setOf("about:blank", "about:srcdoc") ||
                            (url.scheme == "https" && url.host == "challenges.cloudflare.com" && (url.port == -1 || url.port == 443)))
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame) errorCallback.value()
                    }
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(this, "EchonCaptcha", setOf("https://echon-voice.com")) { _, message, origin, mainFrame, _ ->
                        if (mainFrame && origin.scheme == "https" && origin.host == "echon-voice.com" &&
                            (origin.port == -1 || origin.port == 443) && message.type == androidx.webkit.WebMessageCompat.TYPE_STRING && message.data != null) {
                            val token = message.data!!
                            if (token == "!error") errorCallback.value()
                            else if (token.length <= 2048) tokenCallback.value(token)
                        }
                    }
                    loadDataWithBaseURL("https://echon-voice.com/", turnstileHtml(siteKey), "text/html", "utf-8", null)
                } else {
                    post { errorCallback.value() } // Update Android System WebView to retry safely.
                }
            }
        },
        onRelease = { view ->
            tokenCallback.value("")
            view.stopLoading()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.removeWebMessageListener(view, "EchonCaptcha")
            }
            view.destroy()
        },
    )
}
