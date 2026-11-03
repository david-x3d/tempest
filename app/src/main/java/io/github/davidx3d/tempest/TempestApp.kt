package io.github.davidx3d.tempest

import android.app.Application
import io.github.davidx3d.tempest.data.privilege.ShizukuShell

class TempestApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ShizukuShell.init(this)
        container = AppContainer(this)
    }
}
