package com.medreminder.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen alert shown over the lock screen. Stays until each medicine is confirmed. */
class AlarmActivity : Activity() {

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#102027"))
            isFillViewport = true
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        RingService.listener = { render() }
        render()
    }

    override fun onPause() {
        RingService.listener = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        render()
    }

    private fun render() {
        val rings = RingService.snapshot()
        if (rings.isEmpty()) {
            finish()
            return
        }
        content.removeAllViews()

        content.addView(TextView(this).apply {
            text = "💊  İlaç alma vakti"
            setTextColor(Color.WHITE)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        for (r in rings) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Color.parseColor("#1C313A"), 20)
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }
            card.addView(TextView(this).apply {
                text = r.name
                setTextColor(Color.WHITE)
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
            })
            val sub = buildString {
                if (r.dose.isNotBlank()) append(r.dose).append("  •  ")
                append("planlanan saat ").append(formatTime(r.minute))
            }
            card.addView(TextView(this).apply {
                text = sub
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 18f
                setPadding(0, dp(4), 0, dp(16))
            })
            card.addView(Button(this).apply {
                text = "✓  İLACI ALDIM"
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = rounded(Color.parseColor("#2E7D32"), 16)
                setOnClickListener { RingService.markTaken(this@AlarmActivity, r.code) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)))

            content.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) })
        }

        content.addView(TextView(this).apply {
            text = "Onaylayana kadar alarm çalmaya devam eder."
            setTextColor(Color.parseColor("#78909C"))
            gravity = Gravity.CENTER
            textSize = 14f
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }
}
