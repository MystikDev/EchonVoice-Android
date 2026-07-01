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
            // HEADERS would otherwise print the bearer and the rotating refresh
            // token to any app/user with READ_LOGS or `adb logcat`.
            redactHeader("Authorization")
            redactHeader("X-Refresh-Token")
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
    fun provideRefreshClient(): OkHttpClient =
        OkHttpClient.Builder()
            .certificatePinner(TlsPinning.certificatePinner())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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

    /** Primary pinned client: bearer attach + 401 refresh/retry. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .certificatePinner(TlsPinning.certificatePinner())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
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
