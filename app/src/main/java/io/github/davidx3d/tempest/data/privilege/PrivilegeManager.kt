package io.github.davidx3d.tempest.data.privilege

import io.github.davidx3d.tempest.data.settings.Settings
import io.github.davidx3d.tempest.data.thermal.RootShell
import io.github.davidx3d.tempest.data.thermal.RootState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Which backend elevated reads go through. */
enum class PrivilegeMode { None, Root, Shizuku }

/** The chosen backend and whether it actually works right now. */
data class Access(val mode: PrivilegeMode = PrivilegeMode.None, val state: RootState = RootState.Off) {
    val granted: Boolean get() = state == RootState.Granted
}

/**
 * One place that decides between Shizuku and root and runs commands with whichever is active.
 * Shizuku wins when both are switched on and it is ready.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivilegeManager(settings: Flow<Settings>, scope: CoroutineScope) {

    private data class Want(val mode: PrivilegeMode, val shizukuReady: Boolean)

    val access: StateFlow<Access> = combine(settings, ShizukuShell.status) { prefs, shizuku ->
        val ready = shizuku == ShizukuStatus.Ready
        val mode = when {
            prefs.useShizuku && ready -> PrivilegeMode.Shizuku
            prefs.useRoot -> PrivilegeMode.Root
            prefs.useShizuku -> PrivilegeMode.Shizuku
            else -> PrivilegeMode.None
        }
        Want(mode, ready)
    }
        .distinctUntilChanged()
        .flatMapLatest { want ->
            flow {
                when (want.mode) {
                    PrivilegeMode.None -> emit(Access())
                    PrivilegeMode.Root -> {
                        emit(Access(want.mode, RootState.Checking))
                        emit(Access(want.mode, if (RootShell.isGranted()) RootState.Granted else RootState.Denied))
                    }
                    PrivilegeMode.Shizuku -> if (!want.shizukuReady) {
                        emit(Access(want.mode, RootState.Denied))
                    } else {
                        emit(Access(want.mode, RootState.Checking))
                        val ok = ShizukuShell.run("id", timeoutSeconds = 10)?.contains("uid=") == true
                        emit(Access(want.mode, if (ok) RootState.Granted else RootState.Denied))
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, Access())

    /** Stdout of [command] with elevated rights, or null when no backend is active or it failed. */
    suspend fun run(command: String, timeoutSeconds: Long = 5): String? {
        val current = access.value
        if (!current.granted) return null
        return when (current.mode) {
            PrivilegeMode.Root -> RootShell.run(command, timeoutSeconds)
            PrivilegeMode.Shizuku -> ShizukuShell.run(command, timeoutSeconds)
            PrivilegeMode.None -> null
        }
    }
}
