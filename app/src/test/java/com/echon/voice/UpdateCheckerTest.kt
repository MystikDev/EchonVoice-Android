package com.echon.voice

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.update.UpdateChecker
import com.echon.voice.core.update.UpdateStatus
import com.echon.voice.model.AppRelease
import com.echon.voice.model.LoginRequest
import com.echon.voice.model.LoginResponse
import com.echon.voice.model.MeResponse
import com.echon.voice.model.RegisterRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the in-app updater's decision logic deterministically (the live
 * manifest can't be hosted on the pinned host from a test). Installed build is
 * BuildConfig.VERSION_CODE = 1.
 */
class UpdateCheckerTest {

    /** EchonApi fake that returns a fixed manifest (or throws) for latestRelease. */
    private class FakeApi(
        val release: AppRelease? = null,
        val error: Throwable? = null,
    ) : EchonApi {
        override suspend fun latestRelease(url: String): AppRelease {
            error?.let { throw it }
            return release!!
        }
        override suspend fun login(body: LoginRequest): LoginResponse = error("unused")
        override suspend fun register(body: RegisterRequest): LoginResponse = error("unused")
        override suspend fun logout() = error("unused")
        override suspend fun me(): MeResponse = error("unused")
        override suspend fun acceptTos() = error("unused")
    }

    private fun release(versionCode: Int, mandatory: Boolean = false, minSupported: Int = 0) =
        AppRelease(
            versionCode = versionCode,
            versionName = "x",
            apkUrl = "https://echon-voice.com/app/echon-latest.apk",
            minSupportedVersionCode = minSupported,
            mandatory = mandatory,
        )

    @Test
    fun newerVersion_isAvailable_optional() = runTest {
        val status = UpdateChecker(FakeApi(release(versionCode = 2))).check()
        assertTrue(status is UpdateStatus.Available)
        assertEquals(false, (status as UpdateStatus.Available).mandatory)
    }

    @Test
    fun sameOrOlderVersion_isUpToDate() = runTest {
        assertEquals(UpdateStatus.UpToDate, UpdateChecker(FakeApi(release(versionCode = 1))).check())
        assertEquals(UpdateStatus.UpToDate, UpdateChecker(FakeApi(release(versionCode = 0))).check())
    }

    @Test
    fun belowMinSupported_isMandatory() = runTest {
        val status = UpdateChecker(FakeApi(release(versionCode = 3, minSupported = 2))).check()
        assertTrue(status is UpdateStatus.Available)
        assertTrue((status as UpdateStatus.Available).mandatory)
    }

    @Test
    fun mandatoryFlag_isMandatory() = runTest {
        val status = UpdateChecker(FakeApi(release(versionCode = 2, mandatory = true))).check()
        assertTrue((status as UpdateStatus.Available).mandatory)
    }

    @Test
    fun fetchFailure_neverBlocksApp() = runTest {
        val status = UpdateChecker(FakeApi(error = RuntimeException("offline"))).check()
        assertEquals(UpdateStatus.UpToDate, status)
    }
}
