package com.echon.voice.nav

import androidx.lifecycle.ViewModel
import com.echon.voice.core.push.DeepLinkStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Exposes the pending notification deep-link to the signed-in nav host. */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val store: DeepLinkStore,
) : ViewModel() {
    val pending = store.pending
    fun consume() = store.consume()
}
