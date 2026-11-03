package io.github.davidx3d.tempest.data.privilege

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import io.github.davidx3d.tempest.IShellService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Where Shizuku stands, from "not on this phone" to "ready to run commands". */
enum class ShizukuStatus { NotInstalled, NotRunning, NeedsPermission, Denied, Ready }

/**
 * Talks to Shizuku (or Sui): tracks its state and runs shell commands through a [ShellService]
 * that Shizuku starts with shell privileges.
 */
object ShizukuShell {

    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4107
    private const val BIND_TIMEOUT_MS = 10_000L

    private val _status = MutableStateFlow(ShizukuStatus.NotInstalled)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private lateinit var appContext: Context
    private val bindLock = Mutex()

    @Volatile private var service: IShellService? = null
    @Volatile private var waiting: CompletableDeferred<IShellService?>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            service = binder?.takeIf { it.pingBinder() }?.let { IShellService.Stub.asInterface(it) }
            waiting?.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { code, _ -> if (code == REQUEST_CODE) refresh() }
        refresh()
    }

    fun refresh() {
        _status.value = compute()
    }

    /** Shows Shizuku's own permission dialog. The result arrives through [status]. */
    fun requestPermission() {
        if (_status.value == ShizukuStatus.NeedsPermission) runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** Opens the Shizuku app, or its page when it is not installed. */
    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Stdout of [command], or null when Shizuku is not ready or the command failed. */
    suspend fun run(command: String, timeoutSeconds: Long = 5): String? {
        val shell = bind() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { shell.exec(command, timeoutSeconds * 1000) }.getOrNull()
        }
    }

    private fun compute(): ShizukuStatus = runCatching {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> if (isInstalled()) ShizukuStatus.NotRunning else ShizukuStatus.NotInstalled
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.Ready
            Shizuku.shouldShowRequestPermissionRationale() -> ShizukuStatus.Denied
            else -> ShizukuStatus.NeedsPermission
        }
    }.getOrDefault(ShizukuStatus.NotRunning)

    private fun isInstalled(): Boolean =
        runCatching { appContext.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    private suspend fun bind(): IShellService? {
        alive()?.let { return it }
        if (_status.value != ShizukuStatus.Ready) return null
        return bindLock.withLock {
            alive()?.let { return@withLock it }
            val result = CompletableDeferred<IShellService?>()
            waiting = result
            val args = Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellService::class.java.name))
                .tag("tempest-shell")
                .processNameSuffix("shell")
                .daemon(false)
                .version(1)
            runCatching { Shizuku.bindUserService(args, connection) }.onFailure { return@withLock null }
            withTimeoutOrNull(BIND_TIMEOUT_MS) { result.await() }
        }
    }

    private fun alive(): IShellService? = service?.takeIf { it.asBinder().pingBinder() }
}
