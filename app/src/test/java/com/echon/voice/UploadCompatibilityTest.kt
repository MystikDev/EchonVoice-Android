package com.echon.voice

import com.echon.voice.core.network.*
import com.echon.voice.core.util.isEchonUploadUrl
import com.echon.voice.feature.chat.MessageStore
import com.echon.voice.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UploadCompatibilityTest {
    @Test fun profileImagesStayOnEchonUploadsAndAllowClear() {
        for (url in listOf("", "/uploads/avatar/a.png", "https://echon-voice.com/uploads/avatar/a.png")) assertTrue(url, isEchonUploadUrl(url))
        for (url in listOf("https://evil.test/uploads/a.png", "//evil.test/uploads/a.png", "http://echon-voice.com/uploads/a.png", "/uploads/../private", "/uploads/%2e%2e/private", "/uploads/"+"x".repeat(512))) assertFalse(url, isEchonUploadUrl(url))
    }

    @Test fun deniedMessageReloadPurgesCachedMessagesAfterOfflinePermissionChange() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))
            val api = retrofit2.Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(EchonJson.asConverterFactory("application/json".toMediaType())).build().create(EchonApi::class.java)
            val store = MessageStore(api, "private", ChatChannelKind.SERVER)
            store.applyRemote(Message("m", channelId = "private", content = "cached"))
            store.reconcileLatest()
            assertTrue(store.accessRevoked.value); assertTrue(store.messages.value.isEmpty())
        }
    }

    @Test fun attachmentUrlRemainsUploadResultAndInvalidAttachmentHasRecoveryMessage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_attachment"}"""))
            val api = retrofit2.Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(EchonJson.asConverterFactory("application/json".toMediaType())).build().create(EchonApi::class.java)
            val store = MessageStore(api, "channel", ChatChannelKind.SERVER)
            val original = "/uploads/message/owned-upload.png"
            store.send("file", User("me"), attachments = listOf(OutgoingAttachment("file.png", original, "image/png", 100)))
            assertTrue(server.takeRequest().body.readUtf8().contains("\"url\":\"$original\""))
            assertTrue(store.messages.value.single().sendFailed)
            assertTrue(store.messages.value.single().sendError!!.contains("upload the file again"))
        }
    }
}
