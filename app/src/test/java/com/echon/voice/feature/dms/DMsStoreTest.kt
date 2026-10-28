package com.echon.voice.feature.dms

import com.echon.voice.core.network.EchonApi
import org.junit.Assert.assertFalse
import org.junit.Test
import java.lang.reflect.Proxy

class DMsStoreTest {
    private val unusedApi = Proxy.newProxyInstance(
        EchonApi::class.java.classLoader,
        arrayOf(EchonApi::class.java),
    ) { _, method, _ -> error("Unexpected API call: ${method.name}") } as EchonApi

    @Test
    fun `touch reports unknown conversations so realtime can refresh the DM list`() {
        val store = DMsStore(unusedApi)

        assertFalse(store.touch("new-dm", "2026-10-28T12:00:00Z"))
    }
}
