package com.echon.voice.feature.chat

/** Preserve intentional blank lines and spacing, including pasted Windows text. */
internal fun normalizeMessageText(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
