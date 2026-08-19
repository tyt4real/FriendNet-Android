package net.tyt4.friendnet.ui.servers

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import net.tyt4.friendnet.gen.CreateServerRequest
import net.tyt4.friendnet.gen.Event
import net.tyt4.friendnet.gen.ServerInfo
import net.tyt4.friendnet.gen.UpdateServerRequest
import net.tyt4.friendnet.repository.ServerRepository
import net.tyt4.friendnet.grpc.EventStreamManager

class ServersViewModel : ViewModel() {
    val servers: LiveData<List<ServerInfo>> = ServerRepository.servers

    private val eventListener: (Event) -> Unit = { event ->
        if (event.type == Event.Type.TYPE_SERVER_CONN_STATE_CHANGE) {
            ServerRepository.refresh()
        }
    }

    init {
        ServerRepository.refresh()
        EventStreamManager.getInstance().addListener(Event.Type.TYPE_SERVER_CONN_STATE_CHANGE, eventListener)
    }

    override fun onCleared() {
        super.onCleared()
        EventStreamManager.getInstance().removeListener(Event.Type.TYPE_SERVER_CONN_STATE_CHANGE, eventListener)
    }

    fun refresh() = ServerRepository.refresh()

    fun connectServer(uuid: String) = ServerRepository.connectServer(uuid)

    fun disconnectServer(uuid: String) = ServerRepository.disconnectServer(uuid)

    fun deleteServer(uuid: String) = ServerRepository.deleteServer(uuid)

    fun createServer(request: CreateServerRequest, onResult: (Result<ServerInfo>) -> Unit) =
        ServerRepository.createServer(request, onResult)

    fun updateServer(request: UpdateServerRequest, onResult: (Result<ServerInfo>) -> Unit) =
        ServerRepository.updateServer(request, onResult)
}