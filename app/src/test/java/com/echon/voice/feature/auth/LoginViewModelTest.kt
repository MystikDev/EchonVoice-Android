package com.echon.voice.feature.auth

import com.echon.voice.core.network.ApiException
import com.echon.voice.model.EmailLoginChallenge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun incorrectCodeCanBeRetriedAndResendUsesANewChallenge() = runTest(dispatcher) {
        val logins = mutableListOf<Pair<String, String>>()
        val verifications = mutableListOf<Pair<String, String>>()
        var invalidCode = true
        val model = LoginViewModel({ email, password ->
            logins += email to password
            EmailLoginChallenge("challenge-${logins.size}")
        }, { id, code ->
            verifications += id to code
            if (invalidCode) throw ApiException.Http(400, "invalid_or_expired_code")
        })
        fill(model)
        model.submit(); model.submit(); advanceUntilIdle()
        assertEquals(1, logins.size)
        assertEquals("member@example.test", model.verificationEmail)
        assertFalse(model.canSubmit)
        model.code = "000000"
        model.submit(); advanceUntilIdle()
        assertEquals("challenge-1", model.challenge?.id)
        assertEquals("That code is incorrect or has expired. Try again or request a new code.", model.error)
        model.resendCode(); model.resendCode()
        assertFalse(model.canSubmit)
        advanceUntilIdle()
        assertEquals(listOf("member@example.test" to "password", "member@example.test" to "password"), logins)
        assertEquals("challenge-2", model.challenge?.id)
        assertEquals("", model.code)
        assertNull(model.error)
        assertNotNull(model.notice)
        invalidCode = false
        model.code = " 012345 "
        model.submit(); advanceUntilIdle()
        assertEquals(listOf("challenge-1" to "000000", "challenge-2" to "012345"), verifications)
        assertNull(model.challenge)
        assertEquals("", model.password)
        assertEquals("", model.code)
        assertEquals("", model.verificationEmail)
    }

    @Test fun verificationFailureHandlesMachineCodeEnvelope() = runTest(dispatcher) {
        val model = LoginViewModel({ _, _ -> EmailLoginChallenge("id") }, { _, _ ->
            throw ApiException.Http(400, "Bad code", "invalid_or_expired_code")
        })
        fill(model); model.submit(); advanceUntilIdle()
        model.code = "123456"; model.submit(); advanceUntilIdle()
        assertTrue(model.error!!.contains("incorrect or has expired"))
        assertTrue(model.canSubmit)
    }

    @Test fun loadingBlocksDuplicateActionsAndBackClearsChallengeAndSecrets() = runTest(dispatcher) {
        val pending = CompletableDeferred<EmailLoginChallenge?>()
        var calls = 0
        val model = LoginViewModel({ _, _ -> calls++; pending.await() }, { _, _ -> fail("No verification expected") })
        fill(model); model.submit(); model.submit(); model.resendCode(); model.startOver()
        runCurrent()
        assertEquals(1, calls)
        assertTrue(model.isLoading)
        pending.complete(EmailLoginChallenge("id")); advanceUntilIdle()
        model.code = "123456"
        model.startOver()
        assertNull(model.challenge)
        assertEquals("", model.code)
        assertEquals("", model.password)
        assertEquals("", model.verificationEmail)
        model.resendCode(); advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun networkFailureAndRateLimitedResendLeaveVerificationAvailable() = runTest(dispatcher) {
        var calls = 0
        val model = LoginViewModel({ _, _ ->
            when (++calls) {
                1 -> throw ApiException.Transport(java.io.IOException("offline"))
                2 -> EmailLoginChallenge("id")
                else -> throw ApiException.Http(429, "rate_limited")
            }
        }, { _, _ -> })
        fill(model); model.submit(); advanceUntilIdle()
        assertEquals("offline", model.error)
        assertTrue(model.canSubmit)
        model.submit(); advanceUntilIdle()
        model.resendCode(); advanceUntilIdle()
        assertEquals("Too many attempts. Please wait before trying again.", model.error)
        assertEquals("id", model.challenge?.id)
        assertFalse(model.isLoading)
    }

    @Test fun ordinaryLoginClearsCredentialsWithoutShowingACodeStep() = runTest(dispatcher) {
        val model = LoginViewModel({ _, _ -> null }, { _, _ -> fail("No verification expected") })
        fill(model); model.submit(); advanceUntilIdle()
        assertNull(model.challenge)
        assertNull(model.error)
        assertEquals("", model.password)
        assertFalse(model.isLoading)
    }

    private fun fill(model: LoginViewModel) {
        model.email = " member@example.test "
        model.password = "password"
    }
}
