package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.components.WhatsAppGroupInviteModal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommunityInviteModalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCloseButtonClickTriggersDismiss() {
        var dismissedValue: Boolean? = null
        var isModalVisible by mutableStateOf(true)

        composeTestRule.setContent {
            WhatsAppGroupInviteModal(
                visible = isModalVisible,
                onDismiss = { shouldDismissForever ->
                    dismissedValue = shouldDismissForever
                    isModalVisible = false
                }
            )
        }

        // Click the close 'X' button
        composeTestRule.onNodeWithTag("community_modal_close_button").performClick()

        // Verify dismissal was called with false (since checkbox was unchecked)
        assertEquals(false, dismissedValue)
    }

    @Test
    fun testDontShowAgainCheckboxAndCloseButtonClick() {
        var dismissedValue: Boolean? = null
        var isModalVisible by mutableStateOf(true)

        composeTestRule.setContent {
            WhatsAppGroupInviteModal(
                visible = isModalVisible,
                onDismiss = { shouldDismissForever ->
                    dismissedValue = shouldDismissForever
                    isModalVisible = false
                }
            )
        }

        // Toggle "Não mostrar novamente"
        composeTestRule.onNodeWithTag("community_modal_dont_show_again").performClick()

        // Click the close 'X' button
        composeTestRule.onNodeWithTag("community_modal_close_button").performClick()

        // Verify dismissal was called with true (should persist preference)
        assertEquals(true, dismissedValue)
    }
}
