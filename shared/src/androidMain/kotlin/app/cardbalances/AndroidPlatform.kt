package app.cardbalances

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import app.cardbalances.data.CardDatabase
import app.cardbalances.data.CardRepository
import app.cardbalances.data.configure
import java.util.Locale

/**
 * Opens Google Wallet. Wallet has no public API for picking a specific card to pay with, so we
 * open the app and the user swipes to the card ending in the digits we showed them.
 */
class GoogleWalletLauncher(private val context: Context) : WalletLauncher {
    override val walletName = "Google Wallet"

    override fun open(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(WALLET_PACKAGE)
        val intents =
            listOfNotNull(
                launch,
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$WALLET_PACKAGE")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$WALLET_PACKAGE")),
            )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {}
        }
        return false
    }

    private companion object {
        const val WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"
    }
}

fun createAppDependencies(context: Context): AppDependencies {
    val app = context.applicationContext
    val db = Room.databaseBuilder<CardDatabase>(app, app.getDatabasePath("cardbalances.db").absolutePath).configure()
    val currency =
        runCatching { java.util.Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"
    return AppDependencies(CardRepository(db), GoogleWalletLauncher(app), currency)
}
