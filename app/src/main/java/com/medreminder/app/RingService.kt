package com.medreminder.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Foreground service that plays a looping alarm sound + vibration and keeps going
 * until every ringing medicine has been confirmed as taken.
 */
class RingService : Service() {

    companion object {
        const val ACTION_RING = "com.medreminder.app.RING"
        const val ACTION_RESUME = "com.medreminder.app.RESUME"
        private const val CHANNEL_ID = "medicine_alarm"
        private const val NOTIF_ID = 1001

        // All of this is touched from the main thread only.
        private val rings = LinkedHashMap<Int, Ring>()
        private var instance: RingService? = null
        var listener: (() -> Unit)? = null

        fun snapshot(): List<Ring> = rings.values.toList()

        private fun restoreIfNeeded(ctx: Context) {
            if (rings.isEmpty()) Store.loadRinging(ctx).forEach { rings[it.code] = it }
        }

        fun ring(ctx: Context, r: Ring) {
            restoreIfNeeded(ctx)
            rings[r.code] = r
            Store.saveRinging(ctx, rings.values)
            ctx.startForegroundService(Intent(ctx, RingService::class.java).setAction(ACTION_RING))
            listener?.invoke()
        }

        fun markTaken(ctx: Context, code: Int) {
            restoreIfNeeded(ctx)
            rings.remove(code)
            Store.saveRinging(ctx, rings.values)
            val svc = instance
            if (svc != null) {
                svc.refresh()
            } else if (rings.isEmpty()) {
                ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
            } else {
                ctx.startForegroundService(Intent(ctx, RingService::class.java).setAction(ACTION_RESUME))
            }
            listener?.invoke()
        }
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        createChannel()
        restoreIfNeeded(this)

        // Must be called promptly after startForegroundService().
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }

        if (rings.isEmpty()) {
            shutdown()
            return START_NOT_STICKY
        }
        startAlert()
        return START_STICKY
    }

    override fun onDestroy() {
        stopAlert()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun refresh() {
        if (rings.isEmpty()) {
            shutdown()
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        }
    }

    private fun shutdown() {
        stopAlert()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- sound / vibration ----------

    private fun startAlert() {
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "medreminder:ring").apply {
                acquire(6 * 60 * 60 * 1000L)
            }
        }
        if (player == null) {
            ensureAudible()
            val candidates = listOf(
                RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
            for (uri in candidates) {
                if (uri != null && tryPlay(uri)) break
            }
            startVibration()
        }
    }

    private fun tryPlay(uri: Uri): Boolean {
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            false
        }
    }

    /** A silent alarm stream would defeat the purpose, so make sure it is at least 60%. */
    private fun ensureAudible() {
        try {
            val am = getSystemService(AudioManager::class.java)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val min = (max * 0.6).toInt().coerceAtLeast(1)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < min) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, min, 0)
            }
        } catch (e: Exception) {
        }
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            vibrator().vibrate(effect, attrs)
        } catch (e: Exception) {
        }
    }

    private fun stopAlert() {
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        try {
            vibrator().cancel()
        } catch (e: Exception) {
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ---------- notification ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "İlaç alarmları", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "İlacı aldığınızı onaylayana kadar çalar"
            setSound(null, null)          // sound is played by the service itself
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val list = rings.values.toList()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (list.isEmpty()) "" else list.joinToString("\n") {
            if (it.dose.isBlank()) it.name else "${it.name} – ${it.dose}"
        }
        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("İlaç alma vakti")
            .setContentText(text.lineSequence().firstOrNull() ?: "")
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        for (r in list.take(3)) {
            val taken = PendingIntent.getBroadcast(
                this, r.code,
                Intent(this, AlarmReceiver::class.java)
                    .setAction(AlarmReceiver.ACTION_TAKEN)
                    .putExtra(AlarmReceiver.EXTRA_CODE, r.code),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val label = if (list.size == 1) "ALDIM" else "Aldım: ${r.name}"
            @Suppress("DEPRECATION")
            b.addAction(0, label, taken)
        }
        return b.build()
    }
}
