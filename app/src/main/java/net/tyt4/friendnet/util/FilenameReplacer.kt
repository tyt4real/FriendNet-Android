package net.tyt4.friendnet.util

// Port of the Go backend's StrictReplacer (client/fsys/replace.go).
// Keeps completed-download path computation in sync with how the backend
// writes files to disk.
object FilenameReplacer {

    fun replacePath(path: String): String {
        return path.split('/').joinToString("/") { part ->
            if (part.isEmpty()) part else replaceFilename(part)
        }
    }

    fun replaceFilename(str: String): String {
        val b = StringBuilder(str.length)
        for (r in str) {
            b.append(
                when (r) {
                    '\u0000' -> "\u2400" // SYMBOL FOR NULL
                    '/' -> "\uFF0F" // FULLWIDTH SOLIDUS
                    '\\' -> "\uFF3C" // FULLWIDTH REVERSE SOLIDUS
                    ':' -> "\uA789" // MODIFIER LETTER COLON
                    '*' -> "\u2217" // ASTERISK OPERATOR
                    '?' -> "\uFF1F" // FULLWIDTH QUESTION MARK
                    '"' -> "\uFF02" // FULLWIDTH QUOTATION MARK
                    '<' -> "\u2039" // SINGLE LEFT-POINTING ANGLE QUOTATION MARK
                    '>' -> "\u203A" // SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
                    '|' -> "\uFF5C" // FULLWIDTH VERTICAL LINE
                    in '\u0001'..'\u001F' -> (0x2400 + r.code).toChar().toString() // CONTROL PICTURES
                    '\u007F' -> "\u2421" // SYMBOL FOR DELETE
                    '\uFFFD' -> "\uFFFD" // REPLACEMENT CHARACTER
                    else -> r.toString()
                }
            )
        }

        var out = b.toString().trimEnd(' ', '.')
        if (out.isEmpty()) out = "_"
        if (isWindowsReservedDeviceName(out)) out = "_$out"
        return out
    }

    private fun isWindowsReservedDeviceName(name: String): Boolean {
        val base = name.trimEnd(' ', '.').substringBefore('.').uppercase()
        return when (base) {
            "CON", "PRN", "AUX", "NUL" -> true
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9" -> true
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" -> true
            else -> false
        }
    }
}