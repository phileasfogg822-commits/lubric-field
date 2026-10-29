package com.lubrcapp.lubrcapp.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Pure ISO-date <-> DatePicker-millis conversions. The picker works in
 * UTC-midnight millis, so both directions use ZoneOffset.UTC: converting
 * in the device zone would shift the picked day for zones ahead of UTC.
 */
object DatePickerDates {
    fun isoDateToUtcMillis(iso: String): Long? = runCatching {
        LocalDate.parse(iso.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()

    fun utcMillisToIsoDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
}

/**
 * A date field with a calendar picker. The text field stays editable
 * (typing a yyyy-MM-dd date still works exactly as before); the
 * trailing calendar button opens a Material3 DatePickerDialog, and
 * confirming writes the picked day as an ISO date string. An existing
 * value seeds the picker's selection; an unparseable value opens the
 * picker with no selection instead of crashing.
 */
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = stringResource(R.string.date_field_pick),
                )
            }
        },
        modifier = modifier.then(
            if (testTag != null) Modifier.testTag(testTag) else Modifier,
        ),
    )
    if (showDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DatePickerDates.isoDateToUtcMillis(value),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            onValueChange(DatePickerDates.utcMillisToIsoDate(it))
                        }
                        showDialog = false
                    },
                ) {
                    Text(stringResource(R.string.date_field_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.date_field_dismiss))
                }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}
