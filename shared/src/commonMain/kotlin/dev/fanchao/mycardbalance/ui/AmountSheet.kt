package dev.fanchao.mycardbalance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.fanchao.mycardbalance.domain.Card
import dev.fanchao.mycardbalance.domain.Money
import kotlinx.coroutines.launch

enum class AmountSheetMode { Redeem, SetBalance }

/**
 * Bottom sheet for entering an amount. [onSubmit] returns an error message to show, or null when
 * the change was saved and the sheet can close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountSheet(
    mode: AmountSheetMode,
    card: Card,
    onDismiss: () -> Unit,
    onSubmit: suspend (AmountSheetMode, Long, String) -> String?,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val currency = card.currency
    var amount by rememberSaveable {
        mutableStateOf(if (mode == AmountSheetMode.SetBalance) Money.formatPlain(card.balanceMinor, currency) else "")
    }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val parsed = Money.parse(amount, currency)
    val after =
        when (mode) {
            AmountSheetMode.Redeem -> parsed?.let { card.balanceMinor - it }
            AmountSheetMode.SetBalance -> parsed
        }
    val overspend = mode == AmountSheetMode.Redeem && after != null && after < 0
    val valid = parsed != null && !overspend && (mode == AmountSheetMode.SetBalance || parsed > 0)

    fun submit() {
        if (!valid) return
        scope.launch {
            val err = onSubmit(mode, parsed!!, note)
            if (err != null) error = err
            else {
                state.hide()
                onDismiss()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding()) {
            Text(
                if (mode == AmountSheetMode.Redeem) "Record a purchase" else "Set balance",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${card.nickname} · •••• ${card.last4} · ${Money.format(card.balanceMinor, currency)} left",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            AmountField(
                value = amount,
                onValueChange = {
                    amount = it
                    error = null
                },
                currency = currency,
                label = if (mode == AmountSheetMode.Redeem) "Amount spent" else "New balance",
                error = error ?: if (overspend) "Only ${Money.format(card.balanceMinor, currency)} left on this card" else null,
                supporting = after?.takeIf { !overspend && mode == AmountSheetMode.Redeem }?.let { "Balance after: ${Money.format(it, currency)}" }
                    ?: after?.takeIf { mode == AmountSheetMode.SetBalance && it != card.balanceMinor }?.let {
                        val diff = it - card.balanceMinor
                        "Change: ${Money.format(diff, currency, showSign = true)}"
                    },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                selectAllInitially = mode == AmountSheetMode.SetBalance,
            )
            if (mode == AmountSheetMode.Redeem) {
                Row {
                    AssistChip(
                        onClick = { amount = Money.formatPlain(card.balanceMinor, currency) },
                        label = { Text("Use full balance") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(60) },
                label = { Text("Note (optional)") },
                placeholder = { Text(if (mode == AmountSheetMode.Redeem) "e.g. Groceries" else "e.g. Checked receipt") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = ::submit, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (mode == AmountSheetMode.Redeem) "Deduct" else "Save balance")
            }
        }
    }
}
