package io.nativeplanet.grove.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.nativeplanet.grove.GroveApp
import io.nativeplanet.grove.R
import io.nativeplanet.grove.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class GroveSyncService : Service() {

    companion object {
        private const val CHANNEL_ID = "grove_sync"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GroveSyncService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GroveSyncService::class.java))
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Syncing..."))
        startSync()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        scope.cancel()
    }

    private fun startSync() {
        syncJob = scope.launch {
            val repository = GroveApp.instance.repository

            while (isActive) {
                try {
                    val pendingCount = repository.pendingUploads.first()

                    if (pendingCount > 0) {
                        updateNotification("Uploading $pendingCount files...")
                        repository.retryPendingUploads()
                    }

                    if (repository.isConnected.value) {
                        updateNotification("Connected")
                        repository.syncAll()
                    } else {
                        updateNotification("Offline - will sync when connected")
                    }
                } catch (e: Exception) {
                    updateNotification("Sync error: ${e.message}")
                }

                delay(60_000)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Grove Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background sync for Grove files"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("Grove")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }
}
