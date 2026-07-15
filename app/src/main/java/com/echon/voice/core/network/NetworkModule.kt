package com.echon.voice.core.network

import com.echon.voice.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private fun logging(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // Never emit session credentials to logcat, even in debug builds:
            // HEADERS would otherwise print the bearer, the rotating refresh token,
            // and the refresh_token cookie to anyone with READ_LOGS / `adb logcat`.
            redactHeader("Authorization")
            redactHeader("X-Refresh-Token")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    /**
     * Bare pinned client used only by [TokenRefresher]. No authenticator → a
     * failed refresh can never recurse into another refresh.
     */
    @Provides
    @Singleton
    @Named("refresh")
    fun provideRefreshClient(refreshCookie: RefreshCookieInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .certificatePinner(TlsPinning.certificatePinner())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(refreshCookie) // capture the rotated refresh_token cookie
            .addInterceptor(logging())
            .build()

    /**
     * Plain HTTPS client (no echon pinning, no auth) for update checks/downloads,
     * which are hosted on GitHub Releases — a different host than the pinned API.
     * Integrity of the downloaded APK is guaranteed by Android's signature check
     * (same release key as the installed app).
     */
    @Provides
    @Singleton
    @Named("plain")
    fun providePlainClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    /**
     * Media client for Coil (avatars/attachments). Pinned so echon-voice.com media
     * still fails closed, but carries NO session credentials, NO 401 authenticator,
     * and does NOT capture refresh cookies — so an absolute image URL to a
     * third-party host can never receive the bearer or poison the session.
     */
    @Provides
    @Singleton
    @Named("media")
    fun provideMediaClient(): OkHttpClient =
        OkHttpClient.Builder()
            .certificatePinner(TlsPinning.certificatePinner())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging())
            .build()

    /** Primary pinned client: bearer attach + 401 refresh/retry. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
        refreshCookie: RefreshCookieInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .certificatePinner(TlsPinning.certificatePinner())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .addInterceptor(refreshCookie) // capture the refresh_token cookie from login
            .addInterceptor(logging())
            .build()

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(EchonJson.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideEchonApi(retrofit: Retrofit): EchonApi =
        retrofit.create(EchonApi::class.java)
}
