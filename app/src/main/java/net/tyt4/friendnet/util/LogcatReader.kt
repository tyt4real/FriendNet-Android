package net.tyt4.friendnet.util

import android.content.Context
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogcatReader {

    private const val MAX_LINES = 5000

    fun dump(context: Context): String {
        val pid = Process.myPid()
        val header = buildString {
            appendLine("=== FriendNet log dump ===")
            appendLine("Package: ${context.packageName} | PID: $pid")
            appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
        }

        val logs = try {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "threadtime", "-t", MAX_LINES.toString(), "--pid=$pid"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Failed to read logcat: ${e.message}"
        }

        return buildString {
            append(header)
            appendLine()
            appendLine("--- Logcat ---")
            appendLine(logs)
        }.trimEnd()
    }
}