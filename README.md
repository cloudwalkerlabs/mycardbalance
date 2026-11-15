# Card Balances

A small Kotlin Multiplatform app for tracking prepaid card balances by hand. Add a card with its
balance, then record what you spend so you know what's left without logging in to the issuer's
website.

- Only the **last 4 digits** of a card are stored. You can type the full number: it's checked
  with the Luhn algorithm and used to detect Visa/Mastercard/Amex, then discarded.
- Each card has its own currency. Amounts are stored as integer minor units (cents).
- Every change is a ledger entry: a balance update (including the card's starting balance) or a
  purchase ("Spend"). Balances are never stored: the `card_ledger` SQL view derives them as the
  latest update minus the purchases after it, so a balance can't disagree with its history.
- "Pay with Google Wallet" opens the Wallet app, or its Play Store page if it isn't installed.
  Wallet has no public API to preselect a card, so the app shows the last 4 digits to pick
  the right card.
- There's no login, because access is handled outside the app.

## Layout

| Module       | What's in it                                                                                    |
|--------------|-------------------------------------------------------------------------------------------------|
| `shared`     | KMP library: Room database on bundled SQLite (`data/`), repository, Compose Multiplatform UI. Android-specific code (database builder, Wallet launcher) is in `androidMain`. Room schemas are exported to `shared/schemas/`. |
| `androidApp` | The Android application shell (`MainActivity`, manifest, launcher icon).                        |
| `design/logos` | Logo concepts. Open `index.html` to compare them.                                            |

Everything except `androidMain` is common code, so adding iOS later means enabling the iOS targets
in `shared/build.gradle.kts` (plus `kspIos*` for Room), writing a `WalletLauncher` for Apple
Wallet, and adding an Xcode host app.

## Build and run

```sh
./gradlew :shared:jvmTest                  # unit + database tests (real bundled SQLite)
./gradlew :androidApp:installDebug         # install on a connected device/emulator
```
