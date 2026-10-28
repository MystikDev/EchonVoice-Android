package com.echon.voice.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatContentTest {
    @Test
    fun `findMessageLinks makes http links clickable without trailing punctuation`() {
        val text = "Watch https://youtu.be/dQw4w9WgXcQ, then visit https://example.com/a?q=1."

        assertEquals(
            listOf("https://youtu.be/dQw4w9WgXcQ", "https://example.com/a?q=1"),
            findMessageLinks(text).map { it.url },
        )
    }

    @Test
    fun `youtubeVideoId supports common YouTube link forms and rejects lookalike hosts`() {
        val id = "dQw4w9WgXcQ"

        assertEquals(id, youtubeVideoId("https://www.youtube.com/watch?v=$id&t=12"))
        assertEquals(id, youtubeVideoId("https://youtu.be/$id"))
        assertEquals(id, youtubeVideoId("https://m.youtube.com/shorts/$id"))
        assertEquals(id, youtubeVideoId("https://www.youtube-nocookie.com/embed/$id"))
        assertNull(youtubeVideoId("https://youtube.com.evil.example/watch?v=$id"))
        assertNull(youtubeVideoId("javascript:alert(1)"))
    }

    @Test
    fun `activeMention only matches a mention being typed and replacement preserves prior text`() {
        val text = "Hello there\n@jo"
        val mention = activeMention(text)!!

        assertEquals("jo", mention.query)
        assertEquals("Hello there\n@joseph ", replaceActiveMention(text, mention, "joseph"))
        assertNull(activeMention("email@example.com"))
        assertNull(activeMention("@joseph already selected"))
    }
}
