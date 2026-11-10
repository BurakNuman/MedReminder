package com.medreminder.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var listBox: LinearLayout
    private lateinit var banner: TextView
    private var meds = mutableListOf<Medicine>()

    private val teal = Color.parseColor("#00897B")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Scheduler.scheduleAll(this)   // idempotent; keeps alarms fresh
        updateBanner()
        refreshList()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateBanner()
    }

    // ---------- UI ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(teal)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(36), dp(12), dp(14))
        }
        header.addView(TextView(this).apply {
            text = "💊 İlaç Hatırlatıcı"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply {
            text = "Test alarmı"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            isAllCaps = false
            setOnClickListener {
                RingService.ring(this@MainActivity, Ring(-1, "Test ilacı", "bu bir testtir", 0))
                startActivity(Intent(this@MainActivity, AlarmActivity::class.java))
            }
        })
        root.addView(header)

        banner = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#FFE0B2"))
            setTextColor(Color.parseColor("#E65100"))
            textSize = 14f
            setPadding(dp(16), dp(10), dp(16), dp(10))
            visibility = View.GONE
        }
        root.addView(banner)

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(ScrollView(this).apply { addView(listBox) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(Button(this).apply {
            text = "+  İlaç ekle"
            textSize = 18f
            setTextColor(Color.WHITE)
            background = rounded(teal, 14)
            setOnClickListener { showEditor(null) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)).apply {
            setMargins(dp(16), dp(8), dp(16), dp(16))
        })

        setContentView(root)
    }

    private fun updateBanner() {
        val nm = getSystemService(NotificationManager::class.java)
        when {
            !nm.areNotificationsEnabled() -> {
                banner.text = "⚠ Bildirimler kapalı – alarmlar görüntülenemez. Düzeltmek için dokunun."
                banner.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
                banner.visibility = View.VISIBLE
            }
            Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent() -> {
                banner.text = "⚠ Alarmın kilit ekranında açılması için tam ekran bildirimlere izin verin. Düzeltmek için dokunun."
                banner.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")))
                }
                banner.visibility = View.VISIBLE
            }
            else -> banner.visibility = View.GONE
        }
    }

    private fun refreshList() {
        meds = Store.load(this)
        listBox.removeAllViews()
        if (meds.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "Henüz ilaç yok.\nİlk hatırlatıcınızı oluşturmak için “İlaç ekle” düğmesine dokunun."
                setTextColor(Color.GRAY)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            })
            return
        }
        for (m in meds.sortedBy { it.times.minOrNull() ?: 0 }) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Color.WHITE, 14)
                elevation = dp(2).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(6))
            }
            card.addView(TextView(this).apply {
                text = m.name
                textSize = 20f
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
            })
            if (m.dose.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = m.dose
                    textSize = 15f
                    setTextColor(Color.GRAY)
                })
            }
            card.addView(TextView(this).apply {
                text = "⏰  " + m.times.sorted().joinToString("   ") { formatTime(it) }
                textSize = 18f
                setTextColor(teal)
                setPadding(0, dp(8), 0, dp(4))
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            row.addView(textButton("Düzenle") { showEditor(m) })
            row.addView(textButton("Sil") { confirmDelete(m) })
            card.addView(row)

            listBox.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
        }
    }

    private fun textButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(teal)
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { onClick() }
    }

    private fun confirmDelete(m: Medicine) {
        AlertDialog.Builder(this)
            .setTitle("${m.name} silinsin mi?")
            .setMessage("Tüm hatırlatıcıları kaldırılacak.")
            .setPositiveButton("Sil") { _, _ ->
                Scheduler.cancelMedicine(this, m)
                meds.removeAll { it.id == m.id }
                Store.save(this, meds)
                refreshList()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // ---------- add / edit dialog ----------

    private fun showEditor(existing: Medicine?) {
        val times = (existing?.times ?: mutableListOf()).toMutableList()

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val nameEt = EditText(this).apply {
            hint = "İlaç adı"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            setText(existing?.name ?: "")
        }
        val doseEt = EditText(this).apply {
            hint = "Doz (örn. 1 tablet, 500 mg) – isteğe bağlı"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setText(existing?.dose ?: "")
        }
        val timesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun renderTimes() {
            timesBox.removeAllViews()
            times.sort()
            for (t in times.toList()) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                row.addView(TextView(this).apply {
                    text = "⏰  " + formatTime(t)
                    textSize = 20f
                    setTextColor(Color.BLACK)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply {
                    text = "✕"
                    textSize = 20f
                    setTextColor(Color.parseColor("#C62828"))
                    setPadding(dp(16), dp(8), dp(8), dp(8))
                    setOnClickListener { times.remove(t); renderTimes() }
                })
                timesBox.addView(row)
            }
        }
        renderTimes()

        val addTime = Button(this).apply {
            text = "+ Saat ekle"
            isAllCaps = false
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    val v = h * 60 + m
                    if (!times.contains(v)) times.add(v)
                    renderTimes()
                }, 8, 0, true).show()
            }
        }

        form.addView(nameEt)
        form.addView(doseEt)
        form.addView(TextView(this).apply {
            text = "Hatırlatma saatleri (her gün)"
            setPadding(0, dp(16), 0, dp(4))
            setTextColor(Color.GRAY)
        })
        form.addView(timesBox)
        form.addView(addTime)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "İlaç ekle" else "İlacı düzenle")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Kaydet", null)
            .setNegativeButton("İptal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameEt.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "İlaç adını girin", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (times.isEmpty()) {
                    Toast.makeText(this, "En az bir saat ekleyin", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val dose = doseEt.text.toString().trim()
                if (existing != null) {
                    Scheduler.cancelMedicine(this, existing)   // old times
                    existing.name = name
                    existing.dose = dose
                    existing.times.clear()
                    existing.times.addAll(times.sorted())
                    Store.save(this, meds)
                    Scheduler.scheduleMedicine(this, existing)
                } else {
                    val m = Medicine(Store.nextId(this), name, dose, times.sorted().toMutableList())
                    meds.add(m)
                    Store.save(this, meds)
                    Scheduler.scheduleMedicine(this, m)
                }
                dialog.dismiss()
                refreshList()
            }
        }
        dialog.show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }
}
