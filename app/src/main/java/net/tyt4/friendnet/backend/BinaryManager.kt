package net.tyt4.friendnet.backend

import android.content.Context
import android.util.Log
import java.io.File

class BinaryManager(private val context: Context) {
    private val binaryName = "libfriendnet.so"

    fun getBinaryPath(): String {
        return File(context.applicationInfo.nativeLibraryDir, binaryName).absolutePath
    }

    fun isReady(): Boolean {
        return File(getBinaryPath()).exists()
    }

    fun ensureBinary(): Boolean {
        val binaryFile = File(getBinaryPath())
        if (!binaryFile.exists()) {
            Log.e("BinaryManager", "Binary not found at ${binaryFile.absolutePath}")
            return false
        }
        
        if (!binaryFile.canExecute()) {
            Log.w("BinaryManager", "Binary not executable, attempting to set permission")
            try {
                binaryFile.setExecutable(true)
            } catch (e: Exception) {
                Log.e("BinaryManager", "Failed to set executable permission", e)
            }
        }
        
        return binaryFile.canExecute()
    }
}
