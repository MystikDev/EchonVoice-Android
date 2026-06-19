package com.echon.voice.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echon.voice.feature.chat.ChatScreen
import com.echon.voice.feature.members.MembersScreen
import com.echon.voice.feature.moderation.BlockedUsersScreen
import com.echon.voice.feature.moderation.ReportSheet
import com.echon.voice.feature.moderation.ReportTarget
import com.echon.voice.feature.profiles.UserProfileSheet
import com.echon.voice.feature.voice.VoiceCallScreen
import com.echon.voice.model.User

/**
 * Signed-in navigation: tab scaffold + chat/members/blocked destinations, with
 * the reusable profile + report sheets hosted at the top so any destination can
 * surface them.
 */
@Composable
fun SignedInNavHost() {
    val nav = rememberNavController()
    var profileUser by remember { mutableStateOf<User?>(null) }
    var reportUser by remember { mutableStateOf<User?>(null) }

    fun openDm(channelId: String, name: String) {
        nav.navigate("chat/$channelId?channelName=$name&channelKind=dm")
    }

    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainScaffold(
                onOpenChannel = { id, name, kind -> nav.navigate("chat/$id?channelName=$name&channelKind=$kind") },
                onOpenVoice = { id, name -> nav.navigate("voice/$id?channelName=$name") },
                onOpenMembers = { nav.navigate("members/$it") },
                onOpenProfile = { profileUser = it },
                onOpenBlockedUsers = { nav.navigate("blocked") },
            )
        }
        composable(
            route = "chat/{channelId}?channelName={channelName}&channelKind={channelKind}",
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType; defaultValue = "channel" },
                navArgument("channelKind") { type = NavType.StringType; defaultValue = "server" },
            ),
        ) {
            ChatScreen(onBack = { nav.popBackStack() }, onOpenProfile = { profileUser = it })
        }
        composable(
            route = "members/{serverId}",
            arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
        ) {
            MembersScreen(onBack = { nav.popBackStack() }, onOpenProfile = { profileUser = it })
        }
        composable(
            route = "voice/{channelId}?channelName={channelName}",
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType; defaultValue = "voice" },
            ),
        ) {
            VoiceCallScreen(onBack = { nav.popBackStack() })
        }
        composable("blocked") {
            BlockedUsersScreen(onBack = { nav.popBackStack() })
        }
    }

    profileUser?.let { user ->
        UserProfileSheet(
            user = user,
            onOpenDm = { id, name -> profileUser = null; openDm(id, name) },
            onReport = { profileUser = null; reportUser = user },
            onDismiss = { profileUser = null },
        )
    }
    reportUser?.let { user ->
        ReportSheet(
            target = ReportTarget.UserTarget(user.id, user.username ?: "user"),
            onDismiss = { reportUser = null },
        )
    }
}
