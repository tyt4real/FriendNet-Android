package net.tyt4.friendnet.util

import java.util.Locale

object Formats {
    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    fun bytes(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < UNITS.size - 1) {
            value /= 1024.0
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, UNITS[unit])
    }

    fun speed(bytesPerSec: Long): String = "${bytes(bytesPerSec)}/s"
}