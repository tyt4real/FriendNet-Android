package net.tyt4.friendnet.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.tyt4.friendnet.grpc.GrpcClient
import net.tyt4.friendnet.gen.*
import java.util.concurrent.Executors

object DownloadRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val _items = MutableLiveData<List<DownloadManagerItem>>(emptyList())
    val items: LiveData<List<DownloadManagerItem>> = _items

    fun refresh() {
        executor.execute {
            try {
                val response = GrpcClient.getInstance().blockingStub.getDownloadManagerItems(GetDownloadManagerItemsRequest.getDefaultInstance())
                _items.postValue(response.itemsList)
            } catch (e: Exception) {
                _items.postValue(emptyList())
            }
        }
    }

    fun queueFileDownload(
        serverUuid: String,
        peerUsername: String,
        filePath: String,
        onResult: (Throwable?) -> Unit
    ) {
        executor.execute {
            try {
                val request = QueueFileDownloadRequest.newBuilder()
                    .setServerUuid(serverUuid)
                    .setPeerUsername(peerUsername)
                    .setFilePath(filePath)
                    .build()
                GrpcClient.getInstance().blockingStub.queueFileDownload(request)
                onResult(null)
            } catch (e: Exception) {
                onResult(e)
            }
            refresh()
        }
    }

    fun cancelFileDownload(uuid: String, onResult: (Throwable?) -> Unit) {
        executor.execute {
            try {
                val request = CancelFileDownloadRequest.newBuilder().setUuid(uuid).build()
                GrpcClient.getInstance().blockingStub.cancelFileDownload(request)
                onResult(null)
            } catch (e: Exception) {
                onResult(e)
            }
            refresh()
        }
    }

    fun resumeFileDownload(uuid: String, onResult: (Throwable?) -> Unit) {
        executor.execute {
            try {
                val request = ResumeFileDownloadRequest.newBuilder().setUuid(uuid).build()
                GrpcClient.getInstance().blockingStub.resumeFileDownload(request)
                onResult(null)
            } catch (e: Exception) {
                onResult(e)
            }
            refresh()
        }
    }

    fun removeDownloadManagerItem(uuid: String, onResult: (Throwable?) -> Unit) {
        executor.execute {
            try {
                val request = RemoveDownloadManagerItemRequest.newBuilder().setUuid(uuid).build()
                GrpcClient.getInstance().blockingStub.removeDownloadManagerItem(request)
                onResult(null)
            } catch (e: Exception) {
                onResult(e)
            }
            refresh()
        }
    }
}