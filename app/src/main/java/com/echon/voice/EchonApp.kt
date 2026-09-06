package com.echon.voice

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.echon.voice.core.push.PushTokenRegistrar
import com.echon.voice.core.updateapi.scheduleAppUpdates
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named

/**
 * Application entry point. Hilt's object graph roots here; the singleton-scoped
 * stores are the Android analog of the iOS `AppState` composition root.
 *
 * Supplies Coil's [ImageLoader] backed by the pinned, origin-restricted media
 * client, so image loads honor certificate pinning while restricting the
 * session bearer to the exact API origin. Third-party image hosts receive no credentials.
 */
@HiltAndroidApp
class EchonApp : Application(), ImageLoaderFactory {
    @Inject @Named("media") lateinit var mediaClient: OkHttpClient
    @Inject lateinit var pushTokenRegistrar: PushTokenRegistrar

    override fun onCreate() {
        super.onCreate()
        // Direct-download flavor keeps installs current in the background; the Play
        // flavor supplies a no-op (Play delivers updates and prohibits self-update).
        scheduleAppUpdates(this)
        // Register the FCM token for push notifications once signed in (no-op until
        // a Firebase project + backend /v1/devices endpoint are configured).
        pushTokenRegistrar.start()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(mediaClient)
            // Authenticated attachment bytes must not persist in a shared disk cache.
            .diskCachePolicy(coil.request.CachePolicy.DISABLED)
            .crossfade(true)
            .build()
}
