package com.echon.voice.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echon.voice.feature.chat.ChatScreen
import com.echon.voice.feature.moderation.BlockedUsersScreen

/**
 * Navigation for the signed-in area: a tab scaffold (servers/settings) plus
 * the chat screen and blocked-users screen pushed on top.
 */
@Composable
fun SignedInNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainScaffold(
                onOpenChannel = { id, name -> nav.navigate("chat/$id?channelName=$name") },
                onOpenBlockedUsers = { nav.navigate("blocked") },
            )
        }
        composable(
            route = "chat/{channelId}?channelName={channelName}",
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType; defaultValue = "channel" },
            ),
        ) {
            ChatScreen(onBack = { nav.popBackStack() })
        }
        composable("blocked") {
            BlockedUsersScreen(onBack = { nav.popBackStack() })
        }
    }
}
