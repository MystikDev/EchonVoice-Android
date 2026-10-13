package com.echon.voice.feature.auth

import com.echon.voice.core.network.*
import com.echon.voice.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SignupCompatibilityTest {
    private fun api(config: () -> CaptchaConfig) = Proxy.newProxyInstance(
        EchonApi::class.java.classLoader, arrayOf(EchonApi::class.java),
    ) { _, method, _ -> check(method.name == "captchaConfig"); config() } as EchonApi

    private fun fill(model: RegisterViewModel) {
        model.email = "member@example.test"; model.username = "member"; model.password = "password123"
        model.setDateOfBirthMillis(946684800000); model.tosAccepted = true
    }

    @Test fun signupSendsSingleUseTokenAndRejectsOldWidgetCallbacksAfterFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tokens = mutableListOf<String?>()
            val model = RegisterViewModel({ _, _, _, _, token ->
                tokens += token
                throw ApiException.Http(400, "captcha_failed")
            }, api { CaptchaConfig(siteKey = "0xExample", required = true) })
            fill(model); assertFalse(model.canSubmit)
            advanceUntilIdle(); assertFalse(model.canSubmit)
            val revision = model.captchaRevision
            model.onCaptchaToken(revision, "fresh-token"); assertTrue(model.canSubmit)
            model.submit(); model.submit(); advanceUntilIdle()
            assertEquals(listOf("fresh-token"), tokens)
            assertEquals("Please try the check again.", model.error)
            assertFalse(model.canSubmit); assertNull(model.captchaToken)
            model.onCaptchaToken(revision, "stale-token"); assertFalse(model.canSubmit)
            model.onCaptchaToken(model.captchaRevision, "new-token"); assertTrue(model.canSubmit)
            model.onCaptchaToken(model.captchaRevision, ""); assertFalse(model.canSubmit)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun nullKeySkipsWidgetButConfigurationFailureRequiresRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var fail = true
            val tokens = mutableListOf<String?>()
            val model = RegisterViewModel({ _, _, _, _, token -> tokens += token }, api {
                if (fail) throw java.io.IOException("offline")
                CaptchaConfig(siteKey = null)
            })
            fill(model); advanceUntilIdle(); assertFalse(model.canSubmit); assertNotNull(model.captchaError)
            fail = false; model.loadCaptcha(); advanceUntilIdle(); assertTrue(model.canSubmit)
            model.submit(); advanceUntilIdle(); assertEquals(listOf<String?>(null), tokens)
            val optional = RegisterViewModel({ _, _, _, _, token -> tokens += token }, api {
                CaptchaConfig(siteKey = "0xExample", required = false)
            })
            fill(optional); advanceUntilIdle(); assertTrue(optional.canSubmit)
            optional.submit(); advanceUntilIdle(); assertEquals(listOf<String?>(null, null), tokens)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun enforcementChangeRefreshesConfigurationAndRequiresFreshToken() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var enforced = false
            val model = RegisterViewModel({ _, _, _, _, _ ->
                enforced = true
                throw ApiException.Http(400, "captcha_required")
            }, api { CaptchaConfig(siteKey = "0xExample", required = enforced) })
            fill(model); advanceUntilIdle(); assertTrue(model.canSubmit)
            model.submit(); advanceUntilIdle()
            assertTrue(model.captchaConfig!!.required); assertFalse(model.canSubmit)
            model.onCaptchaToken(model.captchaRevision, "fresh-token"); assertTrue(model.canSubmit)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun malformedSiteKeyCannotInjectHtmlAndEveryAttemptResetsToken() {
        assertFalse(validSiteKey("x'</script><script>alert(1)</script>"))
        assertThrows(IllegalArgumentException::class.java) { turnstileHtml("x'") }
        val html = turnstileHtml("0xExample")
        assertTrue(html.contains("action:'signup'")); assertTrue(html.contains("expired-callback"))
    }

    @Test fun signupWireUsesCaptchaTokenWhileLoginRemainsUnchanged() = kotlinx.coroutines.runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"provider":"turnstile","site_key":null,"required":false}"""))
            server.enqueue(MockResponse().setBody("""{"token":"access"}"""))
            server.enqueue(MockResponse().setBody("""{"token":"access"}"""))
            // Use the same serialization as production with the local transport.
            val local = retrofit2.Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(EchonJson.asConverterFactory("application/json".toMediaType()))
                .build().create(EchonApi::class.java)
            assertNull(local.captchaConfig().siteKey)
            local.register(RegisterRequest("e", "u", "p", "2000-01-01", "one-use-token"))
            local.login(LoginRequest("e", "p"))
            assertEquals("/v1/auth/captcha", server.takeRequest().path)
            val signup = server.takeRequest().body.readUtf8()
            assertTrue(signup.contains("\"captcha_token\":\"one-use-token\""))
            assertFalse(server.takeRequest().body.readUtf8().contains("captcha"))
        }
    }
}
