package app.cardbalances.android

import android.app.Application
import app.cardbalances.AppDependencies
import app.cardbalances.createAppDependencies

class CardBalancesApp : Application() {
    val deps: AppDependencies by lazy { createAppDependencies(this) }
}
