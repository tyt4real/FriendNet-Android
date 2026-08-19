package net.tyt4.friendnet.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.DialogSearchBinding
import net.tyt4.friendnet.databinding.FragmentBrowseBinding
import net.tyt4.friendnet.gen.FileMeta
import net.tyt4.friendnet.gen.OnlineUserInfo
import net.tyt4.friendnet.gen.ServerConnState
import net.tyt4.friendnet.gen.ServerInfo
import net.tyt4.friendnet.gen.StreamSearchResponse
import net.tyt4.friendnet.repository.DownloadRepository
import net.tyt4.friendnet.util.Formats

class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by viewModels()

    private val servers = mutableListOf<ServerInfo>()
    private val users = mutableListOf<OnlineUserInfo>()
    private var currentServerUuid: String? = null
    private var currentUsername: String? = null
    private var updatingSpinners = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val serverAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val userAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerServer.adapter = serverAdapter
        binding.spinnerUser.adapter = userAdapter

        binding.spinnerServer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingSpinners) return
                val server = servers.getOrNull(position)
                val uuid = server?.uuid
                if (uuid != currentServerUuid) {
                    currentServerUuid = uuid
                    currentUsername = null
                    viewModel.selectServer(uuid)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerUser.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingSpinners) return
                val username = users.getOrNull(position)?.username
                if (username != currentUsername) {
                    currentUsername = username
                    viewModel.selectUser(username)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val fileAdapter = FileAdapter { file ->
            if (file.isDir) {
                viewModel.navigateInto(file)
            } else {
                showDownloadDialog(file)
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = fileAdapter

        viewModel.servers.observe(viewLifecycleOwner) { list ->
            val openServers = list.filter { it.state.connState == ServerConnState.SERVER_CONN_STATE_OPEN }

            updatingSpinners = true
            servers.clear()
            servers.addAll(openServers)
            serverAdapter.clear()
            serverAdapter.addAll(openServers.map { it.name })

            val previousIndex = openServers.indexOfFirst { it.uuid == currentServerUuid }
            val restored = openServers.getOrNull(previousIndex)
            binding.spinnerServer.setSelection(previousIndex.coerceAtLeast(0))
            updatingSpinners = false

            if (restored?.uuid != currentServerUuid) {
                currentServerUuid = restored?.uuid
                currentUsername = null
                viewModel.selectServer(restored?.uuid)
            }
            updateEmptyState()
        }

        viewModel.onlineUsers.observe(viewLifecycleOwner) { list ->
            updatingSpinners = true
            users.clear()
            users.addAll(list)
            userAdapter.clear()
            userAdapter.addAll(list.map { it.username })

            val previousIndex = list.indexOfFirst { it.username == currentUsername }
            val restored = list.getOrNull(previousIndex)
            binding.spinnerUser.setSelection(previousIndex.coerceAtLeast(0))
            updatingSpinners = false

            if (restored?.username != currentUsername) {
                currentUsername = restored?.username
                viewModel.selectUser(restored?.username)
            }
            updateEmptyState()
        }

        viewModel.files.observe(viewLifecycleOwner) {
            fileAdapter.submitList(it)
            updateEmptyState()
        }

        viewModel.currentPath.observe(viewLifecycleOwner) { path ->
            binding.textPath.text = path
            binding.buttonUp.isEnabled = viewModel.canNavigateUp()
        }

        binding.buttonUp.setOnClickListener { viewModel.navigateUp() }
        binding.buttonSearch.setOnClickListener { showSearchDialog() }
    }

    private fun showDownloadDialog(file: FileMeta) {
        val size = if (file.size > 0) Formats.bytes(file.size) else ""
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setMessage(getString(R.string.file_from_user, size, currentUsername ?: ""))
            .setPositiveButton(R.string.download) { _, _ ->
                viewModel.downloadFile(file) { error ->
                    if (error == null) {
                        showSnackbar(getString(R.string.download_started))
                    } else {
                        showSnackbar(errorMessage(error))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSearchDialog() {
        if (currentServerUuid == null) {
            showSnackbar(getString(R.string.no_servers_connected))
            return
        }

        val dialogBinding = DialogSearchBinding.inflate(layoutInflater)
        val resultAdapter = SearchResultAdapter { result ->
            downloadSearchResult(result)
        }
        dialogBinding.recyclerResults.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.recyclerResults.adapter = resultAdapter

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.search)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .create()

        var resultsLive: androidx.lifecycle.LiveData<List<StreamSearchResponse>>? = null

        dialogBinding.editQuery.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString()?.trim().orEmpty()
            resultsLive?.let { removeObservers(it) }
            if (query.length < 2) {
                resultAdapter.submitList(emptyList())
                dialogBinding.textSearchHint.text = getString(R.string.search_hint)
                return@doOnTextChanged
            }
            dialogBinding.textSearchHint.text = getString(R.string.searching)
            val live = viewModel.search(query)
            resultsLive = live
            live.observe(viewLifecycleOwner) { results ->
                resultAdapter.submitList(results)
                if (results.isEmpty()) {
                    dialogBinding.textSearchHint.text = getString(R.string.search_empty)
                } else {
                    dialogBinding.textSearchHint.text = getString(R.string.searching)
                }
            }
        }

        dialog.setOnShowListener {
            dialogBinding.editQuery.requestFocus()
        }
        dialog.show()
    }

    private fun downloadSearchResult(result: StreamSearchResponse) {
        val serverUuid = currentServerUuid ?: return
        val directory = result.directoryPath.trimEnd('/')
        val path = if (directory.isEmpty()) "/${result.file.name}" else "$directory/${result.file.name}"
        DownloadRepository.queueFileDownload(serverUuid, result.username, path) { error ->
            if (error == null) {
                showSnackbar(getString(R.string.download_started))
            } else {
                showSnackbar(errorMessage(error))
            }
        }
    }

    private fun removeObservers(live: androidx.lifecycle.LiveData<*>) {
        live.removeObservers(viewLifecycleOwner)
    }

    private fun updateEmptyState() {
        val empty = binding.emptyState
        when {
            servers.isEmpty() -> {
                empty.emptyIcon.setImageResource(R.drawable.ic_server)
                empty.emptyTitle.setText(R.string.no_servers_connected)
                empty.emptySubtitle.setText("")
                empty.root.visibility = View.VISIBLE
            }
            users.isEmpty() -> {
                empty.emptyIcon.setImageResource(R.drawable.ic_info)
                empty.emptyTitle.setText(R.string.no_users_online)
                empty.emptySubtitle.setText("")
                empty.root.visibility = View.VISIBLE
            }
            binding.recyclerView.adapter?.itemCount == 0 -> {
                empty.emptyIcon.setImageResource(R.drawable.ic_folder)
                empty.emptyTitle.setText(R.string.folder_empty)
                empty.emptySubtitle.setText("")
                empty.root.visibility = View.VISIBLE
            }
            else -> empty.root.visibility = View.GONE
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