package io.github.davidx3d.tempest.data.thermal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Minimal `su -c` runner. Works with KernelSU, Magisk and other su implementations. */
object RootShell {

    /** Returns stdout, or null when su is missing, denied, failed or timed out. */
    suspend fun run(command: String, timeoutSeconds: Long = 5): String? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command).start()
            // The output is a few KB at most, so it fits in the pipe buffer while we wait.
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy()
                return@withContext null
            }
            if (process.exitValue() != 0) return@withContext null
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /** The first call may wait for the user to answer the root manager's prompt. */
    suspend fun isGranted(): Boolean = run("id", timeoutSeconds = 20)?.contains("uid=0") == true
}
