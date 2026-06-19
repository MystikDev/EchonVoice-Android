package com.echon.voice.core.network

import com.echon.voice.model.AppRelease
import com.echon.voice.model.LoginRequest
import com.echon.voice.model.LoginResponse
import com.echon.voice.model.MeResponse
import com.echon.voice.model.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit surface for the echon-voice.com /v1 API. Grows per phase; Phase 0/1
 * covers auth + session. The iOS `Endpoint` enum is the contract reference.
 */
interface EchonApi {

    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): LoginResponse

    @POST("v1/auth/logout")
    suspend fun logout()

    @GET("v1/me")
    suspend fun me(): MeResponse

    @POST("v1/me/accept-tos")
    suspend fun acceptTos()

    /**
     * Direct-download update manifest (public, no bearer). Relative URL resolves
     * against the base host: https://echon-voice.com/app/latest.json.
     */
    @GET
    @Headers("${AuthInterceptor.NO_AUTH_HEADER}: 1")
    suspend fun latestRelease(@Url url: String = "app/latest.json"): AppRelease
}
