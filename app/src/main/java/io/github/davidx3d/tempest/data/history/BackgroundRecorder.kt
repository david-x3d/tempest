package io.github.davidx3d.tempest.data.history

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import io.github.davidx3d.tempest.IRecorderService
import io.github.davidx3d.tempest.data.privilege.ShizukuShell
import io.github.davidx3d.tempest.data.privilege.ShizukuStatus
import io.github.davidx3d.tempest.data.settings.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

sealed interface RecorderState {
    data object Off : RecorderState
    data object NeedsShizuku : RecorderState
    data object Starting : RecorderState
    data class Running(val samples: Int) : RecorderState
    data object Failed : RecorderState
}

/**
 * The app's side of the background recorder: starts or stops the [RecorderService] daemon in
 * Shizuku to match the setting and pulls what it recorded into the [HistoryRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundRecorder(
    private val context: Context,
    settings: StateFlow<Settings>,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Off)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    @Volatile private var service: IRecorderService? = null
    @Volatile private var waiting: CompletableDeferred<IRecorderService?>? = null
    private val lock = Mutex()
    private val reconnect = MutableStateFlow(0)
    private var lastImported = 0L

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            service = binder?.takeIf { it.pingBinder() }?.let { IRecorderService.Stub.asInterface(it) }
            waiting?.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            // The daemon died while Shizuku kept running: start it again.
            reconnect.value++
        }
    }

    private val args by lazy {
        // Every install brings new code (and deletes the old APK the daemon runs from), so the
        // install time is the version: Shizuku replaces a running daemon whose version differs.
        val version = runCatching {
            (context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime / 1000 % Int.MAX_VALUE).toInt()
        }.getOrDefault(1)
        Shizuku.UserServiceArgs(ComponentName(context.packageName, RecorderService::class.java.name))
            .tag("tempest-recorder")
            .processNameSuffix("recorder")
            .daemon(true)
            .version(version)
    }

    init {
        scope.launch {
            var wasOn = false
            combine(settings, ShizukuShell.status, reconnect) { prefs, status, attempt ->
                Want(prefs.backgroundRecording, prefs.recordIntervalSeconds, status == ShizukuStatus.Ready, attempt)
            }
                .distinctUntilChanged()
                .collectLatest { (on, interval, ready) ->
                    when {
                        !on -> {
                            if (wasOn && ready) stopDaemon()
                            wasOn = false
                            _state.value = RecorderState.Off
                        }
                        !ready -> {
                            wasOn = true
                            _state.value = RecorderState.NeedsShizuku
                        }
                        else -> {
                            wasOn = true
                            if (_state.value !is RecorderState.Running) _state.value = RecorderState.Starting
                            val recorder = bind()
                            if (recorder == null) {
                                _state.value = RecorderState.Failed
                            } else {
                                runCatching { recorder.setInterval(interval) }
                                sync()
                            }
                        }
                    }
                }
        }
    }

    /** Pulls new samples from the daemon. Called on start-up and whenever the app comes back. */
    suspend fun sync() = lock.withLock {
        val recorder = alive() ?: return@withLock
        val text = withContext(Dispatchers.IO) { runCatching { recorder.samplesSince(lastImported) }.getOrNull() }
            ?: return@withLock
        val battery = ArrayList<Sample>()
        val soc = ArrayList<Sample>()
        text.lineSequence().forEach { line ->
            val parts = line.split(',')
            val time = parts[0].toLongOrNull() ?: return@forEach
            parts.getOrNull(1)?.toFloatOrNull()?.let { battery += Sample(time, it) }
            parts.getOrNull(2)?.toFloatOrNull()?.let { soc += Sample(time, it) }
            if (time > lastImported) lastImported = time
        }
        history.import(battery, soc)
        _state.value = RecorderState.Running(runCatching { recorder.count() }.getOrDefault(0))
    }

    fun syncLater() {
        scope.launch { sync() }
    }

    /** Deletes the recorded and the in-app history. */
    fun clear() {
        scope.launch {
            lock.withLock {
                alive()?.let { recorder -> withContext(Dispatchers.IO) { runCatching { recorder.clear() } } }
                history.clear()
                if (_state.value is RecorderState.Running) _state.value = RecorderState.Running(0)
            }
        }
    }

    private suspend fun bind(): IRecorderService? {
        alive()?.let { return it }
        val result = CompletableDeferred<IRecorderService?>()
        waiting = result
        runCatching { Shizuku.bindUserService(args, connection) }.onFailure { return null }
        return withTimeoutOrNull(BIND_TIMEOUT_MS) { result.await() }
    }

    private fun stopDaemon() {
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
        lastImported = 0L
    }

    private fun alive(): IRecorderService? = service?.takeIf { it.asBinder().pingBinder() }

    private data class Want(val on: Boolean, val interval: Int, val ready: Boolean, val attempt: Int)

    private companion object {
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
