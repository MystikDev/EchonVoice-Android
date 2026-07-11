package com.echon.voice.core.storage

import android.content.Context
import com.echon.voice.core.designsystem.EchonPalettes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-sensitive UI preferences (plain SharedPreferences, unlike the encrypted
 * token store). Holds the 8-bit skin toggle and the chosen palette; exposed as
 * StateFlows so the theme recomposes the moment either changes.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("echon-appearance", Context.MODE_PRIVATE)

    private val _skinEnabled = MutableStateFlow(prefs.getBoolean(KEY_SKIN, false))
    val skinEnabled: StateFlow<Boolean> = _skinEnabled.asStateFlow()

    private val _paletteId = MutableStateFlow(prefs.getString(KEY_PALETTE, EchonPalettes.Default.id)!!)
    val paletteId: StateFlow<String> = _paletteId.asStateFlow()

    fun setSkinEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIN, enabled).apply()
        _skinEnabled.value = enabled
    }

    fun setPalette(id: String) {
        prefs.edit().putString(KEY_PALETTE, id).apply()
        _paletteId.value = id
    }

    private companion object {
        const val KEY_SKIN = "skin_enabled"
        const val KEY_PALETTE = "palette_id"
    }
}
