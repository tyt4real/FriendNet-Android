package net.tyt4.friendnet.ui.logs

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.FragmentLogsBinding
import net.tyt4.friendnet.util.LogcatReader

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    private val saveLogs = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) saveTo(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRefresh.setOnClickListener { loadLogs() }
        binding.btnSave.setOnClickListener {
            saveLogs.launch("friendnet_logs_${System.currentTimeMillis()}.txt")
        }

        loadLogs()
    }

    private fun loadLogs() {
        binding.btnRefresh.isEnabled = false
        binding.textLogs.setText(R.string.logs_loading)
        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) { LogcatReader.dump(requireContext()) }
            binding.textLogs.text = logs
            binding.btnRefresh.isEnabled = true
        }
    }

    private fun saveTo(uri: Uri) {
        val text = binding.textLogs.text.toString()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray())
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            showSnackbar(if (ok) getString(R.string.logs_saved) else getString(R.string.logs_save_failed))
        }
    }

    private fun showSnackbar(message: String) {
        binding.root.post {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}