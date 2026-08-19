package net.tyt4.friendnet.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.ItemFileBinding
import net.tyt4.friendnet.gen.FileMeta
import net.tyt4.friendnet.util.Formats

class FileAdapter(
    private val onClick: (FileMeta) -> Unit
) : ListAdapter<FileMeta, FileAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.binding.root.context

        holder.binding.textName.text = item.name
        holder.binding.textInfo.text = if (item.isDir) {
            context.getString(R.string.folder)
        } else {
            Formats.bytes(item.size)
        }

        if (item.isDir) {
            holder.binding.imageIcon.setImageResource(R.drawable.ic_folder)
            holder.binding.imageAction.setImageResource(R.drawable.ic_arrow_up)
            holder.binding.imageAction.rotation = 90f
            holder.binding.imageAction.alpha = 0.4f
        } else {
            holder.binding.imageIcon.setImageResource(R.drawable.ic_file)
            holder.binding.imageAction.setImageResource(R.drawable.ic_download)
            holder.binding.imageAction.rotation = 0f
            holder.binding.imageAction.alpha = 1f
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    object DiffCallback : DiffUtil.ItemCallback<FileMeta>() {
        override fun areItemsTheSame(oldItem: FileMeta, newItem: FileMeta): Boolean {
            return oldItem.name == newItem.name && oldItem.isDir == newItem.isDir
        }

        override fun areContentsTheSame(oldItem: FileMeta, newItem: FileMeta): Boolean {
            return oldItem == newItem
        }
    }
}