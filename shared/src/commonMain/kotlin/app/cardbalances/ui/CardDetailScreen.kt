package app.cardbalances.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cardbalances.WalletLauncher
import app.cardbalances.data.CardRepository
import app.cardbalances.domain.Card
import app.cardbalances.domain.CardEvent
import app.cardbalances.domain.EventType
import app.cardbalances.domain.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardDetailViewModel(private val repository: CardRepository, val cardId: Long) : ViewModel() {
    val card: StateFlow<Card?> =
        repository.observeCard(cardId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val events: StateFlow<List<CardEvent>> =
        repository.observeEvents(cardId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun redeem(amountMinor: Long, note: String): Result<Long> = repository.redeem(cardId, amountMinor, note)

    suspend fun setBalance(balanceMinor: Long, note: String) = repository.setBalance(cardId, balanceMinor, note)

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    wallet: WalletLauncher,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<AmountSheetMode?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.nickname ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(AppIcons.Edit, contentDescription = "Edit card") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete card") },
                                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val c = card ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                CardFace(c.nickname, c.last4, c.network, Money.format(c.balanceMinor, c.currency), dimmed = c.balanceMinor == 0L)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { sheet = AmountSheetMode.Redeem },
                        enabled = c.balanceMinor > 0,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(AppIcons.Remove, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Spend")
                    }
                    OutlinedButton(onClick = { sheet = AmountSheetMode.SetBalance }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(AppIcons.Edit, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Set balance")
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        if (!wallet.open()) scope.launch { snackbar.showSnackbar("Couldn't open ${wallet.walletName}") }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(AppIcons.Wallet, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Pay with ${wallet.walletName}")
                }
                Spacer(Modifier.height(28.dp))
                Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(events, key = { it.id }) { e ->
                EventRow(e, c)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }

    val c = card
    if (sheet != null && c != null) {
        AmountSheet(
            mode = sheet!!,
            card = c,
            onDismiss = { sheet = null },
            onSubmit = { mode, amount, note ->
                when (mode) {
                    AmountSheetMode.Redeem -> viewModel.redeem(amount, note).exceptionOrNull()?.message
                    AmountSheetMode.SetBalance -> {
                        viewModel.setBalance(amount, note)
                        null
                    }
                }
            },
        )
    }

    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${c.nickname}?") },
            text = { Text("The card ending ${c.last4} and its history will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onBack) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EventRow(e: CardEvent, card: Card) {
    val (icon, title) =
        when (e.type) {
            EventType.REDEEM -> AppIcons.Receipt to "Spent"
            EventType.SET_BALANCE -> AppIcons.Edit to "Balance updated"
        }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.note ?: title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.note != null) "$title · ${formatTimestamp(e.createdAt)}" else formatTimestamp(e.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val amount = Money.format(e.deltaMinor, card.currency, showSign = true)
            Text(
                amount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (e.deltaMinor < 0) MaterialTheme.colorScheme.onSurface else PositiveGreen,
            )
            Text(
                "Left ${Money.format(e.balanceAfterMinor, card.currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PositiveGreen = Color(0xFF2E9D68)
