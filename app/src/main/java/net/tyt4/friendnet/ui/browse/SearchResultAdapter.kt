package net.tyt4.friendnet.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.ItemSearchResultBinding
import net.tyt4.friendnet.gen.StreamSearchResponse

class SearchResultAdapter(
    private val onClick: (StreamSearchResponse) -> Unit
) : ListAdapter<StreamSearchResponse, SearchResultAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.binding.root.context
        val file = item.file

        holder.binding.textName.text = file.name
        val directory = item.directoryPath.trimEnd('/')
        holder.binding.textMeta.text = context.getString(
            R.string.file_from_user,
            item.username,
            if (directory.isEmpty()) "/" else directory
        )

        holder.itemView.setOnClickListener { onClick(item) }
    }

    object DiffCallback : DiffUtil.ItemCallback<StreamSearchResponse>() {
        override fun areItemsTheSame(oldItem: StreamSearchResponse, newItem: StreamSearchResponse): Boolean {
            return oldItem.username == newItem.username &&
                oldItem.directoryPath == newItem.directoryPath &&
                oldItem.file.name == newItem.file.name
        }

        override fun areContentsTheSame(oldItem: StreamSearchResponse, newItem: StreamSearchResponse): Boolean {
            return oldItem == newItem
        }
    }
}