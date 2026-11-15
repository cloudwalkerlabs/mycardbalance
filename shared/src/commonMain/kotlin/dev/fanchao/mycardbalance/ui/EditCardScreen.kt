package dev.fanchao.mycardbalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanchao.mycardbalance.data.CardRepository
import dev.fanchao.mycardbalance.domain.CardNetwork
import dev.fanchao.mycardbalance.domain.CardNumber
import dev.fanchao.mycardbalance.domain.CardNumberResult
import dev.fanchao.mycardbalance.domain.Currency
import dev.fanchao.mycardbalance.domain.Expiry
import dev.fanchao.mycardbalance.domain.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EditCardViewModel(
    private val repository: CardRepository,
    /** Null when adding a new card. */
    val cardId: Long?,
    deviceCurrencyCode: String,
) : ViewModel() {
    var nickname by mutableStateOf("")
    var number by mutableStateOf("")
    /** Digits only, MMYY. */
    var expiry by mutableStateOf("")
    var currency by mutableStateOf(Currency.of(deviceCurrencyCode))
    var balance by mutableStateOf("")
    var loaded by mutableStateOf(cardId == null)
        private set

    /** Network from the original card, kept when the user only re-types the last four. */
    private var storedNetwork = CardNetwork.OTHER
    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            // New cards start in the device locale's currency (set in the constructor).
            if (cardId != null) {
                repository.observeCard(cardId).first()?.let { card ->
                    nickname = card.nickname
                    number = card.last4
                    expiry = card.expiry.display.filter(Char::isDigit)
                    currency = card.currency
                    storedNetwork = card.network
                }
                loaded = true
            }
        }
    }

    val numberResult: CardNumberResult
        get() = CardNumber.parse(number, storedNetwork)

    val previewLast4: String
        get() = (numberResult as? CardNumberResult.Valid)?.identity?.last4 ?: number.filter(Char::isDigit).takeLast(4)

    val previewNetwork: CardNetwork
        get() = (numberResult as? CardNumberResult.Valid)?.identity?.network ?: CardNumber.detectNetwork(number.filter(Char::isDigit))

    val nicknameError: String?
        get() = if (nickname.isBlank()) "Give the card a name you'll recognise" else null

    val numberError: String?
        get() = (numberResult as? CardNumberResult.Invalid)?.reason

    val expiryError: String?
        get() =
            when {
                expiry.length < 4 -> "Enter the expiry date shown on the card"
                Expiry.parse(expiry) == null -> "Month must be 01 to 12"
                else -> null
            }

    val balanceError: String?
        get() = if (cardId == null && Money.parse(balance, currency) == null) "Enter the card's current balance" else null

    fun save(onSaved: () -> Unit) {
        showErrors = true
        if (nicknameError != null || numberError != null || expiryError != null || balanceError != null || saving) return
        val parsedExpiry = Expiry.parse(expiry)!!
        val identity = (numberResult as CardNumberResult.Valid).identity
        saving = true
        viewModelScope.launch {
            if (cardId == null) {
                repository.addCard(nickname, identity, currency, parsedExpiry, Money.parse(balance, currency)!!)
            } else {
                repository.updateDetails(cardId, nickname, identity, currency, parsedExpiry)
            }
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardScreen(viewModel: EditCardViewModel, onClose: () -> Unit) {
    val vm = viewModel
    val adding = vm.cardId == null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (adding) "Add card" else "Edit card") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = "Close") }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = { vm.save(onClose) },
                    enabled = vm.loaded && !vm.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).height(52.dp),
                ) {
                    Text(if (adding) "Add card" else "Save changes")
                }
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            CardFace(
                nickname = vm.nickname,
                last4 = vm.previewLast4,
                network = vm.previewNetwork,
                expiry = vm.expiry.takeIf { it.isNotEmpty() }?.let { d -> d.take(2).padEnd(2, '•') + "/" + d.drop(2).padEnd(2, '•') } ?: "MM/YY",
                balance = if (adding) Money.parse(vm.balance, vm.currency)?.let { Money.format(it, vm.currency) } ?: Money.format(0, vm.currency) else null,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = vm.nickname,
                onValueChange = { vm.nickname = it.take(40) },
                label = { Text("Nickname") },
                placeholder = { Text("e.g. Birthday Prezzy") },
                isError = vm.showErrors && vm.nicknameError != null,
                supportingText = (vm.nicknameError?.takeIf { vm.showErrors })?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.number,
                onValueChange = { new -> vm.number = new.filter(Char::isDigit).take(19) },
                visualTransformation = CardNumberGrouping,
                label = { Text("Card number") },
                isError = vm.showErrors && vm.numberError != null,
                supportingText = {
                    Text(vm.numberError?.takeIf { vm.showErrors } ?: "Full number or last 4 digits. Only the last 4 are saved.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.expiry,
                onValueChange = { new -> vm.expiry = new.filter(Char::isDigit).take(4) },
                label = { Text("Expiry") },
                placeholder = { Text("MM/YY") },
                visualTransformation = ExpiryFormat,
                isError = vm.showErrors && vm.expiryError != null,
                supportingText = (vm.expiryError?.takeIf { vm.showErrors })?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            CurrencyPicker(vm.currency, onPick = { vm.currency = it })
            if (adding) {
                Spacer(Modifier.height(16.dp))
                AmountField(
                    value = vm.balance,
                    onValueChange = { vm.balance = it },
                    currency = vm.currency,
                    label = "Current balance",
                    error = vm.balanceError?.takeIf { vm.showErrors },
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CurrencyPicker(selected: Currency, onPick: (Currency) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        OutlinedTextField(
            value = "${selected.code} · ${selected.name}",
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            trailingIcon = { Icon(AppIcons.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // The read-only field would swallow taps, so a transparent layer on top opens the menu.
        Box(Modifier.matchParentSize().padding(top = 8.dp).clip(MaterialTheme.shapes.extraSmall).clickable { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { width.toDp() }).heightIn(max = 360.dp),
        ) {
            Currency.all.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.code} · ${c.name}") },
                    onClick = {
                        onPick(c)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Shows a digits-only card number as `4111 1111 1111 1111`. */
private object CardNumberGrouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = text.text.chunked(4).joinToString(" ")
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset + (offset - 1).coerceAtLeast(0) / 4
                override fun transformedToOriginal(offset: Int) = (offset - offset / 5).coerceIn(0, text.length)
            }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

/** Shows digits `MMYY` as `MM/YY`. */
private object ExpiryFormat : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        val out = if (t.length > 2) t.take(2) + "/" + t.drop(2) else t
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = if (offset > 2) offset + 1 else offset
                override fun transformedToOriginal(offset: Int) = (if (offset > 2) offset - 1 else offset).coerceIn(0, t.length)
            }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
