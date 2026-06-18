package com.echon.voice.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Shared JSON config for REST + WebSocket decoding.
 *
 * Mirrors the iOS `JSONDecoder.keyDecodingStrategy = .convertFromSnakeCase`:
 * Kotlin camelCase property names map to the server's snake_case keys in both
 * directions. Tolerant of unknown keys and absent fields so new server fields
 * never crash an old client (the iOS app makes nearly every field optional for
 * the same reason).
 */
@OptIn(ExperimentalSerializationApi::class)
val EchonJson: Json = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}
