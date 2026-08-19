package net.tyt4.friendnet.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.ItemDownloadBinding
import net.tyt4.friendnet.gen.DownloadManagerItem
import net.tyt4.friendnet.gen.DownloadStatus
import net.tyt4.friendnet.util.Formats

class DownloadAdapter(
    private val onCancel: (DownloadManagerItem) -> Unit,
    private val onResume: (DownloadManagerItem) -> Unit,
    private val onRemove: (DownloadManagerItem) -> Unit,
    private val onOpen: (DownloadManagerItem) -> Unit
) : ListAdapter<DownloadManagerItem, DownloadAdapter.ViewHolder>(DiffCallback) {

    private val speeds = mutableMapOf<String, Long>()
    private val serverNames = mutableMapOf<String, String>()

    fun setSpeeds(newSpeeds: Map<String, Long>) {
        speeds.clear()
        speeds.putAll(newSpeeds)
        notifyDataSetChanged()
    }

    fun setServerNames(names: Map<String, String>) {
        serverNames.clear()
        serverNames.putAll(names)
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.binding.root.context
        val download = item.download
        val status = download?.status ?: DownloadStatus.DOWNLOAD_STATUS_UNSPECIFIED

        holder.binding.textFilename.text = item.filePath.substringAfterLast('/')
        holder.binding.textPeer.text = context.getString(
            R.string.file_from_user,
            item.peerUsername,
            serverNames[item.serverUuid] ?: item.serverUuid
        )

        holder.binding.textStatus.text = statusText(context, status)
        holder.binding.textStatus.setTextColor(statusColor(context, status))

        if (download != null) {
            val progress = progressOf(download)
            val fileSize = download.fileSize
            if (progress != null) {
                holder.binding.progressBar.isIndeterminate = false
                holder.binding.progressBar.progress = progress
            } else {
                holder.binding.progressBar.isIndeterminate = true
            }

            val speed = speeds[item.uuid]
            if (status == DownloadStatus.DOWNLOAD_STATUS_PENDING && speed != null && speed > 0) {
                holder.binding.textSpeed.text = Formats.speed(speed)
                holder.binding.textSpeed.visibility = View.VISIBLE
            } else {
                holder.binding.textSpeed.visibility = View.INVISIBLE
            }

            if (status == DownloadStatus.DOWNLOAD_STATUS_ERROR && !download.errorMessage.isNullOrEmpty()) {
                holder.binding.textError.text = download.errorMessage
                holder.binding.textError.visibility = View.VISIBLE
            } else {
                holder.binding.textError.visibility = View.GONE
            }

            holder.binding.buttonResume.visibility =
                if (status == DownloadStatus.DOWNLOAD_STATUS_CANCELED || status == DownloadStatus.DOWNLOAD_STATUS_ERROR)
                    View.VISIBLE else View.GONE
            holder.binding.buttonCancel.visibility =
                if (status == DownloadStatus.DOWNLOAD_STATUS_QUEUED || status == DownloadStatus.DOWNLOAD_STATUS_PENDING)
                    View.VISIBLE else View.GONE
            holder.binding.buttonOpen.visibility =
                if (status == DownloadStatus.DOWNLOAD_STATUS_DONE) View.VISIBLE else View.GONE
        } else {
            holder.binding.progressBar.isIndeterminate = false
            holder.binding.progressBar.progress = 0
            holder.binding.textSpeed.visibility = View.INVISIBLE
            holder.binding.textError.visibility = View.GONE
            holder.binding.buttonResume.visibility = View.GONE
            holder.binding.buttonCancel.visibility = View.GONE
            holder.binding.buttonOpen.visibility = View.GONE
        }

        holder.binding.buttonResume.setOnClickListener { onResume(item) }
        holder.binding.buttonCancel.setOnClickListener { onCancel(item) }
        holder.binding.buttonRemove.setOnClickListener { onRemove(item) }
        holder.binding.buttonOpen.setOnClickListener { onOpen(item) }
    }

    private fun progressOf(download: DownloadManagerItem.Download): Int? {
        return when (download.status) {
            DownloadStatus.DOWNLOAD_STATUS_DONE -> 100
            DownloadStatus.DOWNLOAD_STATUS_PENDING,
            DownloadStatus.DOWNLOAD_STATUS_QUEUED -> {
                if (download.fileSize > 0) {
                    ((download.downloaded.toDouble() / download.fileSize) * 100).toInt().coerceIn(0, 100)
                } else {
                    null
                }
            }
            else -> 0
        }
    }

    private fun statusText(context: android.content.Context, status: DownloadStatus): String = when (status) {
        DownloadStatus.DOWNLOAD_STATUS_QUEUED -> context.getString(R.string.download_status_queued)
        DownloadStatus.DOWNLOAD_STATUS_PENDING -> context.getString(R.string.download_status_pending)
        DownloadStatus.DOWNLOAD_STATUS_CANCELED -> context.getString(R.string.download_status_canceled)
        DownloadStatus.DOWNLOAD_STATUS_DONE -> context.getString(R.string.download_status_done)
        DownloadStatus.DOWNLOAD_STATUS_ERROR -> context.getString(R.string.download_status_error)
        else -> "-"
    }

    private fun statusColor(context: android.content.Context, status: DownloadStatus): Int = when (status) {
        DownloadStatus.DOWNLOAD_STATUS_DONE -> ContextCompat.getColor(context, R.color.status_open)
        DownloadStatus.DOWNLOAD_STATUS_ERROR -> ContextCompat.getColor(context, R.color.status_error)
        DownloadStatus.DOWNLOAD_STATUS_CANCELED -> ContextCompat.getColor(context, R.color.status_closed)
        else -> ContextCompat.getColor(context, R.color.primary)
    }

    object DiffCallback : DiffUtil.ItemCallback<DownloadManagerItem>() {
        override fun areItemsTheSame(oldItem: DownloadManagerItem, newItem: DownloadManagerItem): Boolean {
            return oldItem.uuid == newItem.uuid
        }

        override fun areContentsTheSame(oldItem: DownloadManagerItem, newItem: DownloadManagerItem): Boolean {
            return oldItem == newItem
        }
    }
}