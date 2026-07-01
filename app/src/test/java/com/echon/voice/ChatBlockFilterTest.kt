package com.echon.voice

import com.echon.voice.feature.chat.withoutBlockedReactions
import com.echon.voice.model.Message
import com.echon.voice.model.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Proves the block filter strips blocked users' reaction contributions
 * (finding #1 in the security review): a blocked user must not remain visible
 * via a reaction count.
 */
class ChatBlockFilterTest {

    private fun msg(reactions: List<Reaction>?) =
        Message(id = "m1", reactions = reactions)

    @Test
    fun `no blocked users returns the same instance`() {
        val m = msg(listOf(Reaction(emojiKey = "👍", count = 2, userIds = listOf("a", "b"))))
        assertSame(m, m.withoutBlockedReactions(emptySet()))
    }

    @Test
    fun `blocked user is subtracted from the count and userIds`() {
        val m = msg(listOf(Reaction(emojiKey = "👍", count = 2, userIds = listOf("a", "bad"))))
        val result = m.withoutBlockedReactions(setOf("bad")).reactions!!.single()
        assertEquals(1, result.count)
        assertEquals(listOf("a"), result.userIds)
    }

    @Test
    fun `reaction drops entirely when all its users are blocked`() {
        val m = msg(listOf(Reaction(emojiKey = "👍", count = 1, userIds = listOf("bad"))))
        assertEquals(emptyList<Reaction>(), m.withoutBlockedReactions(setOf("bad")).reactions)
    }

    @Test
    fun `only the affected reaction changes, others are preserved`() {
        val untouched = Reaction(emojiKey = "🎉", count = 1, userIds = listOf("a"))
        val m = msg(
            listOf(
                untouched,
                Reaction(emojiKey = "👍", count = 2, userIds = listOf("a", "bad")),
            ),
        )
        val result = m.withoutBlockedReactions(setOf("bad")).reactions!!
        assertEquals(2, result.size)
        assertEquals(untouched, result[0])
        assertEquals(1, result[1].count)
    }

    @Test
    fun `reactions the server did not itemize are left untouched`() {
        val m = msg(listOf(Reaction(emojiKey = "👍", count = 5, userIds = null)))
        val result = m.withoutBlockedReactions(setOf("bad")).reactions!!.single()
        assertEquals(5, result.count)
        assertNull(result.userIds)
    }

    @Test
    fun `null or empty reactions are a no-op`() {
        val none = msg(null)
        assertSame(none, none.withoutBlockedReactions(setOf("bad")))
        val empty = msg(emptyList())
        assertSame(empty, empty.withoutBlockedReactions(setOf("bad")))
    }
}
