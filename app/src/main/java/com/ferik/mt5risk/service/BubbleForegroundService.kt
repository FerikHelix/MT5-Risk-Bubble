package com.ferik.mt5risk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ferik.mt5risk.MainActivity
import com.ferik.mt5risk.R
import com.ferik.mt5risk.ui.overlay.OverlayController

/**
 * Keeps the bubble alive while MetaTrader 5 is in use. A persistent notification is required for
 * that, and it carries a Stop action that removes the bubble.
 */
class BubbleForegroundService : Service() {

    private var overlay: OverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBubble()
            return START_NOT_STICKY
        }
        startInForeground()
        if (overlay == null) overlay = OverlayController(this).also { it.start() }
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        overlay?.stop()
        overlay = null
        running = false
        super.onDestroy()
    }

    private fun stopBubble() {
        overlay?.stop()
        overlay = null
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Bubble", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the MT5 Risk Bubble is on"
            },
        )

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BubbleForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("MT5 Risk Bubble is on")
            .setContentText("Tap the bubble over MetaTrader 5 to size a lot")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "com.ferik.mt5risk.STOP_BUBBLE"
        private const val CHANNEL_ID = "bubble"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BubbleForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BubbleForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
