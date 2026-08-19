package net.tyt4.friendnet.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.grpc.stub.StreamObserver
import net.tyt4.friendnet.grpc.GrpcClient
import net.tyt4.friendnet.gen.*
import java.util.concurrent.Executors

object FileRepository {
    private val executor = Executors.newSingleThreadExecutor()

    fun getDirFiles(serverUuid: String, username: String, path: String): LiveData<List<FileMeta>> {
        val liveData = MutableLiveData<List<FileMeta>>(emptyList())
        val request = GetDirFilesRequest.newBuilder()
            .setServerUuid(serverUuid)
            .setUsername(username)
            .setPath(path)
            .build()

        val content = mutableListOf<FileMeta>()
        GrpcClient.getInstance().asyncStub.getDirFiles(request, object : StreamObserver<GetDirFilesResponse> {
            override fun onNext(value: GetDirFilesResponse) {
                content.addAll(value.contentList)
            }

            override fun onError(t: Throwable?) {
                t?.printStackTrace()
                liveData.postValue(emptyList())
            }

            override fun onCompleted() {
                content.sortWith(compareBy<FileMeta> { !it.isDir }.thenBy { it.name.lowercase() })
                liveData.postValue(content)
            }
        })
        return liveData
    }

    fun getOnlineUsers(serverUuid: String): LiveData<List<OnlineUserInfo>> {
        val liveData = MutableLiveData<List<OnlineUserInfo>>(emptyList())
        val request = GetOnlineUsersRequest.newBuilder()
            .setServerUuid(serverUuid)
            .build()

        val users = mutableSetOf<String>()
        val usersInfo = mutableListOf<OnlineUserInfo>()

        GrpcClient.getInstance().asyncStub.getOnlineUsers(request, object : StreamObserver<GetOnlineUsersResponse> {
            override fun onNext(value: GetOnlineUsersResponse) {
                value.usersList.forEach {
                    if (users.add(it.username)) {
                        usersInfo.add(it)
                    }
                }
                liveData.postValue(ArrayList(usersInfo))
            }

            override fun onError(t: Throwable?) {
                t?.printStackTrace()
                liveData.postValue(emptyList())
            }

            override fun onCompleted() {}
        })
        return liveData
    }

    fun streamSearch(serverUuid: String, query: String, username: String?): LiveData<List<StreamSearchResponse>> {
        val liveData = MutableLiveData<List<StreamSearchResponse>>(emptyList())
        val builder = StreamSearchRequest.newBuilder()
            .setServerUuid(serverUuid)
            .setQuery(query)
        if (username != null) builder.username = username
        val request = builder.build()

        val results = mutableListOf<StreamSearchResponse>()
        GrpcClient.getInstance().asyncStub.streamSearch(request, object : StreamObserver<StreamSearchResponse> {
            override fun onNext(value: StreamSearchResponse) {
                results.add(value)
                liveData.postValue(ArrayList(results))
            }

            override fun onError(t: Throwable?) {
                t?.printStackTrace()
            }

            override fun onCompleted() {}
        })
        return liveData
    }
}