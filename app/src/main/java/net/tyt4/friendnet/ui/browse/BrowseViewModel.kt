package net.tyt4.friendnet.ui.browse

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import net.tyt4.friendnet.gen.Event
import net.tyt4.friendnet.gen.FileMeta
import net.tyt4.friendnet.gen.OnlineUserInfo
import net.tyt4.friendnet.gen.ServerInfo
import net.tyt4.friendnet.gen.StreamSearchResponse
import net.tyt4.friendnet.grpc.EventStreamManager
import net.tyt4.friendnet.repository.DownloadRepository
import net.tyt4.friendnet.repository.FileRepository
import net.tyt4.friendnet.repository.ServerRepository

class BrowseViewModel : ViewModel() {

    private data class DirKey(val serverUuid: String, val username: String, val path: String)

    val servers: LiveData<List<ServerInfo>> = ServerRepository.servers

    private val selectedServerUuid = MutableLiveData<String?>()
    private val selectedUsername = MutableLiveData<String?>()
    private val _currentPath = MutableLiveData("/")
    val currentPath: LiveData<String> = _currentPath
    private val pathStack = ArrayDeque<String>()

    private val dirKey = MutableLiveData<DirKey?>()

    val onlineUsers: LiveData<List<OnlineUserInfo>> = selectedServerUuid.switchMap { uuid ->
        if (uuid != null) FileRepository.getOnlineUsers(uuid) else MutableLiveData(emptyList())
    }

    val files: LiveData<List<FileMeta>> = dirKey.switchMap { key ->
        if (key != null) FileRepository.getDirFiles(key.serverUuid, key.username, key.path)
        else MutableLiveData(emptyList())
    }

    private val serverEventListener: (Event) -> Unit = { event ->
        if (event.type == Event.Type.TYPE_SERVER_CONN_STATE_CHANGE) {
            ServerRepository.refresh()
        }
    }

    private val userEventListener: (Event) -> Unit = { event ->
        when (event.type) {
            Event.Type.TYPE_CLIENT_ONLINE, Event.Type.TYPE_CLIENT_OFFLINE -> refreshOnlineUsers()
            else -> {}
        }
    }

    init {
        ServerRepository.refresh()
        EventStreamManager.getInstance().addListener(Event.Type.TYPE_SERVER_CONN_STATE_CHANGE, serverEventListener)
        EventStreamManager.getInstance().addListener(Event.Type.TYPE_CLIENT_ONLINE, userEventListener)
        EventStreamManager.getInstance().addListener(Event.Type.TYPE_CLIENT_OFFLINE, userEventListener)
    }

    override fun onCleared() {
        super.onCleared()
        EventStreamManager.getInstance().removeListener(Event.Type.TYPE_SERVER_CONN_STATE_CHANGE, serverEventListener)
        EventStreamManager.getInstance().removeListener(Event.Type.TYPE_CLIENT_ONLINE, userEventListener)
        EventStreamManager.getInstance().removeListener(Event.Type.TYPE_CLIENT_OFFLINE, userEventListener)
    }

    fun selectServer(uuid: String?) {
        selectedServerUuid.value = uuid
        selectedUsername.value = null
        _currentPath.value = "/"
        pathStack.clear()
        updateDirKey()
    }

    fun selectUser(username: String?) {
        selectedUsername.value = username
        _currentPath.value = "/"
        pathStack.clear()
        updateDirKey()
    }

    fun navigateInto(file: FileMeta) {
        if (!file.isDir) return
        pathStack.addLast(_currentPath.value ?: "/")
        _currentPath.value = joinPath(_currentPath.value ?: "/", file.name)
        updateDirKey()
    }

    fun navigateUp() {
        if (pathStack.isNotEmpty()) {
            _currentPath.value = pathStack.removeLast()
        } else {
            _currentPath.value = "/"
        }
        updateDirKey()
    }

    fun canNavigateUp(): Boolean = (_currentPath.value ?: "/") != "/"

    fun downloadFile(file: FileMeta, onResult: (Throwable?) -> Unit) {
        val uuid = selectedServerUuid.value
        val username = selectedUsername.value
        if (uuid == null || username == null) {
            onResult(IllegalStateException("No server or user selected"))
            return
        }
        DownloadRepository.queueFileDownload(
            uuid,
            username,
            joinPath(_currentPath.value ?: "/", file.name),
            onResult
        )
    }

    fun search(query: String): LiveData<List<StreamSearchResponse>> {
        val uuid = selectedServerUuid.value
        return if (uuid != null) FileRepository.streamSearch(uuid, query, null)
        else MutableLiveData(emptyList())
    }

    private fun refreshOnlineUsers() {
        selectedServerUuid.value = selectedServerUuid.value
    }

    private fun updateDirKey() {
        val uuid = selectedServerUuid.value
        val username = selectedUsername.value
        val path = _currentPath.value
        dirKey.value = if (uuid != null && username != null && path != null) {
            DirKey(uuid, username, path)
        } else {
            null
        }
    }

    private fun joinPath(base: String, name: String): String {
        val trimmed = base.trimEnd('/')
        return if (trimmed.isEmpty()) "/$name" else "$trimmed/$name"
    }
}