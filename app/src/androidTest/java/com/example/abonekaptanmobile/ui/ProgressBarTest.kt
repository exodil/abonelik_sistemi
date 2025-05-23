package com.example.abonekaptanmobile.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.abonekaptanmobile.ui.components.HorizontalProgressBar
import com.example.abonekaptanmobile.ui.theme.AboneKaptanMobileTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ProgressBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun horizontalProgressBar_displaysCorrectTexts_andProgress() {
        val progress = 0.75f
        val progressText = "Yükleniyor: 75%"
        val timeRemainingText = "1 dk 30 sn"

        composeTestRule.setContent {
            AboneKaptanMobileTheme { // Apply theme for consistent styling
                HorizontalProgressBar(
                    progress = progress,
                    progressText = progressText,
                    estimatedTimeRemainingText = timeRemainingText
                )
            }
        }

        // Assert progress text is displayed
        composeTestRule.onNodeWithText(progressText).assertIsDisplayed()

        // Assert time remaining text is displayed with label
        composeTestRule.onNodeWithText("Kalan Süre: $timeRemainingText").assertIsDisplayed()

        // Assert progress of the LinearProgressIndicator
        // Note: Directly verifying the progress value of LinearProgressIndicator can be complex
        // as it might not expose its progress directly in a way that's easily testable
        // through standard semantic properties for the exact float value.
        // However, we can check if a progress bar exists and has the correct range info.
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(progress, 0f..1f)))
            .assertIsDisplayed()
    }

    @Test
    fun horizontalProgressBar_noTimeRemainingText_whenNullOrEmpty() {
        val progress = 0.25f
        val progressText = "Analiz ediliyor: 25%"

        // Test with null timeRemainingText
        composeTestRule.setContent {
            AboneKaptanMobileTheme {
                HorizontalProgressBar(
                    progress = progress,
                    progressText = progressText,
                    estimatedTimeRemainingText = null
                )
            }
        }
        composeTestRule.onNodeWithText(progressText).assertIsDisplayed()
        // Check that no node with the "Kalan Süre:" prefix exists
        composeTestRule.onNodeWithText("Kalan Süre:", substring = true).assertDoesNotExist()


        // Test with empty timeRemainingText
        composeTestRule.setContent {
            AboneKaptanMobileTheme {
                HorizontalProgressBar(
                    progress = progress,
                    progressText = progressText,
                    estimatedTimeRemainingText = ""
                )
            }
        }
        composeTestRule.onNodeWithText(progressText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Kalan Süre:", substring = true).assertDoesNotExist()
    }
    
    @Test
    fun horizontalProgressBar_displaysCorrectTexts_atZeroProgress() {
        val progress = 0.0f
        val progressText = "Başlatılıyor..."
        val timeRemainingText = "Hesaplanıyor..."

        composeTestRule.setContent {
            AboneKaptanMobileTheme {
                HorizontalProgressBar(
                    progress = progress,
                    progressText = progressText,
                    estimatedTimeRemainingText = timeRemainingText
                )
            }
        }
        composeTestRule.onNodeWithText(progressText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Kalan Süre: $timeRemainingText").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(progress, 0f..1f)))
            .assertIsDisplayed()
    }

    @Test
    fun horizontalProgressBar_displaysCorrectTexts_atFullProgress() {
        val progress = 1.0f
        val progressText = "Tamamlandı: 100%"
        val timeRemainingText = "Bitti!"

        composeTestRule.setContent {
            AboneKaptanMobileTheme {
                HorizontalProgressBar(
                    progress = progress,
                    progressText = progressText,
                    estimatedTimeRemainingText = timeRemainingText
                )
            }
        }
        composeTestRule.onNodeWithText(progressText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Kalan Süre: $timeRemainingText").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(progress, 0f..1f)))
            .assertIsDisplayed()
    }
}
