package net.tyt4.friendnet.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.tyt4.friendnet.grpc.GrpcClient
import net.tyt4.friendnet.gen.*
import java.util.concurrent.Executors

object ServerRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val _servers = MutableLiveData<List<ServerInfo>>(emptyList())
    val servers: LiveData<List<ServerInfo>> = _servers

    fun refresh() {
        executor.execute {
            try {
                val response = GrpcClient.getInstance().blockingStub.getServers(GetServersRequest.getDefaultInstance())
                _servers.postValue(response.serversList)
            } catch (e: Exception) {
                _servers.postValue(emptyList())
            }
        }
    }

    fun createServer(request: CreateServerRequest, onResult: (Result<ServerInfo>) -> Unit) {
        executor.execute {
            try {
                val response = GrpcClient.getInstance().blockingStub.createServer(request)
                onResult(Result.success(response.server))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
            refresh()
        }
    }

    fun updateServer(request: UpdateServerRequest, onResult: (Result<ServerInfo>) -> Unit) {
        executor.execute {
            try {
                val response = GrpcClient.getInstance().blockingStub.updateServer(request)
                onResult(Result.success(response.server))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
            refresh()
        }
    }

    fun deleteServer(uuid: String) {
        executor.execute {
            try {
                val request = DeleteServerRequest.newBuilder().setUuid(uuid).build()
                GrpcClient.getInstance().blockingStub.deleteServer(request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            refresh()
        }
    }

    fun connectServer(uuid: String) {
        executor.execute {
            try {
                val request = ConnectServerRequest.newBuilder().setUuid(uuid).build()
                GrpcClient.getInstance().blockingStub.connectServer(request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            refresh()
        }
    }

    fun disconnectServer(uuid: String) {
        executor.execute {
            try {
                val request = DisconnectServerRequest.newBuilder().setUuid(uuid).build()
                GrpcClient.getInstance().blockingStub.disconnectServer(request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            refresh()
        }
    }
}