package com.echon.voice.core.network

import com.echon.voice.model.AppRelease
import com.echon.voice.model.DeleteAccountRequest
import com.echon.voice.model.LoginRequest
import com.echon.voice.model.LoginResponse
import com.echon.voice.model.MeResponse
import com.echon.voice.model.RegisterRequest
import com.echon.voice.model.ReportRequest
import com.echon.voice.model.User
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
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

    // --- Moderation (Play UGC policy / store review) ---

    @POST("v1/messages/{id}/report")
    suspend fun reportMessage(@Path("id") id: String, @Body body: ReportRequest)

    @POST("v1/users/{id}/report")
    suspend fun reportUser(@Path("id") id: String, @Body body: ReportRequest)

    /** GET → bare array of blocked users. */
    @GET("v1/me/blocks")
    suspend fun myBlocks(): List<User>

    @POST("v1/me/blocks/{id}")
    suspend fun block(@Path("id") userId: String)

    @DELETE("v1/me/blocks/{id}")
    suspend fun unblock(@Path("id") userId: String)

    @POST("v1/me/delete")
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)

    /**
     * Direct-download update manifest (public, no bearer). Relative URL resolves
     * against the base host: https://echon-voice.com/app/latest.json.
     */
    @GET
    @Headers("${AuthInterceptor.NO_AUTH_HEADER}: 1")
    suspend fun latestRelease(@Url url: String = "app/latest.json"): AppRelease
}
