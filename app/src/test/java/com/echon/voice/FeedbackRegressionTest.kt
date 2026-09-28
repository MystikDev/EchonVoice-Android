package com.echon.voice

import com.echon.voice.core.network.EchonApi
import com.echon.voice.core.network.EchonJson
import com.echon.voice.feature.chat.MessageStore
import com.echon.voice.feature.invites.inviteCode
import com.echon.voice.model.ChatChannelKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class FeedbackRegressionTest {
    private fun api(server: MockWebServer) = Retrofit.Builder().baseUrl(server.url("/"))
        .addConverterFactory(EchonJson.asConverterFactory("application/json".toMediaType())).build().create(EchonApi::class.java)

    @Test fun inviteMutationsSendJsonObjects() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"abc-123"}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val api = api(server)
            assertEquals("abc-123", api.createInvite("channel").code)
            api.useInvite("abc-123")
            listOf("/v1/channels/channel/invites", "/v1/invites/abc-123/use").forEach { path ->
                val request = server.takeRequest()
                assertEquals(path, request.path); assertEquals("POST", request.method)
                assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
                assertEquals("{}", request.body.readUtf8())
            }
        }
    }

    @Test fun repeatedBlankLinesSurviveSendEditAndRetryForDmAndServer() = runBlocking {
        for (kind in ChatChannelKind.entries) MockWebServer().use { server ->
            val raw = "\r\n First\r\n\r\n\rSecond\n\n"
            val expected = "\n First\n\n\nSecond\n\n"
            val body = """{"id":"message","channel_id":"channel","content":${EchonJson.encodeToString(String.serializer(),expected)}}"""
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody(body))
            server.enqueue(MockResponse().setBody(body))
            val store = MessageStore(api(server), "channel", kind)
            store.send(raw, null)
            val failed = store.messages.value.single()
            assertTrue(failed.sendFailed); assertEquals(expected, failed.content)
            store.retry(failed.id, null)
            assertFalse(store.messages.value.single().sendFailed)
            store.edit("message", raw)
            repeat(3) {
                val request = server.takeRequest()
                assertEquals(expected, EchonJson.parseToJsonElement(request.body.readUtf8()).jsonObject["content"]!!.jsonPrimitive.content)
            }
            assertEquals(expected, store.messages.value.single().content)
            store.send("\r\n\n ", null)
            assertEquals(1, store.messages.value.size)
        }
    }

    @Test fun pastedInviteLinksAreParsedWithoutAcceptingForeignUrlsOrPathInjection() {
        assertEquals("abc-123", inviteCode("  abc-123\n"))
        assertEquals("abc-123", inviteCode("https://echon-voice.com/invite/abc-123"))
        assertEquals("abc_123", inviteCode("https://echon-voice.com/invites/abc_123/"))
        listOf("https://evil.example/invite/abc", "https://echon-voice.com.evil.example/invite/abc", "https://evil@echon-voice.com/invite/abc", "../abc", "a/b", "", "https://echon-voice.com/invite/%2Fabc").forEach { assertNull(it, inviteCode(it)) }
    }
}
