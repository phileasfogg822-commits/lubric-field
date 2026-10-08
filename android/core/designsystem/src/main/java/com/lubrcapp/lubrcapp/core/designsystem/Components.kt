package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppButtonStyle = LubrcTheme.tokens.buttonStyle,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val tokens = LubrcTheme.tokens
    val sizedModifier = modifier.heightIn(min = tokens.minTouchTarget)

    when (style) {
        AppButtonStyle.OUTLINED -> OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            contentPadding = contentPadding,
            content = content,
        )

        AppButtonStyle.FLAT -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
            contentPadding = contentPadding,
            content = content,
        )

        AppButtonStyle.ELEVATED -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            elevation = ButtonDefaults.buttonElevation(),
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    style: AppCardStyle = LubrcTheme.tokens.cardStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (style) {
        AppCardStyle.OUTLINED -> OutlinedCard(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            content = content,
        )

        AppCardStyle.FLAT -> Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )

        AppCardStyle.DEFAULT -> Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(),
            content = content,
        )
    }
}

@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}
