package org.codeberg.anonymous950.questenhancer.manager

import java.util.concurrent.TimeUnit

/** Runs one command through Magisk's su. Read only use for now. */
object RootShell {
    data class Result(val code: Int, val output: String)

    fun run(command: String, timeoutSeconds: Long = 15): Result {
        return try {
            val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = Thread { out.append(p.inputStream.bufferedReader().readText()) }
            reader.start()
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroy()
                return Result(-1, "timed out after ${timeoutSeconds}s")
            }
            reader.join(1000)
            Result(p.exitValue(), out.toString().trim())
        } catch (e: Exception) {
            Result(-1, "could not run su: ${e.message}")
        }
    }
}
