package dev.fanchao.mycardbalance.android

import android.app.Application
import dev.fanchao.mycardbalance.AppDependencies
import dev.fanchao.mycardbalance.createAppDependencies

class CardBalancesApp : Application() {
    val deps: AppDependencies by lazy { createAppDependencies(this) }
}
