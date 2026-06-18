package com.echon.voice

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt's object graph roots here; the singleton-scoped
 * [com.echon.voice.core.network.ApiClient], realtime client, and feature stores
 * are the Android analog of the iOS `AppState` composition root.
 */
@HiltAndroidApp
class EchonApp : Application()
