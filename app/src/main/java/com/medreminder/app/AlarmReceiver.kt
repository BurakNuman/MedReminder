package com.medreminder.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TAKEN = "com.medreminder.app.TAKEN"
        const val EXTRA_CODE = "code"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_TAKEN) {
            RingService.markTaken(ctx, intent.getIntExtra(EXTRA_CODE, 0))
            return
        }

        val medId = intent.getIntExtra(Scheduler.EXTRA_MED_ID, -1)
        val minute = intent.getIntExtra(Scheduler.EXTRA_MINUTE, -1)
        val med = Store.load(ctx).firstOrNull { it.id == medId } ?: return
        if (minute !in med.times) return

        // Book tomorrow's alarm right away so the daily schedule never breaks,
        // even if the user doesn't react to this one.
        Scheduler.schedule(ctx, medId, minute)

        RingService.ring(ctx, Ring(Scheduler.requestCode(medId, minute), med.name, med.dose, minute))
    }
}
