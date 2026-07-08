package com.echon.voice.core.updateapi

import android.content.Context
import androidx.compose.runtime.Composable
import com.echon.voice.core.update.UpdateScheduler
import com.echon.voice.feature.update.UpdatePrompt

/**
 * Direct-download flavor: wires the in-app self-updater. This seam lets shared
 * code (`EchonApp`, `AppRoot`) call into the updater without depending on the
 * update classes, which live only in this flavor — the Play flavor supplies a
 * no-op equivalent (Play prohibits self-updating apps and delivers updates
 * itself).
 */
fun scheduleAppUpdates(context: Context) = UpdateScheduler.schedule(context)

@Composable
fun UpdateGate() = UpdatePrompt()
