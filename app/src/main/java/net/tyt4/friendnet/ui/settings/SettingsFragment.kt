package net.tyt4.friendnet.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import net.tyt4.friendnet.R
import net.tyt4.friendnet.databinding.FragmentSettingsBinding
import net.tyt4.friendnet.repository.SettingsRepository

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: SettingsRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        repository = SettingsRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository.getTransferSettings().observe(viewLifecycleOwner) { settings ->
            settings?.let {
                binding.textIncompleteDir.text = getString(R.string.settings_incomplete_dir, it.incompleteDownloadDir)
                binding.textCompleteDir.text = getString(R.string.settings_complete_dir, it.completeDownloadDir)
            }
        }

        binding.btnCheckUpdate.setOnClickListener {
            binding.btnCheckUpdate.isEnabled = false
            binding.textUpdateResult.setText(R.string.searching)
            binding.textUpdateResult.visibility = View.VISIBLE
            repository.checkForNewUpdate { newInfo ->
                val text = if (newInfo == null) {
                    getString(R.string.settings_no_update)
                } else {
                    getString(R.string.settings_new_update, newInfo.version)
                }
                requireActivity().runOnUiThread {
                    binding.btnCheckUpdate.isEnabled = true
                    binding.textUpdateResult.text = text
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}