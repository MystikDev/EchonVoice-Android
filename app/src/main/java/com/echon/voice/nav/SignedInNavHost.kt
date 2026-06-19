package com.echon.voice.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.echon.voice.feature.moderation.BlockedUsersScreen
import com.echon.voice.feature.settings.SettingsScreen

/**
 * Navigation for the signed-in area. Phase 2 ships Settings + Blocked Users; the
 * full tab bar (servers / DMs / friends / settings) arrives with Phase 3.
 */
@Composable
fun SignedInNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "settings") {
        composable("settings") {
            SettingsScreen(onOpenBlockedUsers = { nav.navigate("blocked") })
        }
        composable("blocked") {
            BlockedUsersScreen(onBack = { nav.popBackStack() })
        }
    }
}
