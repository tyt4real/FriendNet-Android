package net.tyt4.friendnet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import net.tyt4.friendnet.grpc.GrpcClient
import java.io.File

class FriendNetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GrpcClient.init(File(filesDir, "backend.sock").absolutePath)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val name = "FriendNet Service"
        val descriptionText = "Running FriendNet background process"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel("friendnet_service", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
