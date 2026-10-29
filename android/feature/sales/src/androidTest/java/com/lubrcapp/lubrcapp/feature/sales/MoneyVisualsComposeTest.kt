package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.core.designsystem.BrandPalette
import com.lubrcapp.lubrcapp.core.designsystem.CashFlowBars
import com.lubrcapp.lubrcapp.core.designsystem.DesignSystemConfig
import com.lubrcapp.lubrcapp.core.designsystem.LubrcappTheme
import com.lubrcapp.lubrcapp.core.designsystem.MoneyDirection
import com.lubrcapp.lubrcapp.core.designsystem.MoneyText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyVisualsComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cashFlowBarsShowLabelsAndAmounts() {
        compose.setContent {
            LubrcappTheme(config = testDesignSystemConfig) {
                CashFlowBars(
                    inLabel = "Money in",
                    inAmountText = "5000 PKR",
                    inFraction = 1f,
                    outLabel = "Money out",
                    outAmountText = "1500 PKR",
                    outFraction = 0.3f,
                )
            }
        }
        compose.onNodeWithText("Money in").assertIsDisplayed()
        compose.onNodeWithText("5000 PKR").assertIsDisplayed()
        compose.onNodeWithText("Money out").assertIsDisplayed()
        compose.onNodeWithText("1500 PKR").assertIsDisplayed()
    }

    @Test
    fun moneyTextShowsDirectionAmounts() {
        compose.setContent {
            LubrcappTheme(config = testDesignSystemConfig) {
                Column {
                    MoneyText(text = "2180 PKR", direction = MoneyDirection.IN)
                    MoneyText(text = "14400 PKR", direction = MoneyDirection.OUT)
                }
            }
        }
        compose.onNodeWithText("2180 PKR").assertIsDisplayed()
        compose.onNodeWithText("14400 PKR").assertIsDisplayed()
    }

    private companion object {
        val testDesignSystemConfig = DesignSystemConfig(
            palette = BrandPalette(
                primary = 0xFF0F766E.toInt(),
                secondary = 0xFF14B8A6.toInt(),
                accent = 0xFFD97706.toInt(),
                success = 0xFF16A34A.toInt(),
                warning = 0xFFF59E0B.toInt(),
                error = 0xFFDC2626.toInt(),
            ),
        )
    }
}
