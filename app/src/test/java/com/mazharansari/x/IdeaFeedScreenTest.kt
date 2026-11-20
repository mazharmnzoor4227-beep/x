package com.mazharansari.x

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.data.FeedRefreshState
import com.mazharansari.x.data.Idea
import com.mazharansari.x.data.IdeaFeed
import com.mazharansari.x.data.Role
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.workspace.IDEAS_STORE_EMPTY
import com.mazharansari.x.ui.workspace.IDEAS_STORE_SECTION
import com.mazharansari.x.ui.workspace.IDEAS_YOURS_EMPTY
import com.mazharansari.x.ui.workspace.IDEAS_YOURS_SECTION
import com.mazharansari.x.ui.workspace.IdeasScreen
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Idea store feed — the Ideas screen (Robolectric + Compose). Proves the
 * two sections stay separate, the empty state is honest (no fake ideas),
 * a failed refresh is visible, and tapping "Start work" on a store idea
 * opens a fresh X chat with the idea sent as the first message.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w404dp-h897dp-xxhdpi", application = AxionApp::class)
class IdeaFeedScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val storeIdeaText = "Build a masala price tracker app for the shop in Karachi"
    private val userIdeaText = "Solar charger for the shop"

    @Before fun cleanStores() {
        File(app.filesDir, "ideas.json").delete()
        File(app.filesDir, "idea-feed-state.json").delete()
    }

    private fun seedStoreAndUser() {
        val now = System.currentTimeMillis()
        val ideas = listOf(
            Idea(id = "s1", text = storeIdeaText, area = "App", createdAt = now, source = IdeaFeed.SOURCE_STORE),
            Idea(id = "u1", text = userIdeaText, area = "You", createdAt = now - 1_000, source = IdeaFeed.SOURCE_USER),
        )
        File(app.filesDir, "ideas.json").writeText(json.encodeToString(ListSerializer(Idea.serializer()), ideas))
    }

    private fun vm() = ChatViewModel(app).also { it.debugOnline = false }

    @Test fun storeAndUserIdeasAreInSeparateSections() {
        seedStoreAndUser()
        val viewModel = vm()
        compose.setContent { IdeasScreen(viewModel, back = {}, navigate = {}) }
        compose.onNodeWithText(IDEAS_STORE_SECTION).assertIsDisplayed()
        compose.onNodeWithText(IDEAS_YOURS_SECTION).assertIsDisplayed()
        compose.onNodeWithText(storeIdeaText).assertIsDisplayed()
        compose.onNodeWithText(userIdeaText).assertIsDisplayed()
        compose.onNodeWithText("App ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Not updated yet").assertIsDisplayed()
    }

    @Test fun emptyStoreIsHonestAndShowsGenerateNow() {
        val viewModel = vm()
        compose.setContent { IdeasScreen(viewModel, back = {}, navigate = {}) }
        compose.onNodeWithText(IDEAS_STORE_EMPTY).assertIsDisplayed()
        compose.onNodeWithText("Generate now").assertIsDisplayed()
        compose.onNodeWithText("New ideas").assertIsDisplayed()
        compose.onNodeWithText(IDEAS_YOURS_EMPTY).assertIsDisplayed()
    }

    @Test fun failedRefreshIsShownOnScreen() {
        seedStoreAndUser()
        val now = System.currentTimeMillis()
        val state = FeedRefreshState(lastAttemptAtMs = now, lastFailureAtMs = now, lastError = "No AI model is available on this phone right now")
        File(app.filesDir, "idea-feed-state.json").writeText(json.encodeToString(state))
        val viewModel = vm()
        compose.setContent { IdeasScreen(viewModel, back = {}, navigate = {}) }
        compose.onNodeWithText("Couldn't refresh", substring = true).assertIsDisplayed()
        compose.onNodeWithText("No AI model is available", substring = true).assertIsDisplayed()
        // Yesterday's ideas are still there despite the failure.
        compose.onNodeWithText(storeIdeaText).assertIsDisplayed()
    }

    @Test fun startWorkOnAStoreIdeaOpensChatWithTheIdeaSent() {
        seedStoreAndUser()
        val viewModel = vm()
        var backed = false
        compose.setContent { IdeasScreen(viewModel, back = { backed = true }, navigate = {}) }
        // The store section comes first, so the first "Start work" is the store idea's.
        compose.onAllNodesWithText("Start work")[0].performClick()
        assertTrue("tapping Start work must leave the Ideas screen for chat", backed)
        assertNotNull("a fresh chat must be open", viewModel.conversationId)
        val sent = viewModel.messages.firstOrNull { it.role == Role.USER }
        assertNotNull("the idea must be sent as the first message", sent)
        assertEquals(IdeaFeed.startWorkMessage(storeIdeaText), sent!!.text)
    }

    @Test fun startWorkOnAUserIdeaSendsThatIdeaToo() {
        seedStoreAndUser()
        val viewModel = vm()
        compose.setContent { IdeasScreen(viewModel, back = {}, navigate = {}) }
        // The second "Start work" is the user idea's (store section first).
        compose.onAllNodesWithText("Start work")[1].performClick()
        val sent = viewModel.messages.firstOrNull { it.role == Role.USER }
        assertNotNull("the user idea must be sent as the first message", sent)
        assertEquals(IdeaFeed.startWorkMessage(userIdeaText), sent!!.text)
    }
}
