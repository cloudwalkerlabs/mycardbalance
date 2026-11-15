package dev.fanchao.mycardbalance.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.fanchao.mycardbalance.domain.Currency

/** A money input that only accepts digits and one decimal point, with the currency as prefix. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: Currency,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    /** Select the initial text, so typing replaces a pre-filled amount. */
    selectAllInitially: Boolean = false,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(value, if (selectAllInitially) TextRange(0, value.length) else TextRange(value.length)))
    }
    // Follow external changes, e.g. the "Use full balance" chip.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    OutlinedTextField(
        value = field,
        onValueChange = { new ->
            val filtered = new.text.filter { it.isDigit() || it == '.' }
            val decimals = filtered.substringAfter('.', "").length
            if (filtered.count { it == '.' } <= 1 && (currency.digits > 0 || '.' !in filtered) && decimals <= currency.digits) {
                field = new.copy(text = filtered)
                onValueChange(filtered)
            }
        },
        label = { Text(label) },
        prefix = { Text(currency.symbol) },
        suffix = { Text(currency.code) },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}
