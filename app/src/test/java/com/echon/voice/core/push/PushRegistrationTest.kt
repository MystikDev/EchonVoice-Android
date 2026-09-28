package com.echon.voice.core.push

import com.echon.voice.core.network.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PushRegistrationTest {
    @Test fun retriesTransientFailureAndReportsSuccess() = runTest {
        var attempts = 0
        val states = mutableListOf<PushRegistrationStatus>()
        registerPushWithRetry({ "token" }, { if (++attempts < 3) throw IOException() }, { true }, states::add)
        assertEquals(3, attempts)
        assertEquals(listOf(PushRegistrationStatus.REGISTERING, PushRegistrationStatus.REGISTERED), states)
    }
    @Test fun permanentFailureDoesNotPretendDeviceIsRegistered() = runTest {
        var attempts = 0
        val states = mutableListOf<PushRegistrationStatus>()
        registerPushWithRetry({ "token" }, { attempts++; throw ApiException.Http(404, "Not available") }, { true }, states::add)
        assertEquals(1, attempts); assertEquals(PushRegistrationStatus.FAILED, states.last())
    }
    @Test fun changedAccountDuringTokenFetchNeverRegistersOldWork() = runTest {
        var signedIn = true
        var requests = 0
        val states = mutableListOf<PushRegistrationStatus>()
        registerPushWithRetry({ signedIn = false; "token" }, { requests++ }, { signedIn }, states::add)
        assertEquals(0, requests); assertFalse(PushRegistrationStatus.REGISTERED in states)
    }
    @Test fun stalledTokenFetchEventuallyReportsFailure() = runTest {
        var attempts = 0
        val states = mutableListOf<PushRegistrationStatus>()
        registerPushWithRetry({ attempts++; kotlinx.coroutines.delay(60_000); "token" }, { fail("Must not register") }, { true }, states::add)
        assertEquals(3, attempts); assertEquals(PushRegistrationStatus.FAILED, states.last())
    }
    @Test fun cancellationIsNotRetriedOrReportedAsFailure() = runTest {
        val states = mutableListOf<PushRegistrationStatus>()
        try {
            registerPushWithRetry({ throw CancellationException() }, { fail("Must not register") }, { true }, states::add)
            fail("Must propagate cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf(PushRegistrationStatus.REGISTERING), states)
    }
}
