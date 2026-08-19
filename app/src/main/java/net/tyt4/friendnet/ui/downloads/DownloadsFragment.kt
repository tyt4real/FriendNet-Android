package net.tyt4.friendnet.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.FragmentDownloadsBinding
import net.tyt4.friendnet.gen.DownloadManagerItem
import net.tyt4.friendnet.repository.ServerRepository
import net.tyt4.friendnet.repository.SettingsRepository
import net.tyt4.friendnet.util.FilenameReplacer
import java.io.File

class DownloadsFragment : Fragment() {
    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_download)
        binding.emptyState.emptyTitle.setText(R.string.downloads_empty_title)
        binding.emptyState.emptySubtitle.setText(R.string.downloads_empty_subtitle)

        val adapter = DownloadAdapter(
            onCancel = { item ->
                viewModel.cancelDownload(item.uuid) { error ->
                    if (error == null) showSnackbar(getString(R.string.download_canceled))
                    else showSnackbar(errorMessage(error))
                }
            },
            onResume = { item ->
                viewModel.resumeDownload(item.uuid) { error ->
                    if (error == null) showSnackbar(getString(R.string.download_resumed))
                    else showSnackbar(errorMessage(error))
                }
            },
            onRemove = { item ->
                viewModel.removeDownload(item.uuid) { error ->
                    if (error == null) showSnackbar(getString(R.string.download_removed))
                    else showSnackbar(errorMessage(error))
                }
            },
            onOpen = { item ->
                openDownloadedFile(item)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        viewModel.items.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.emptyState.root.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.speeds.observe(viewLifecycleOwner) { speeds ->
            adapter.setSpeeds(speeds)
        }

        ServerRepository.servers.observe(viewLifecycleOwner) { servers ->
            adapter.setServerNames(servers.associate { it.uuid to it.name })
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun openDownloadedFile(item: DownloadManagerItem) {
        val file = resolveDownloadedFile(item)
        if (file == null || !file.exists()) {
            showSnackbar(getString(R.string.download_file_missing))
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val extension = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showSnackbar(getString(R.string.no_app_to_open_file))
        } catch (e: Exception) {
            showSnackbar(getString(R.string.error_occurred))
        }
    }

    private fun resolveDownloadedFile(item: DownloadManagerItem): File? {
        return try {
            val completeDir = SettingsRepository(requireContext()).getCompleteDownloadDir()
            val relative = FilenameReplacer.replacePath("${item.peerUsername}-${item.serverUuid}/${item.filePath}")
            File(completeDir, relative)
        } catch (e: Exception) {
            null
        }
    }

    private fun showSnackbar(message: String) {
        val view = binding.root
        view.post { Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show() }
    }

    private fun errorMessage(t: Throwable): String {
        val detail = t.message
        return if (detail.isNullOrEmpty()) getString(R.string.error_occurred) else "$detail"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}