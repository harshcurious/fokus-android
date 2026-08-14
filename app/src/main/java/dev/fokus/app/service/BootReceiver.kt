package dev.fokus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Restarts the timer service after a reboot so the screen-unlock auto-start keeps
 * working. Starting a foreground service from the background is allowed here because
 * the app holds the "display over other apps" permission, which is on the exemption
 * list; if it is not granted yet, the service simply starts on the next app launch.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            ContextCompat.startForegroundService(context, FokusService.intent(context))
        } catch (e: Exception) {
            Log.w("BootReceiver", "Could not start service on boot", e)
        }
    }
}
