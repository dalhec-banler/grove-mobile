package io.nativeplanet.grove.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nativeplanet.grove.GroveApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("grove", Context.MODE_PRIVATE)
        val hasConnection = prefs.getString("ship_code", null) != null

        if (hasConnection) {
            GroveSyncService.start(context)
        }
    }
}
