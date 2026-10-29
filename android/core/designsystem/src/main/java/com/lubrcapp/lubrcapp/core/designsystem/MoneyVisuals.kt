package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

/**
 * Phase 33.1 — money visuals shared by every screen that shows
 * cash movement. The money semantics are fixed: IN is the
 * success green, OUT is the error red, matching the Khata
 * convention. Labels are caller parameters (never new strings)
 * so callers keep their exact wording.
 */
enum class MoneyDirection {
    IN,
    OUT,
    NEUTRAL,
}

/**
 * Bar lengths for a cash in/out pair: each amount as a fraction
 * of the larger one. Inputs are clamped at zero (a negative
 * amount is never a bar); both zero yields two empty bars.
 */
fun cashFlowFractions(
    moneyIn: BigDecimal,
    moneyOut: BigDecimal,
): Pair<Float, Float> {
    val inClamped = moneyIn.max(BigDecimal.ZERO)
    val outClamped = moneyOut.max(BigDecimal.ZERO)
    val max = inClamped.max(outClamped)
    if (max.compareTo(BigDecimal.ZERO) <= 0) return 0f to 0f
    return (inClamped.toFloat() / max.toFloat()) to
        (outClamped.toFloat() / max.toFloat())
}

@Composable
fun MoneyText(
    text: String,
    direction: MoneyDirection,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val color = when (direction) {
        MoneyDirection.IN -> LubrcTheme.tokens.success
        MoneyDirection.OUT -> MaterialTheme.colorScheme.error
        MoneyDirection.NEUTRAL -> Color.Unspecified
    }
    Text(text = text, modifier = modifier, style = style, color = color)
}

@Composable
fun CashFlowBars(
    inLabel: String,
    inAmountText: String,
    inFraction: Float,
    outLabel: String,
    outAmountText: String,
    outFraction: Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val success = LubrcTheme.tokens.success
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CashFlowBarRow(
            label = inLabel,
            amountText = inAmountText,
            fraction = inFraction,
            barColor = success,
            trackColor = scheme.surfaceVariant,
        )
        CashFlowBarRow(
            label = outLabel,
            amountText = outAmountText,
            fraction = outFraction,
            barColor = scheme.error,
            trackColor = scheme.surfaceVariant,
        )
    }
}

@Composable
private fun CashFlowBarRow(
    label: String,
    amountText: String,
    fraction: Float,
    barColor: Color,
    trackColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(text = amountText, style = MaterialTheme.typography.titleMedium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor),
            )
        }
    }
}
