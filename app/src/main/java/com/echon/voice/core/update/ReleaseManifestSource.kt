package com.echon.voice.core.update

import com.echon.voice.core.network.EchonJson
import com.echon.voice.model.AppRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named

/** Distribution config for direct (GitHub Releases) download. */
object UpdateConfig {
    // TODO(confirm repo slug before publishing): owner/repo for the public release.
    private const val REPO = "MystikDev/EchonVoice-Android"

    /** Update manifest, committed in the repo and served raw from the default branch. */
    const val MANIFEST_URL = "https://raw.githubusercontent.com/$REPO/main/distribution/latest.json"

    /** Stable "latest release" APK asset URL. */
    const val LATEST_APK_URL = "https://github.com/$REPO/releases/latest/download/echon-release.apk"
}

/** Fetches the update manifest. Abstracted so the version logic stays unit-testable. */
interface ReleaseManifestSource {
    suspend fun fetch(): AppRelease?
}

/** Pulls the manifest from GitHub over the plain (unpinned) client. */
class GithubReleaseManifestSource @Inject constructor(
    @Named("plain") private val client: OkHttpClient,
) : ReleaseManifestSource {
    override suspend fun fetch(): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(UpdateConfig.MANIFEST_URL).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.let { EchonJson.decodeFromString(AppRelease.serializer(), it) }
            }
        }.getOrNull()
    }
}
