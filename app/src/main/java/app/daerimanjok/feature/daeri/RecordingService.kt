package app.daerimanjok.feature.daeri

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
import android.util.Log
import app.daerimanjok.MainActivity
import app.daerimanjok.R

/**
 * Keeps the process running (not frozen) while a trip is being recorded.
 *
 * Android 14 freezes background apps; car-signal callbacks for a frozen app are queued
 * and delivered in one burst later, which breaks distance/time and braking detection.
 * The driver always has navigation in front, so without this the recorder is always
 * in the background. The recording logic itself stays in [DaeriController].
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "handover_trip"
        private const val NOTIFICATION_ID = 1
        private const val ARRIVAL_CHANNEL_ID = "daeri_arrival"
        private const val ARRIVAL_NOTIFICATION_ID = 2
        private const val ARRIVAL_TIMEOUT_MS = 30_000L

        fun cancelArrival(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(ARRIVAL_NOTIFICATION_ID)
        }

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, RecordingService::class.java)) }
                // e.g. ForegroundServiceStartNotAllowedException if the process was started in the background
                .onFailure { Log.w(TAG, "could not start foreground service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }

        /**
         * On arrival the app is usually behind navigation: a heads-up notification shows the
         * owner's parking spot and opens the app on tap. Skipped if notifications are off.
         */
        fun notifyArrival(context: Context, parkingSpot: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return
            nm.createNotificationChannel(
                NotificationChannel(ARRIVAL_CHANNEL_ID, context.getString(R.string.notif_arrival_channel), NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val text = if (parkingSpot.isBlank()) context.getString(R.string.notif_arrival_text_none)
            else context.getString(R.string.notif_arrival_text, parkingSpot)
            val n = Notification.Builder(context, ARRIVAL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(context.getString(R.string.notif_arrival_title))
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                // A car heads-up stays until dismissed; don't leave it over the screen.
                .setTimeoutAfter(ARRIVAL_TIMEOUT_MS)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .build()
            runCatching { nm.notify(ARRIVAL_NOTIFICATION_ID, n) }.onFailure { Log.w(TAG, "arrival notification failed", it) }
        }
    }
}
