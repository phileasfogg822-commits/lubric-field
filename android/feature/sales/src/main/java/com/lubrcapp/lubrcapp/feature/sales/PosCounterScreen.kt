package com.lubrcapp.lubrcapp.feature.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.sales.PosCart
import com.lubrcapp.lubrcapp.domain.sales.PosCartOps
import com.lubrcapp.lubrcapp.domain.sales.PosLineDiscount
import com.lubrcapp.lubrcapp.domain.sales.PosParty

/**
 * Phase 26.7 — the POS counter screen (§147): the cart's
 * lines with quantity controls, the running subtotal, and
 * the Scan / Search / Quantity / Discount / Hold / Pay /
 * Print actions the counter needs.
 *
 * Scan is NOT built here: barcode scanning stays the
 * Phase 16 owner decision (exact-match rule, camera
 * scanning undecided), so the screen offers Search (a
 * query field the shell wires to item lookup) and leaves
 * scanning out rather than shipping a half-scan. Discount
 * shows per line (applied through 26.1's cart ops by the
 * shell); the screen itself edits quantities, removes
 * lines, holds, and pays. Pay hands the cart to the
 * shell, which renders it through the 25.x print chain.
 * The screen is stateless over [cart]: every action is a
 * callback, so the shell owns the cart state. Search
 * results arrive as props: the shell filters the item
 * flow to priced matches (items without a sale rate
 * cannot be added to a cart), and tapping a result adds
 * it to the cart.
 */
@Composable
fun PosCounterScreen(
    cart: PosCart,
    currency: CurrencyCode = CurrencyCode("XXX"),
    onBack: () -> Unit,
    onIncreaseQuantity: (lineId: String) -> Unit,
    onDecreaseQuantity: (lineId: String) -> Unit,
    onRemoveLine: (lineId: String) -> Unit,
    onHold: () -> Unit,
    onPay: () -> Unit,
    onSearchSubmit: (query: String) -> Unit,
    searchResults: List<ItemOption> = emptyList(),
    onAddSearchResult: (ItemOption) -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.pos_back)) }
            Text(stringResource(R.string.pos_title))
        }
        Text(
            when (val party = cart.party) {
                is PosParty.WalkIn -> stringResource(R.string.pos_party_walkin)
                is PosParty.Named -> stringResource(R.string.pos_party_named, party.id.value)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.pos_search_label)) },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onSearchSubmit(searchQuery) }) {
                Text(stringResource(R.string.pos_search))
            }
        }
        searchResults.forEach { option ->
            TextButton(onClick = { onAddSearchResult(option) }) {
                Text(
                    stringResource(
                        R.string.pos_search_result,
                        option.name,
                        option.saleRate?.toPlainString() ?: "",
                    ),
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cart.lines.isEmpty()) {
                    Text(stringResource(R.string.pos_empty))
                } else {
                    cart.lines.forEach { line ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(
                                    R.string.pos_line,
                                    line.description,
                                    line.quantity.amount.toPlainString(),
                                    line.rate.amount.toPlainString(),
                                    PosCartOps.lineAmount(line).amount.toPlainString(),
                                ),
                            )
                            line.discount?.let { discount ->
                                Text(
                                    when (discount) {
                                        is PosLineDiscount.Percent ->
                                            stringResource(
                                                R.string.pos_discount_percent,
                                                discount.percentage.points.toPlainString(),
                                            )
                                        is PosLineDiscount.Fixed ->
                                            stringResource(
                                                R.string.pos_discount_fixed,
                                                discount.amount.amount.toPlainString(),
                                            )
                                    },
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { onDecreaseQuantity(line.id) },
                                    modifier = Modifier.testTag("pos_dec_${line.id}"),
                                ) { Text(stringResource(R.string.pos_decrease)) }
                                TextButton(
                                    onClick = { onIncreaseQuantity(line.id) },
                                    modifier = Modifier.testTag("pos_inc_${line.id}"),
                                ) { Text(stringResource(R.string.pos_increase)) }
                                TextButton(
                                    onClick = { onRemoveLine(line.id) },
                                    modifier = Modifier.testTag("pos_remove_${line.id}"),
                                ) { Text(stringResource(R.string.pos_remove_line)) }
                            }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(
                R.string.pos_subtotal,
                PosCartOps.subtotal(cart, currency).amount.toPlainString(),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onHold, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pos_hold))
            }
            Button(onClick = onPay, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pos_pay))
            }
        }
    }
}
