package com.echon.voice

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.echon.voice.core.updateapi.scheduleAppUpdates
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Application entry point. Hilt's object graph roots here; the singleton-scoped
 * stores are the Android analog of the iOS `AppState` composition root.
 *
 * Also supplies Coil's [ImageLoader] backed by the pinned OkHttp client, so all
 * image loads (avatars, attachments) honor certificate pinning — the analog of
 * the iOS `RemoteImage` going through the pinned session instead of AsyncImage.
 */
@HiltAndroidApp
class EchonApp : Application(), ImageLoaderFactory {
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        // Direct-download flavor keeps installs current in the background; the Play
        // flavor supplies a no-op (Play delivers updates and prohibits self-update).
        scheduleAppUpdates(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
}
