package com.sadrazam.lusifer.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sadrazam.lusifer.MainActivity
import com.sadrazam.lusifer.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Sürekli hafif dinleme: ekran kapalı/kilitliyken de uyandırma kelimesini bekler. */
class LusiferService : Service() {
    companion object {
        const val ACTION_STOP = "com.sadrazam.lusifer.STOP"
        private const val CH = "lusifer_listen"
    }

    private var job: Job? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        try {
            ServiceCompat.startForeground(this, 1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Throwable) {
            Assistant.status.value = "Dinleme servisi başlatılamadı: ${e.message}"
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            val pm = getSystemService(PowerManager::class.java)
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lusifer:listen").also { it.acquire() }
            Assistant.serviceOn.value = true
            job = Assistant.scope.launch {
                try {
                    Assistant.runLoop()
                } finally {
                    Assistant.serviceOn.value = false
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "LUSİFER dinleme", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LusiferService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("LUSİFER dinliyor")
            .setContentText("\"LUSİFER\" veya \"Hey LUSİFER\" deyin")
            .setContentIntent(open)
            .addAction(0, "DURDUR", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        try { Assistant.vosk.cancel() } catch (_: Throwable) {}
        try { Assistant.tts.stop() } catch (_: Throwable) {}
        try { wl?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        Assistant.serviceOn.value = false
        super.onDestroy()
    }
}
