package app.cardbalances

import app.cardbalances.data.CardRepository

/** Opens the phone's payment wallet. Implemented per platform. */
interface WalletLauncher {
    /** Short name for the button, e.g. "Google Wallet". */
    val walletName: String

    /** Opens the wallet app, or its store page when not installed. Returns false if neither works. */
    fun open(): Boolean
}

/** Everything the shared UI needs from the host platform. */
class AppDependencies(
    val repository: CardRepository,
    val wallet: WalletLauncher,
    /** ISO 4217 code for the device's locale, used until the user has added a card. */
    val deviceCurrencyCode: String,
)
