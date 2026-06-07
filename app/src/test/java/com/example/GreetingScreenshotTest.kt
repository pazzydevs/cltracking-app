package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

import com.example.data.CallEvent
import com.example.ui.CallHistoryItem

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleEvent = CallEvent(
        eventId = "test-uuid-1",
        deviceId = "dev-moto-92d",
        agentName = "Alice Agent",
        source = "WHATSAPP",
        contactName = "Sarah Connor",
        phoneNumber = "+14155552671",
        status = "INCOMING",
        timestamp = 1780416000000L, // Fixed timestamp for reproducible screenshots
        duration = 0L,
        isSynced = true
    )

    composeTestRule.setContent { 
      MyApplicationTheme { 
        CallHistoryItem(event = sampleEvent) 
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
