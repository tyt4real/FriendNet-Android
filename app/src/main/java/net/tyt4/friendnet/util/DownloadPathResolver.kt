package net.tyt4.friendnet.util

import android.content.Context
import android.util.Log
import net.tyt4.friendnet.gen.DownloadManagerItem
import net.tyt4.friendnet.repository.SettingsRepository
import java.io.File

// Resolves the on-disk location of a completed download.
//
// The backend's download directories are captured when the DownloadManager
// starts, and settings changes only take effect on the next backend start.
// On the first run after a fresh install (or before settings are applied), the
// backend therefore writes files to its default directory
// (<filesDir>/Downloads/FriendNet Downloads/Complete) instead of the public
// Downloads directory that this app configures. Check every plausible location.
object DownloadPathResolver {

    private const val TAG = "DownloadPathResolver"

    fun resolve(context: Context, item: DownloadManagerItem): File? {
        val relative = FilenameReplacer.replacePath(
            "${item.peerUsername}-${item.serverUuid}/${item.filePath}"
        )

        val candidates = buildList {
            add(File(SettingsRepository(context).getCompleteDownloadDir(), relative))
            add(File(defaultBackendCompleteDir(context), relative))
        }.distinctBy { it.absolutePath }

        val existing = candidates.firstOrNull { it.exists() }
        if (existing != null) {
            Log.d(TAG, "Resolved ${item.filePath} to ${existing.absolutePath}")
        } else {
            Log.w(TAG, "No completed file found for ${item.filePath}; checked: " +
                candidates.joinToString { it.absolutePath })
        }
        return existing
    }

    private fun defaultBackendCompleteDir(context: Context): File =
        File(context.filesDir, "Downloads/FriendNet Downloads/Complete")
}