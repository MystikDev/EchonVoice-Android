package com.echon.voice.core.realtime

import com.echon.voice.core.di.ApplicationScope
import com.echon.voice.core.network.EchonApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PresenceResponse(val presences: Map<String, String>)

object PresenceStatus {
    val supported = setOf("online", "idle", "dnd", "offline")
    fun label(status: String?): String = when (status) {
        "online" -> "Online"
        "idle" -> "Idle"
        "dnd" -> "Do not disturb"
        "offline" -> "Offline"
        else -> "Status unavailable"
    }
}

data class PresenceState(
    val statuses: Map<String, String> = emptyMap(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
) {
    // The API snapshot is sparse: absent users are offline only after a successful load.
    fun status(userId: String): String? = if (userId in statuses) {
        statuses[userId]?.takeIf { it in PresenceStatus.supported }
    } else if (loaded) "offline" else null
}

/** Snapshot hydration, live deltas and periodic recovery for lossy presence events. */
@Singleton
class PresenceStore @Inject constructor(
    private val api: EchonApi,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _social = MutableStateFlow(PresenceState())
    val social = _social.asStateFlow()
    private val _servers = MutableStateFlow<Map<String, PresenceState>>(emptyMap())
    val servers = _servers.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val watchers = mutableMapOf<String, MutableSet<Any>>()
    private var connected = false
    private var generation = 0L
    private var revision = 0L
    private data class Change(val revision: Long, val status: String)
    private val changes = mutableMapOf<String, Change>()

    @Synchronized
    fun connect() {
        disconnect()
        connected = true
        poll(SOCIAL)
        watchers.keys.forEach(::poll)
    }

    @Synchronized
    fun disconnect() {
        generation++
        connected = false
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        changes.clear()
        _social.value = PresenceState()
        _servers.value = watchers.keys.associateWith { PresenceState() }
    }

    /** The caller's lifecycle owns the subscription, including cancellation on background. */
    suspend fun watchServer(serverId: String) {
        val token = Any()
        synchronized(this) {
            watchers.getOrPut(serverId) { mutableSetOf() }.add(token)
            if (connected && serverId !in jobs) poll(serverId)
        }
        try {
            awaitCancellation()
        } finally {
            synchronized(this) {
                watchers[serverId]?.let { readers ->
                    readers.remove(token)
                    if (readers.isEmpty()) {
                        watchers.remove(serverId)
                        jobs.remove(serverId)?.cancel()
                        _servers.value = _servers.value - serverId
                    }
                }
            }
        }
    }

    @Synchronized
    fun apply(userId: String, status: String) {
        if (!connected || userId.isBlank() || status !in PresenceStatus.supported) return
        changes[userId] = Change(++revision, status)
        _social.value = _social.value.copy(statuses = _social.value.statuses + (userId to status))
        _servers.value = _servers.value.mapValues { (_, state) ->
            state.copy(statuses = state.statuses + (userId to status))
        }
    }

    // Called with this monitor held. Each source has one serial request/poll loop.
    private fun poll(key: String) {
        val epoch = generation
        if (key != SOCIAL) _servers.value = _servers.value + (key to PresenceState())
        val job = scope.launch(start = CoroutineStart.LAZY) {
            while (isActive) {
                val before = synchronized(this@PresenceStore) { revision }
                try {
                    val response = if (key == SOCIAL) api.socialPresence() else api.serverPresence(key)
                    synchronized(this@PresenceStore) {
                        if (epoch == generation && isActive && connected) {
                            val statuses = response.presences.toMutableMap()
                            // A slow REST response must not undo newer socket events.
                            changes.filterValues { it.revision > before }.forEach { (id, change) -> statuses[id] = change.status }
                            publish(key, PresenceState(statuses, loaded = true))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    synchronized(this@PresenceStore) {
                        if (epoch == generation && isActive && connected) publish(key, PresenceState(failed = true))
                    }
                }
                delay(REFRESH_MS)
            }
        }
        jobs[key] = job
        job.start()
    }

    private fun publish(key: String, state: PresenceState) {
        if (key == SOCIAL) _social.value = state
        else _servers.value = _servers.value + (key to state)
    }

    private companion object {
        const val SOCIAL = "@social"
        const val REFRESH_MS = 60_000L
    }
}
