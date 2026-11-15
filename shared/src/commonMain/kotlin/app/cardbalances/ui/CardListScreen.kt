package app.cardbalances.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.cardbalances.domain.CardSort
import app.cardbalances.domain.CardSortField
import app.cardbalances.domain.Expiry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cardbalances.data.CardRepository
import app.cardbalances.domain.Card
import app.cardbalances.domain.CardNetwork
import app.cardbalances.domain.Currency
import app.cardbalances.domain.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CardListState(
    val cards: List<Card>,
    /** Per-currency totals of cards that still have money and haven't expired. */
    val totals: List<Pair<Currency, Long>>,
    val activeCount: Int,
    val sort: CardSort,
)

class CardListViewModel(private val repository: CardRepository) : ViewModel() {
    /** Null while the first query is loading, so we don't flash the empty state. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CardListState?> =
        repository
            .observeSort()
            .flatMapLatest { sort -> repository.observeCards(sort).map { cards -> sort to cards } }
            .map { (sort, cards) ->
                val today = Expiry.today()
                val active = cards.filter { it.balanceMinor > 0 && !it.expiry.isExpired(today) }
                val totals =
                    active.groupBy { it.currency.code }
                        .map { (_, group) -> group.first().currency to group.sumOf { it.balanceMinor } }
                        .sortedByDescending { it.second }
                CardListState(cards, totals, active.size, sort)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(sort: CardSort) {
        viewModelScope.launch { repository.setSort(sort) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(viewModel: CardListViewModel, onOpenCard: (Long) -> Unit, onAddCard: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("My cards") },
                actions = { state?.let { s -> if (s.cards.size > 1) SortMenu(s.sort, viewModel::setSort) } },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state?.cards?.isNotEmpty() == true) {
                ExtendedFloatingActionButton(
                    onClick = onAddCard,
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text("Add card") },
                )
            }
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        if (s.cards.isEmpty()) {
            EmptyState(onAddCard, Modifier.padding(padding))
        } else {
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { TotalsHeader(s) }
                items(s.cards, key = { it.id }) { card -> CardRow(card, onClick = { onOpenCard(card.id) }) }
            }
        }
    }
}

@Composable
private fun TotalsHeader(state: CardListState) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Total available",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(4.dp))
            if (state.totals.isEmpty()) {
                Text(
                    "Nothing left to spend",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            state.totals.forEachIndexed { i, (currency, total) ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        Money.format(total, currency),
                        style = if (i == 0) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        currency.code,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = if (i == 0) 6.dp else 3.dp),
                    )
                }
            }
            if (state.totals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "across ${state.activeCount} ${if (state.activeCount == 1) "card" else "cards"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun SortMenu(sort: CardSort, onSort: (CardSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AppIcons.Sort, contentDescription = "Sort cards") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading("Sort by")
            CardSortField.entries.forEach { field ->
                SortItem(field.label, selected = sort.field == field) {
                    // Each field starts in the direction people usually want for it.
                    onSort(CardSort(field, ascending = field == CardSortField.EXPIRY))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuHeading("Order")
            SortItem(sort.field.ascendingLabel, selected = sort.ascending) { onSort(sort.copy(ascending = true)) }
            SortItem(sort.field.descendingLabel, selected = !sort.ascending) { onSort(sort.copy(ascending = false)) }
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SortItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        trailingIcon = {
            if (selected) Icon(AppIcons.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        },
        onClick = onClick,
    )
}

@Composable
private fun CardRow(card: Card, onClick: () -> Unit) {
    val expired = card.expiry.isExpired()
    val empty = card.balanceMinor == 0L || expired
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(card.last4, dimmed = empty)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    card.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        if (card.network != CardNetwork.OTHER) append(card.network.label).append(" ")
                        append("•••• ").append(card.last4)
                        if (!expired) append(" · Exp ").append(card.expiry.display)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(card.balanceMinor, card.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        expired -> "Expired ${card.expiry.display}"
                        card.balanceMinor == 0L -> "Used up"
                        else -> card.currency.code
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAddCard: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CardFace("Gift card", "", CardNetwork.OTHER, balance = null, modifier = Modifier.widthIn(max = 260.dp))
            Spacer(Modifier.height(28.dp))
            Text("No cards yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add a prepaid card with its balance, then deduct what you spend so you always know what's left.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAddCard) {
                Icon(AppIcons.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Add a card")
            }
        }
    }
}
