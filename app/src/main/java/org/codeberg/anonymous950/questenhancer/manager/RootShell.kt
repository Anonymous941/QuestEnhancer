package org.codeberg.anonymous950.questenhancer.manager

import java.io.BufferedReader
import java.io.BufferedWriter

/**
 * One `su` shell for the whole app, so Magisk asks for root once.
 * Commands run one at a time on it; each ends with a marker line carrying its exit code.
 * If root is refused, nothing asks again until [retry] (the Refresh button).
 */
object RootShell {
    data class Result(val code: Int, val output: String)

    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null
    private var refused = false

    @Synchronized
    fun retry() {
        refused = false
    }

    /** Seconds one command may take before the shell's `timeout` stops it (exit code 124). */
    const val TIMEOUT_SECONDS = 20

    @Synchronized
    fun run(command: String): Result {
        if (refused) return Result(-1, "root was refused (tap Refresh to ask again)")
        if (process == null && !start()) return Result(-1, "could not start su")
        val marker = "__QE_DONE_${System.nanoTime()}__"
        return try {
            input!!.apply {
                // Run it in a child sh under toybox `timeout`, so a hung command ends
                // and the shared shell (and its one root grant) carries on.
                val quoted = "'" + command.replace("'", "'\\''") + "'"
                // (Plain sh if this build's toybox has no timeout.)
                write(
                    "if command -v timeout >/dev/null; then timeout $TIMEOUT_SECONDS sh -c $quoted; " +
                        "else sh -c $quoted; fi </dev/null 2>&1\necho \"$marker \$?\"\n"
                )
                flush()
            }
            readUntil(marker)
        } catch (e: Exception) {
            closed()
        }
    }

    /** Collects output up to the marker line; the marker carries the exit code. */
    private fun readUntil(marker: String): Result {
        val text = StringBuilder()
        while (true) {
            val line = output!!.readLine() ?: return closed()
            if (line.startsWith(marker)) {
                val code = line.substring(marker.length).trim().toIntOrNull() ?: -1
                if (code == 124) return Result(code, "timed out after ${TIMEOUT_SECONDS}s")
                return Result(code, text.toString().trim())
            }
            text.append(line).append('\n')
        }
    }

    private fun start(): Boolean = try {
        val p = ProcessBuilder("su").redirectErrorStream(true).start()
        process = p
        input = p.outputStream.bufferedWriter()
        output = p.inputStream.bufferedReader()
        true
    } catch (e: Exception) {
        false
    }

    /** su exited: root refused, or the shell died. Don't start another on our own. */
    private fun closed(): Result {
        process?.destroy()
        process = null
        input = null
        output = null
        refused = true
        return Result(-1, "root was refused (tap Refresh to ask again)")
    }
}
