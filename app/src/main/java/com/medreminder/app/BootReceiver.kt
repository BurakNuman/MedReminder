package com.medreminder.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-books all alarms after reboot, app update, or a clock / time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleAll(ctx)
    }
}
