package com.medreminder.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules one exact, repeating-by-rescheduling daily alarm per (medicine, time).
 * Uses setAlarmClock(), which fires exactly on time even in Doze and needs no special permission.
 */
object Scheduler {
    const val EXTRA_MED_ID = "med_id"
    const val EXTRA_MINUTE = "minute"

    fun requestCode(medId: Int, minute: Int) = medId * 1440 + minute

    private fun alarmIntent(ctx: Context, medId: Int, minute: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .putExtra(EXTRA_MED_ID, medId)
            .putExtra(EXTRA_MINUTE, minute)
        return PendingIntent.getBroadcast(
            ctx, requestCode(medId, minute), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTrigger(minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minute / 60)
        cal.set(Calendar.MINUTE, minute % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun schedule(ctx: Context, medId: Int, minute: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(nextTrigger(minute), show),
            alarmIntent(ctx, medId, minute)
        )
    }

    fun cancel(ctx: Context, medId: Int, minute: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(alarmIntent(ctx, medId, minute))
    }

    fun scheduleMedicine(ctx: Context, m: Medicine) = m.times.forEach { schedule(ctx, m.id, it) }

    fun cancelMedicine(ctx: Context, m: Medicine) = m.times.forEach { cancel(ctx, m.id, it) }

    fun scheduleAll(ctx: Context) = Store.load(ctx).forEach { scheduleMedicine(ctx, it) }
}
