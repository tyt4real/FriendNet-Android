package net.tyt4.friendnet.ui.servers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.DialogAddServerBinding
import net.tyt4.friendnet.databinding.FragmentServersBinding
import net.tyt4.friendnet.gen.CreateServerRequest
import net.tyt4.friendnet.gen.ServerInfo
import net.tyt4.friendnet.gen.UpdateServerRequest

class ServersFragment : Fragment() {

    private var _binding: FragmentServersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ServersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_server)
        binding.emptyState.emptyTitle.setText(R.string.servers_empty_title)
        binding.emptyState.emptySubtitle.setText(R.string.servers_empty_subtitle)

        val adapter = ServerAdapter(
            onConnect = { viewModel.connectServer(it.uuid); showSnackbar(getString(R.string.connect_requested, it.name)) },
            onDisconnect = { viewModel.disconnectServer(it.uuid); showSnackbar(getString(R.string.disconnect_requested, it.name)) },
            onEdit = { showServerDialog(it) },
            onDelete = { confirmDelete(it) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        viewModel.servers.observe(viewLifecycleOwner) { servers ->
            adapter.submitList(servers)
            binding.emptyState.root.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        binding.fabAddServer.setOnClickListener { showServerDialog(null) }
    }

    private fun showServerDialog(existing: ServerInfo?) {
        val dialogBinding = DialogAddServerBinding.inflate(layoutInflater)
        val isEdit = existing != null

        if (isEdit) {
            dialogBinding.editName.setText(existing!!.name)
            dialogBinding.editAddress.setText(existing.address)
            dialogBinding.editRoom.setText(existing.room)
            dialogBinding.editUsername.setText(existing.username)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) getString(R.string.edit_server) else getString(R.string.add_server))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.save), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = dialogBinding.editName.text.toString().trim()
                        val address = dialogBinding.editAddress.text.toString().trim()
                        val room = dialogBinding.editRoom.text.toString().trim()
                        val username = dialogBinding.editUsername.text.toString().trim()
                        val password = dialogBinding.editPassword.text.toString()

                        if (name.isEmpty() || address.isEmpty()) {
                            showSnackbar(getString(R.string.error_occurred))
                            return@setOnClickListener
                        }

                        if (isEdit) {
                            val builder = UpdateServerRequest.newBuilder().setUuid(existing!!.uuid)
                            builder.name = name
                            builder.address = address
                            if (room.isNotEmpty()) builder.room = room
                            if (username.isNotEmpty()) builder.username = username
                            if (password.isNotEmpty()) builder.password = password
                            viewModel.updateServer(builder.build()) { result ->
                                result.onSuccess {
                                    showSnackbar(getString(R.string.server_updated))
                                }.onFailure {
                                    showSnackbar(errorMessage(it))
                                }
                            }
                        } else {
                            val request = CreateServerRequest.newBuilder()
                                .setName(name)
                                .setAddress(address)
                                .setRoom(room)
                                .setUsername(username)
                                .setPassword(password)
                                .build()
                            viewModel.createServer(request) { result ->
                                result.onSuccess {
                                    showSnackbar(getString(R.string.server_created))
                                }.onFailure {
                                    showSnackbar(errorMessage(it))
                                }
                            }
                        }
                        dismiss()
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(server: ServerInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_server_title)
            .setMessage(getString(R.string.delete_server_message, server.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteServer(server.uuid)
                showSnackbar(getString(R.string.server_deleted))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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