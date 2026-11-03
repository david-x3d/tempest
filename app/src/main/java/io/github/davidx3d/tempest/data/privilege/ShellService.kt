package io.github.davidx3d.tempest.data.privilege

import io.github.davidx3d.tempest.IShellService
import java.io.File
import kotlin.system.exitProcess

/**
 * Runs inside the process Shizuku starts with shell (adb) or root identity, so the commands it
 * executes can read what an ordinary app cannot. Instantiated by Shizuku, never by the app.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String, timeoutMs: Long): String? = try {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
            .start()
        // A hung command must not block the binder thread forever.
        val watchdog = Thread {
            try {
                Thread.sleep(timeoutMs)
                process.destroy()
            } catch (_: InterruptedException) {
            }
        }.apply {
            isDaemon = true
            start()
        }
        try {
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } finally {
            watchdog.interrupt()
        }
    } catch (_: Exception) {
        null
    }
}
