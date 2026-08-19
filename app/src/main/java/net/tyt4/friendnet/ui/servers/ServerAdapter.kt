package net.tyt4.friendnet.ui.servers

import android.view.LayoutInflater
import android.view.Menu
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.ItemServerBinding
import net.tyt4.friendnet.gen.ServerConnState
import net.tyt4.friendnet.gen.ServerInfo

class ServerAdapter(
    private val onConnect: (ServerInfo) -> Unit,
    private val onDisconnect: (ServerInfo) -> Unit,
    private val onEdit: (ServerInfo) -> Unit,
    private val onDelete: (ServerInfo) -> Unit
) : ListAdapter<ServerInfo, ServerAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemServerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.binding.root.context
        val state = item.state.connState

        holder.binding.textName.text = item.name
        holder.binding.textAddress.text = item.address
        holder.binding.textStatus.text = statusText(context, state)
        holder.binding.textStatus.setTextColor(statusColor(context, state))
        holder.binding.statusDot.setImageResource(statusDot(state))

        holder.binding.buttonMenu.setOnClickListener { v ->
            PopupMenu(context, v).apply {
                if (state == ServerConnState.SERVER_CONN_STATE_OPEN) {
                    menu.add(Menu.NONE, 1, 0, context.getString(R.string.disconnect)).setOnMenuItemClickListener {
                        onDisconnect(item)
                        true
                    }
                } else {
                    menu.add(Menu.NONE, 1, 0, context.getString(R.string.connect)).setOnMenuItemClickListener {
                        onConnect(item)
                        true
                    }
                }
                menu.add(Menu.NONE, 2, 1, context.getString(R.string.edit)).setOnMenuItemClickListener {
                    onEdit(item)
                    true
                }
                menu.add(Menu.NONE, 3, 2, context.getString(R.string.delete)).setOnMenuItemClickListener {
                    onDelete(item)
                    true
                }
                show()
            }
        }
    }

    private fun statusText(context: android.content.Context, state: ServerConnState): String = when (state) {
        ServerConnState.SERVER_CONN_STATE_OPEN -> context.getString(R.string.server_status_open)
        ServerConnState.SERVER_CONN_STATE_OPENING -> context.getString(R.string.server_status_opening)
        else -> context.getString(R.string.server_status_closed)
    }

    private fun statusColor(context: android.content.Context, state: ServerConnState): Int = when (state) {
        ServerConnState.SERVER_CONN_STATE_OPEN -> ContextCompat.getColor(context, R.color.status_open)
        ServerConnState.SERVER_CONN_STATE_OPENING -> ContextCompat.getColor(context, R.color.status_opening)
        else -> ContextCompat.getColor(context, R.color.status_closed)
    }

    private fun statusDot(state: ServerConnState): Int = when (state) {
        ServerConnState.SERVER_CONN_STATE_OPEN -> R.drawable.dot_open
        ServerConnState.SERVER_CONN_STATE_OPENING -> R.drawable.dot_opening
        else -> R.drawable.dot_closed
    }

    object DiffCallback : DiffUtil.ItemCallback<ServerInfo>() {
        override fun areItemsTheSame(oldItem: ServerInfo, newItem: ServerInfo): Boolean {
            return oldItem.uuid == newItem.uuid
        }

        override fun areContentsTheSame(oldItem: ServerInfo, newItem: ServerInfo): Boolean {
            return oldItem == newItem
        }
    }
}