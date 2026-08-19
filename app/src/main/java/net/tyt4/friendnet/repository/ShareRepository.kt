package net.tyt4.friendnet.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.tyt4.friendnet.grpc.GrpcClient
import net.tyt4.friendnet.gen.*
import java.util.concurrent.Executors

object ShareRepository {
    private val executor = Executors.newSingleThreadExecutor()

    fun getShares(serverUuid: String): LiveData<List<ShareInfo>> {
        val liveData = MutableLiveData<List<ShareInfo>>()
        executor.execute {
            try {
                val request = GetSharesRequest.newBuilder().setServerUuid(serverUuid).build()
                val response = GrpcClient.getInstance().blockingStub.getShares(request)
                liveData.postValue(response.sharesList)
            } catch (e: Exception) {
                e.printStackTrace()
                liveData.postValue(emptyList())
            }
        }
        return liveData
    }

    fun createShare(serverUuid: String, name: String, path: String, followLinks: Boolean) {
        executor.execute {
            try {
                val request = CreateShareRequest.newBuilder()
                    .setServerUuid(serverUuid)
                    .setName(name)
                    .setPath(path)
                    .setFollowLinks(followLinks)
                    .build()
                GrpcClient.getInstance().blockingStub.createShare(request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteShare(serverUuid: String, shareName: String) {
        executor.execute {
            try {
                val request = DeleteShareRequest.newBuilder()
                    .setServerUuid(serverUuid)
                    .setName(shareName)
                    .build()
                GrpcClient.getInstance().blockingStub.deleteShare(request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun indexShare(serverUuid: String, shareName: String) {
        executor.execute {
            try {
                val request = IndexShareRequest.newBuilder()
                    .setServerUuid(serverUuid)
                    .setName(shareName)
                    .build()
                GrpcClient.getInstance().blockingStub.indexShare(request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}