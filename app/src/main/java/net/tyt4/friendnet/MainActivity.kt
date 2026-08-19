package net.tyt4.friendnet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.tyt4.friendnet.backend.GoBackendService
import net.tyt4.friendnet.databinding.ActivityMainBinding
import net.tyt4.friendnet.grpc.EventStreamManager
import net.tyt4.friendnet.grpc.GrpcClient
import net.tyt4.friendnet.gen.Event
import net.tyt4.friendnet.repository.DownloadRepository
import net.tyt4.friendnet.repository.ServerRepository

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val legacyStorageLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_servers, R.id.navigation_browse, R.id.navigation_downloads, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        EventStreamManager.getInstance().addListener(Event.Type.TYPE_STOP) {
            stopBackendService()
        }

        EventStreamManager.getInstance().addOnConnectListener {
            ServerRepository.refresh()
            DownloadRepository.refresh()
        }

        startBackend()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun startBackend() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.loadingText.text = getString(R.string.starting_backend)

        val serviceIntent = Intent(this, GoBackendService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        lifecycleScope.launch {
            val ready = withContext(Dispatchers.IO) {
                GrpcClient.getInstance().waitUntilReady(10000)
            }

            if (ready) {
                binding.loadingOverlay.visibility = View.GONE
                ensureStorageAccess()
                net.tyt4.friendnet.repository.SettingsRepository(this@MainActivity).initFirstLaunchIfNeeded()
                EventStreamManager.getInstance().start()
                Toast.makeText(this@MainActivity, getString(R.string.backend_connected), Toast.LENGTH_SHORT).show()
            } else {
                binding.loadingText.text = getString(R.string.failed_to_connect_retry)
                binding.loadingOverlay.setOnClickListener {
                    startBackend()
                }
            }
        }
    }

    private fun ensureStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.storage_access_title)
                    .setMessage(R.string.storage_access_message)
                    .setPositiveButton(R.string.storage_access_grant) { _, _ ->
                        try {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        } else {
            val missing = arrayListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (missing.isNotEmpty()) {
                legacyStorageLauncher.launch(missing.toTypedArray())
            }
        }
    }

    private fun stopBackendService() {
        val serviceIntent = Intent(this, GoBackendService::class.java).apply {
            action = "STOP"
        }
        startService(serviceIntent)
    }
}