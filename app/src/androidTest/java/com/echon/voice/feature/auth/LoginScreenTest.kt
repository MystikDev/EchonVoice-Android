package com.echon.voice.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.echon.voice.core.network.ApiException
import com.echon.voice.model.EmailLoginChallenge
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun emailCodeCanBeRetriedResentAndVerified() {
        var loginRequests = 0
        val verifications = mutableListOf<Pair<String, String>>()
        val model = LoginViewModel({ _, _ ->
            EmailLoginChallenge("challenge-${++loginRequests}")
        }, { id, code ->
            verifications += id to code
            if (code != "012345") throw ApiException.Http(400, "invalid_or_expired_code")
        })
        compose.setContent { MaterialTheme { LoginScreen({}, viewModel = model) } }
        compose.onNodeWithText("Email").performTextInput("member@example.test")
        compose.onNodeWithText("Password").performTextInput("password")
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        compose.onNodeWithText("Check your email").assertIsDisplayed()
        compose.onNodeWithText("Verify and sign in").assertIsNotEnabled()
        compose.onNodeWithText("Verification code").performScrollTo().performTextInput("000000")
        compose.onNodeWithText("Verify and sign in").performScrollTo().performClick()
        compose.onNodeWithText("That code is incorrect or has expired. Try again or request a new code.")
            .assertExists()
        compose.onNodeWithText("Send a new code").performScrollTo().performClick()
        compose.onNodeWithText("A new code has been sent. Use the latest email.").assertExists()
        compose.onNodeWithText("Verify and sign in").assertIsNotEnabled()
        compose.onNodeWithText("Verification code").performScrollTo().performTextInput("012345")
        compose.onNodeWithText("Verify and sign in").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, loginRequests)
            assertEquals(listOf("challenge-1" to "000000", "challenge-2" to "012345"), verifications)
        }
    }

    @Test fun backToSignInClearsThePendingCodeAndPassword() {
        val model = LoginViewModel({ _, _ -> EmailLoginChallenge("id") }, { _, _ -> })
        compose.setContent { MaterialTheme { LoginScreen({}, viewModel = model) } }
        compose.onNodeWithText("Email").performTextInput("member@example.test")
        compose.onNodeWithText("Password").performTextInput("password")
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        compose.onNodeWithText("Verification code").performScrollTo().performTextInput("123456")
        compose.onNodeWithText("Back to sign in").performScrollTo().performClick()
        compose.onNodeWithText("Verification code").assertDoesNotExist()
        compose.onNodeWithText("Password").assertExists()
        compose.onNodeWithText("Sign in").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals("", model.password)
            assertEquals("", model.code)
        }
    }
}
