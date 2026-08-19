package net.tyt4.friendnet.ui.downloads

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.tyt4.friendnet.grpc.EventStreamManager
import net.tyt4.friendnet.gen.DownloadManagerItem
import net.tyt4.friendnet.gen.Event
import net.tyt4.friendnet.repository.DownloadRepository

class DownloadsViewModel : ViewModel() {
    val items: LiveData<List<DownloadManagerItem>> = DownloadRepository.items

    private val _speeds = MutableLiveData<Map<String, Long>>(emptyMap())
    val speeds: LiveData<Map<String, Long>> = _speeds

    private val eventListener: (Event) -> Unit = { event ->
        when (event.type) {
            Event.Type.TYPE_DOWNLOAD_STATUS_UPDATES -> {
                val speedMap = mutableMapOf<String, Long>()
                event.downloadStatusUpdates.filesList.forEach { speedMap[it.uuid] = it.speed }
                _speeds.postValue(speedMap)
                DownloadRepository.refresh()
            }
            Event.Type.TYPE_NEW_DM_ITEM,
            Event.Type.TYPE_DM_ITEM_REMOVED -> DownloadRepository.refresh()
            else -> {}
        }
    }

    init {
        DownloadRepository.refresh()
        EventStreamManager.getInstance().addListener(Event.Type.TYPE_DOWNLOAD_STATUS_UPDATES, eventListener)
        EventStreamManager.getInstance().addListener(Event.Type.TYPE_NEW_DM_ITEM, eventListener)
        EventStreamManager.getInstance().addListener(Event.Type.TYPE_DM_ITEM_REMOVED, eventListener)
    }

    override fun onCleared() {
        super.onCleared()
        EventStreamManager.getInstance().removeListener(Event.Type.TYPE_DOWNLOAD_STATUS_UPDATES, eventListener)
        EventStreamManager.getInstance().removeListener(Event.Type.TYPE_NEW_DM_ITEM, eventListener)
        EventStreamManager.getInstance().removeListener(Event.Type.TYPE_DM_ITEM_REMOVED, eventListener)
    }

    fun refresh() = DownloadRepository.refresh()

    fun cancelDownload(uuid: String, onResult: (Throwable?) -> Unit) =
        DownloadRepository.cancelFileDownload(uuid, onResult)

    fun resumeDownload(uuid: String, onResult: (Throwable?) -> Unit) =
        DownloadRepository.resumeFileDownload(uuid, onResult)

    fun removeDownload(uuid: String, onResult: (Throwable?) -> Unit) =
        DownloadRepository.removeDownloadManagerItem(uuid, onResult)
}