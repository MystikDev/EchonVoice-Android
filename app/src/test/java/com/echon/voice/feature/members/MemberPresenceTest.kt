package com.echon.voice.feature.members

import com.echon.voice.core.realtime.PresenceState
import com.echon.voice.model.Member
import com.echon.voice.model.User
import org.junit.Assert.*
import org.junit.Test

class MemberPresenceTest {
    private val members = listOf("online", "idle", "dnd", "offline").map { Member(User(it, username = it)) }

    @Test fun onlineCountIncludesIdleAndDndWithoutClassifyingThemOffline() {
        val state = PresenceState(mapOf("online" to "online", "idle" to "idle", "dnd" to "dnd"), loaded = true)
        val groups = groupMembers(members + members.first(), state)
        assertEquals(listOf("Online", "Offline"), groups.map { it.label })
        assertEquals(listOf(3, 1), groups.map { it.members.size })
        assertEquals("offline", groups.last().members.single().user.id)
    }

    @Test fun unavailableConnectionDoesNotCreateFalseOfflinePopulation() {
        val groups = groupMembers(members, PresenceState())
        assertEquals("Status unavailable", groups.single().label)
        assertEquals(4, groups.single().members.size)
    }
}
