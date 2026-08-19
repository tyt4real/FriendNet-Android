package net.tyt4.friendnet.repository

import android.content.Context
import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.tyt4.friendnet.grpc.GrpcClient
import net.tyt4.friendnet.gen.*
import java.io.File
import java.util.concurrent.Executors

class SettingsRepository(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences("friendnet_settings", Context.MODE_PRIVATE)

    fun getTransferSettings(): LiveData<TransferSettings?> {
        val liveData = MutableLiveData<TransferSettings?>()
        executor.execute {
            try {
                val response = GrpcClient.getInstance().blockingStub.getTransferSettings(GetTransferSettingsRequest.getDefaultInstance())
                liveData.postValue(response.settings)
            } catch (e: Exception) {
                e.printStackTrace()
                liveData.postValue(null)
            }
        }
        return liveData
    }

    fun updateTransferSettings(settings: TransferSettings) {
        executor.execute {
            try {
                val request = UpdateTransferSettingsRequest.newBuilder()
                    .setSettings(settings)
                    .build()
                GrpcClient.getInstance().blockingStub.updateTransferSettings(request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun checkForNewUpdate(onResult: (UpdateInfo?) -> Unit) {
        executor.execute {
            try {
                val response = GrpcClient.getInstance().blockingStub.checkForNewUpdate(CheckForNewUpdateRequest.getDefaultInstance())
                onResult(if (response.hasNewInfo()) response.newInfo else null)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun getCompleteDownloadDir(): File {
        val storedPath = prefs.getString("complete_download_dir", null)
        if (storedPath != null) {
            val dir = File(storedPath)
            if (dir.exists()) return dir
        }
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun initFirstLaunchIfNeeded() {
        executor.execute {
            try {
                val incompleteDir = File(context.getExternalFilesDir(null), "FriendNet/.incomplete")
                val completeDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                incompleteDir.mkdirs()
                completeDir.mkdirs()

                val alreadyApplied = prefs.getString("complete_download_dir", null) == completeDir.absolutePath
                if (alreadyApplied) return@execute

                val settings = TransferSettings.newBuilder()
                    .setIncompleteDownloadDir(incompleteDir.absolutePath)
                    .setCompleteDownloadDir(completeDir.absolutePath)
                    .setDownloadConcurrency(3)
                    .build()

                val request = UpdateTransferSettingsRequest.newBuilder()
                    .setSettings(settings)
                    .build()
                GrpcClient.getInstance().blockingStub.updateTransferSettings(request)
                prefs.edit()
                    .putBoolean("first_launch_done", true)
                    .putString("complete_download_dir", completeDir.absolutePath)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}