package net.tyt4.friendnet.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import net.tyt4.friendnet.MainActivity
import net.tyt4.friendnet.R
import net.tyt4.friendnet.grpc.GrpcClient
import net.tyt4.friendnet.gen.StopRequest
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GoBackendService : Service() {
    private val TAG = "GoBackendService"
    private val CHANNEL_ID = "friendnet_service"
    private val NOTIFICATION_ID = 1
    
    private var process: Process? = null
    private val binder = LocalBinder()
    private lateinit var binaryManager: BinaryManager

    inner class LocalBinder : Binder() {
        fun getService(): GoBackendService = this@GoBackendService
    }

    override fun onCreate() {
        super.onCreate()
        binaryManager = BinaryManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (process == null) {
            thread {
                startBackend()
            }
        }

        return START_STICKY
    }

    private fun startBackend() {
        if (!binaryManager.ensureBinary()) {
            Log.e(TAG, "Failed to ensure binary is ready")
            stopSelf()
            return
        }

        val binaryPath = binaryManager.getBinaryPath()
        val dataDir = File(filesDir, "backend_data")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }

        BackendSettingsSeeder.seed(this, dataDir)

        val socketPath = File(filesDir, "backend.sock").absolutePath
        val cmd = arrayOf(
            binaryPath,
            "-headless",
            "-datadir", dataDir.absolutePath,
            "-webaddr", "unix://$socketPath",
            "-davaddr", "http://127.0.0.1:20043",
        )
        
        try {
            val builder = ProcessBuilder(*cmd)
                .directory(filesDir)
                .redirectErrorStream(true)
            
            // Set environment variables to help Go and its libraries find appropriate directories
            builder.environment()["HOME"] = filesDir.absolutePath
            builder.environment()["TMPDIR"] = cacheDir.absolutePath
            
            val proc = builder.start()
            process = proc
            
            Log.i(TAG, "Started backend process with data dir: ${dataDir.absolutePath}")
            
            // Consume stdout/stderr
            proc.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, "Backend: $line")
                    if (line?.contains("\"token\":\"") == true) {
                        try {
                            val token = line!!.substringAfter("\"token\":\"").substringBefore("\"")
                            GrpcClient.setToken(token)
                            Log.i(TAG, "Parsed RPC token from backend logs")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse token from line: $line", e)
                        }
                    }
                }
            }
            
            val exitCode = proc.waitFor()
            Log.i(TAG, "Backend process exited with code $exitCode")
            process = null
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error running backend", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        thread {
            try {
                GrpcClient.getInstance().blockingStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .stop(StopRequest.getDefaultInstance())
            } catch (e: Exception) {
                Log.e(TAG, "Error calling stop RPC", e)
            }
            
            process?.let {
                it.destroy()
                if (!it.waitFor(3, TimeUnit.SECONDS)) {
                    it.destroyForcibly()
                }
            }
            GrpcClient.getInstance().shutdown()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        val name = "FriendNet Service"
        val descriptionText = "Running FriendNet background process"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, GoBackendService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FriendNet")
            .setContentText("Running in background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }
}
