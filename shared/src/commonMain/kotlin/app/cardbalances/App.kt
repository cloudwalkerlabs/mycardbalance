package app.cardbalances

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.cardbalances.ui.AppTheme
import app.cardbalances.ui.CardDetailScreen
import app.cardbalances.ui.CardDetailViewModel
import app.cardbalances.ui.CardListScreen
import app.cardbalances.ui.CardListViewModel
import app.cardbalances.ui.EditCardScreen
import app.cardbalances.ui.EditCardViewModel

sealed interface Route {
    data object Cards : Route

    data class Detail(val id: Long) : Route

    /** [id] null means a new card. */
    data class Edit(val id: Long?) : Route
}

/** Saves the back stack as strings so it survives process death without a serialization plugin. */
private val BackStackSaver =
    listSaver<SnapshotStateList<Route>, String>(
        save = { stack ->
            stack.map {
                when (it) {
                    Route.Cards -> "cards"
                    is Route.Detail -> "detail:${it.id}"
                    is Route.Edit -> "edit:${it.id ?: ""}"
                }
            }
        },
        restore = { saved ->
            saved.mapNotNull { s ->
                val (kind, arg) = (s.split(':', limit = 2) + "").let { it[0] to it[1] }
                when (kind) {
                    "cards" -> Route.Cards
                    "detail" -> arg.toLongOrNull()?.let(Route::Detail)
                    "edit" -> Route.Edit(arg.toLongOrNull())
                    else -> null
                }
            }.ifEmpty { listOf(Route.Cards) }.toMutableStateList()
        },
    )

private fun List<Route>.toMutableStateList() = mutableStateListOf<Route>().also { it.addAll(this) }

@Composable
fun App(deps: AppDependencies) {
    AppTheme {
        val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Route>(Route.Cards) }
        fun pop() {
            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        }

        NavDisplay(
            backStack = backStack,
            onBack = { pop() },
            entryDecorators =
                listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            transitionSpec = {
                slideInHorizontally { it / 4 } + fadeIn() togetherWith slideOutHorizontally { -it / 4 } + fadeOut()
            },
            popTransitionSpec = {
                slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it / 4 } + fadeOut()
            },
            predictivePopTransitionSpec = {
                slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it / 4 } + fadeOut()
            },
            entryProvider =
                entryProvider {
                    entry<Route.Cards> {
                        CardListScreen(
                            viewModel = viewModel { CardListViewModel(deps.repository) },
                            onOpenCard = { backStack.add(Route.Detail(it)) },
                            onAddCard = { backStack.add(Route.Edit(null)) },
                        )
                    }
                    entry<Route.Detail> { route ->
                        CardDetailScreen(
                            viewModel = viewModel { CardDetailViewModel(deps.repository, route.id) },
                            wallet = deps.wallet,
                            onBack = { pop() },
                            onEdit = { backStack.add(Route.Edit(route.id)) },
                        )
                    }
                    entry<Route.Edit>(
                        metadata =
                            NavDisplay.transitionSpec {
                                slideInVertically { it / 3 } + fadeIn() togetherWith ExitTransition.KeepUntilTransitionsFinished
                            } +
                                NavDisplay.popTransitionSpec {
                                    EnterTransition.None togetherWith slideOutVertically { it / 3 } + fadeOut()
                                }
                    ) { route ->
                        EditCardScreen(
                            viewModel = viewModel { EditCardViewModel(deps.repository, route.id, deps.deviceCurrencyCode) },
                            onClose = { pop() },
                        )
                    }
                },
        )
    }
}
