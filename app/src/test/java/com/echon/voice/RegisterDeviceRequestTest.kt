package com.echon.voice

import com.echon.voice.core.network.EchonJson
import com.echon.voice.model.RegisterDeviceRequest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the /v1/devices contract: the body field MUST serialize as `device_token`
 * (the server rejects `token` with 422). An explicit @SerialName here is silently
 * overridden by the global snake_case naming strategy, so the property is named
 * `deviceToken` and relies on that strategy — this test proves it produces the
 * right wire key.
 */
class RegisterDeviceRequestTest {

    @Test
    fun `body has both device_token and platform (the server's required fields)`() {
        val json = EchonJson.encodeToString(
            RegisterDeviceRequest.serializer(),
            RegisterDeviceRequest(deviceToken = "abc123", platform = "android"),
        )
        val obj = EchonJson.parseToJsonElement(json).jsonObject
        assertTrue("body must contain device_token; was $json", "device_token" in obj.keys)
        assertTrue("body must not contain a bare token key; was $json", "token" !in obj.keys)
        assertTrue("body must contain platform; was $json", "platform" in obj.keys)
        assertEquals("android", obj["platform"]!!.toString().trim('"'))
    }
}
