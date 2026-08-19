package net.tyt4.friendnet.backend

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import java.io.File

// Pre-seeds the backend's download directory settings into its SQLite database
// BEFORE the backend process starts.
//
// The backend's DownloadManager reads the incomplete/complete download dirs once
// at startup, and settings changes only take effect on the next backend start.
// Without this, the first run after a fresh install wrote downloads to the
// backend's default directory, and a stale/mixed settings state could leave the
// two dirs on different mounts, making the completion rename fail with
// "invalid cross-device link" (EXDEV). Seeding the dirs (both on the same
// external-storage mount) before the process starts avoids both problems.
object BackendSettingsSeeder {

    private const val TAG = "BackendSettingsSeeder"

    private const val KEY_INCOMPLETE = "dm_dir_incomplete"
    private const val KEY_COMPLETE = "dm_dir_complete"
    private const val MIGRATION_SETTING_KV = "20260225_add_setting_kv"

    fun seed(context: Context, dataDir: File) {
        try {
            val dbFile = File(dataDir, "client.db")
            dbFile.parentFile?.mkdirs()

            // Keep both dirs inside the public Downloads tree so the completion
            // rename is always on the same mount (never EXDEV), no matter how the
            // device maps storage.
            val completeDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val incompleteDir = File(completeDir, ".friendnet-incomplete")

            val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                val hasMigrationTable = tableExists(db, "migration")
                val hasSettingTable = tableExists(db, "setting")

                if (!hasSettingTable) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS setting (
                            key text not null primary key,
                            value text not null,
                            updated_ts integer default (strftime('%s', 'now')) not null
                        )
                        """.trimIndent()
                    )
                }
                if (!hasMigrationTable) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS migration (
                            name text not null primary key,
                            created_ts integer not null default (strftime('%s', 'now'))
                        )
                        """.trimIndent()
                    )
                }
                if (!hasSettingTable || !hasMigrationTable) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO migration (name) VALUES (?)",
                        arrayOf(MIGRATION_SETTING_KV)
                    )
                }

                db.execSQL(
                    "INSERT OR REPLACE INTO setting (key, value) VALUES (?, ?)",
                    arrayOf(KEY_INCOMPLETE, incompleteDir.absolutePath)
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO setting (key, value) VALUES (?, ?)",
                    arrayOf(KEY_COMPLETE, completeDir.absolutePath)
                )
            } finally {
                db.close()
            }

            val readBack = db.rawQuery(
                "SELECT key, value FROM setting WHERE key IN (?, ?)",
                arrayOf(KEY_INCOMPLETE, KEY_COMPLETE)
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(0), cursor.getString(1))
                    }
                }
            }

            Log.i(
                TAG,
                "Seeded backend settings: incomplete=${readBack[KEY_INCOMPLETE]} complete=${readBack[KEY_COMPLETE]}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed backend settings", e)
        }
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(name)
        ).use { cursor -> return cursor.count > 0 }
    }
}