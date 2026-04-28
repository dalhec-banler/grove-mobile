package io.nativeplanet.grove.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.nativeplanet.grove.GroveApp
import io.nativeplanet.grove.R
import io.nativeplanet.grove.data.ConnectionState
import io.nativeplanet.grove.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class GroveSyncService : Service() {

    companion object {
        private const val TAG = "GroveSyncService"
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
    private var reconnectJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasNetwork = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Starting..."))
        startSync()
        observeConnectionState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
        syncJob?.cancel()
        reconnectJob?.cancel()
        scope.cancel()
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                hasNetwork = true
                onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                hasNetwork = false
                updateNotification("Offline - waiting for network")
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)

        val activeNetwork = connectivityManager.activeNetwork
        hasNetwork = activeNetwork != null &&
            connectivityManager.getNetworkCapabilities(activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister network callback", e)
            }
        }
    }

    private fun onNetworkAvailable() {
        val repository = GroveApp.instance.repository
        val client = GroveApp.instance.urbitClient

        if (client.connectionState.value == ConnectionState.RECONNECTING ||
            client.connectionState.value == ConnectionState.DISCONNECTED) {
            scheduleReconnect()
        }
    }

    private fun observeConnectionState() {
        scope.launch {
            GroveApp.instance.urbitClient.connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        updateNotification("Connected")
                        GroveApp.instance.urbitClient.resetReconnectDelay()
                    }
                    ConnectionState.CONNECTING -> updateNotification("Connecting...")
                    ConnectionState.RECONNECTING -> {
                        updateNotification("Reconnecting...")
                        scheduleReconnect()
                    }
                    ConnectionState.DISCONNECTED -> updateNotification("Disconnected")
                    ConnectionState.ERROR -> {
                        val error = GroveApp.instance.urbitClient.lastError.value
                        updateNotification("Error: $error")
                        scheduleReconnect()
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!hasNetwork) return
        reconnectJob?.cancel()

        val client = GroveApp.instance.urbitClient
        val delay = client.getReconnectDelay()

        Log.d(TAG, "Scheduling reconnect in ${delay}ms")
        reconnectJob = scope.launch {
            delay(delay)
            // TODO: Need stored credentials to reconnect automatically
            // For now, just try to sync if we have a session
            if (client.connectionState.value != ConnectionState.CONNECTED) {
                GroveApp.instance.repository.syncAll()
            }
        }
    }

    private fun startSync() {
        syncJob = scope.launch {
            val repository = GroveApp.instance.repository

            while (isActive) {
                try {
                    val pendingCount = repository.pendingUploads.first()

                    if (pendingCount > 0 && hasNetwork) {
                        updateNotification("Uploading $pendingCount files...")
                        repository.retryPendingUploads()
                    }

                    if (repository.isConnected.value && hasNetwork) {
                        repository.syncAll()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync error", e)
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
