package com.medreminder.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A medicine with one or more daily reminder times (minutes since midnight). */
data class Medicine(
    val id: Int,
    var name: String,
    var dose: String,
    val times: MutableList<Int>
)

/** An alarm that is currently ringing and waiting for the user to confirm. */
data class Ring(val code: Int, val name: String, val dose: String, val minute: Int)

object Store {
    private const val PREFS = "medreminder"
    private const val KEY_LIST = "medicines"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_RINGING = "ringing"

    fun load(ctx: Context): MutableList<Medicine> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null)
            ?: return mutableListOf()
        val out = mutableListOf<Medicine>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = o.getJSONArray("times")
                val times = mutableListOf<Int>()
                for (j in 0 until t.length()) times.add(t.getInt(j))
                out.add(Medicine(o.getInt("id"), o.getString("name"), o.optString("dose", ""), times))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save(ctx: Context, list: List<Medicine>) {
        val arr = JSONArray()
        for (m in list) {
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("dose", m.dose)
                put("times", JSONArray(m.times))
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun nextId(ctx: Context): Int {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = sp.getInt(KEY_NEXT_ID, 1)
        sp.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    fun loadRinging(ctx: Context): List<Ring> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RINGING, null)
            ?: return emptyList()
        val out = mutableListOf<Ring>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Ring(o.getInt("code"), o.getString("name"), o.optString("dose", ""), o.getInt("minute")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun saveRinging(ctx: Context, rings: Collection<Ring>) {
        val arr = JSONArray()
        for (r in rings) {
            arr.put(JSONObject().apply {
                put("code", r.code)
                put("name", r.name)
                put("dose", r.dose)
                put("minute", r.minute)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RINGING, arr.toString()).apply()
    }
}

fun formatTime(minuteOfDay: Int): String =
    String.format(java.util.Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
