package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.sales.PosCart
import com.lubrcapp.lubrcapp.domain.sales.PosCartLine
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 26.7 — the POS counter screen renders the cart,
 * its subtotal, and every counter action as a callback.
 */
@RunWith(AndroidJUnit4::class)
class PosCounterScreenTest {
    @get:Rule val compose = createComposeRule()

    private val pkr = CurrencyCode("PKR")

    private fun cart() = PosCart(
        id = "cart-1",
        lines = listOf(
            PosCartLine(
                id = "line-1",
                description = "Engine Oil",
                quantity = Quantity.of("2"),
                rate = Money.of("500", pkr),
            ),
            PosCartLine(
                id = "line-2",
                description = "Filter",
                quantity = Quantity.of("1"),
                rate = Money.of("300", pkr),
            ),
        ),
    )

    private fun setContent(
        cart: PosCart = cart(),
        onIncreaseQuantity: (String) -> Unit = {},
        onDecreaseQuantity: (String) -> Unit = {},
        onRemoveLine: (String) -> Unit = {},
        onHold: () -> Unit = {},
        onPay: () -> Unit = {},
        onSearchSubmit: (String) -> Unit = {},
        searchResults: List<ItemOption> = emptyList(),
        onAddSearchResult: (ItemOption) -> Unit = {},
    ) {
        compose.setContent {
            PosCounterScreen(
                cart = cart,
                currency = pkr,
                onBack = {},
                onIncreaseQuantity = onIncreaseQuantity,
                onDecreaseQuantity = onDecreaseQuantity,
                onRemoveLine = onRemoveLine,
                onHold = onHold,
                onPay = onPay,
                onSearchSubmit = onSearchSubmit,
                searchResults = searchResults,
                onAddSearchResult = onAddSearchResult,
            )
        }
    }

    @Test fun rendersLinesSubtotalAndActions() {
        setContent()
        compose.onNodeWithText("Engine Oil · 2 × 500 = 1000").assertIsDisplayed()
        compose.onNodeWithText("Filter · 1 × 300 = 300").assertIsDisplayed()
        compose.onNodeWithText("Subtotal: 1300").assertIsDisplayed()
        compose.onNodeWithText("Walk-in customer").assertIsDisplayed()
        compose.onNodeWithText("Hold").assertIsDisplayed()
        compose.onNodeWithText("Pay").assertIsDisplayed()
    }

    @Test fun quantityButtonsInvokeCallbacksWithLineId() {
        var increased: String? = null
        var decreased: String? = null
        setContent(
            onIncreaseQuantity = { increased = it },
            onDecreaseQuantity = { decreased = it },
        )
        compose.onNodeWithTag("pos_inc_line-2").performClick()
        compose.onNodeWithTag("pos_dec_line-1").performClick()
        assertEquals("line-2", increased)
        assertEquals("line-1", decreased)
    }

    @Test fun removeHoldAndPayInvokeCallbacks() {
        var removed: String? = null
        var held = 0
        var paid = 0
        setContent(
            onRemoveLine = { removed = it },
            onHold = { held++ },
            onPay = { paid++ },
        )
        compose.onNodeWithTag("pos_remove_line-1").performClick()
        compose.onNodeWithText("Hold").performClick()
        compose.onNodeWithText("Pay").performClick()
        assertEquals("line-1", removed)
        assertEquals(1, held)
        assertEquals(1, paid)
    }

    @Test fun searchSubmitInvokesCallbackWithQuery() {
        var submitted: String? = null
        setContent(onSearchSubmit = { submitted = it })
        compose.onNodeWithText("Search items").performTextInput("oil")
        compose.onNodeWithText("Search").performClick()
        assertEquals("oil", submitted)
    }

    @Test fun searchResultTapInvokesCallback() {
        var added: ItemOption? = null
        setContent(
            searchResults = listOf(ItemOption("item-1", "Engine Oil", BigDecimal("500"), null)),
            onAddSearchResult = { added = it },
        )
        compose.onNodeWithText("Engine Oil · 500").performClick()
        assertEquals("item-1", added?.id)
    }

    @Test fun emptyCartShowsEmptyMessage() {
        setContent(cart = PosCart(id = "cart-empty"))
        compose.onNodeWithText("No items yet").assertIsDisplayed()
        compose.onNodeWithText("Subtotal: 0").assertIsDisplayed()
    }
}
