package com.echon.voice

import com.echon.voice.core.update.ReleaseManifestSource
import com.echon.voice.core.update.UpdateChecker
import com.echon.voice.core.update.UpdateStatus
import com.echon.voice.model.AppRelease
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the in-app updater's decision logic deterministically. The installed
 * build is BuildConfig.VERSION_CODE = 1.
 */
class UpdateCheckerTest {

    private class FakeSource(val release: AppRelease?, val error: Throwable? = null) : ReleaseManifestSource {
        override suspend fun fetch(): AppRelease? {
            error?.let { throw it }
            return release
        }
    }

    private fun release(versionCode: Int, mandatory: Boolean = false, minSupported: Int = 0) =
        AppRelease(
            versionCode = versionCode,
            versionName = "x",
            apkUrl = "https://github.com/owner/repo/releases/latest/download/echon-release.apk",
            minSupportedVersionCode = minSupported,
            mandatory = mandatory,
        )

    @Test
    fun newerVersion_isAvailable_optional() = runTest {
        val status = UpdateChecker(FakeSource(release(versionCode = 2))).check()
        assertTrue(status is UpdateStatus.Available)
        assertEquals(false, (status as UpdateStatus.Available).mandatory)
    }

    @Test
    fun sameOrOlderVersion_isUpToDate() = runTest {
        assertEquals(UpdateStatus.UpToDate, UpdateChecker(FakeSource(release(versionCode = 1))).check())
        assertEquals(UpdateStatus.UpToDate, UpdateChecker(FakeSource(release(versionCode = 0))).check())
    }

    @Test
    fun belowMinSupported_isMandatory() = runTest {
        val status = UpdateChecker(FakeSource(release(versionCode = 3, minSupported = 2))).check()
        assertTrue((status as UpdateStatus.Available).mandatory)
    }

    @Test
    fun mandatoryFlag_isMandatory() = runTest {
        val status = UpdateChecker(FakeSource(release(versionCode = 2, mandatory = true))).check()
        assertTrue((status as UpdateStatus.Available).mandatory)
    }

    @Test
    fun missingManifest_neverBlocksApp() = runTest {
        assertEquals(UpdateStatus.UpToDate, UpdateChecker(FakeSource(null)).check())
        assertEquals(UpdateStatus.UpToDate, UpdateChecker(FakeSource(null, RuntimeException("offline"))).check())
    }
}
