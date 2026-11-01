package com.vamz.xosisland

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val GRAY = Color.parseColor("#9A9A9A")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    }

    override fun onResume() {
        super.onResume()
        setContentView(buildUi())
    }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun section(t: String) = tv(t, 14f, GRAY).apply { setPadding(dp(8), dp(24), 0, dp(8)) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(Color.parseColor("#1C1C1E")); cornerRadius = dp(24).toFloat() }
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }

    private fun textCol(title: String, sub: String?) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(tv(title, 17f, Color.WHITE))
        if (sub != null) addView(tv(sub, 13f, GRAY))
    }

    private fun switchRow(title: String, sub: String?, key: String): View {
        val sp = Prefs.sp(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        row.addView(textCol(title, sub), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply {
            isChecked = sp.getBoolean(key, true)
            setOnCheckedChangeListener { _, c -> sp.edit().putBoolean(key, c).apply() }
        }
        row.addView(sw)
        return row
    }

    private fun permRow(title: String, ok: Boolean, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { action() }
        }
        row.addView(tv(title, 16f, Color.WHITE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(tv(if (ok) "Aktif" else "Buka ›", 14f, if (ok) Color.parseColor("#34C759") else Color.parseColor("#2F6BFF"), true))
        return row
    }

    private fun listenerOn(): Boolean =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true

    private fun buildUi(): View {
        val sp = Prefs.sp(this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val sv = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(40))
        }
        sv.addView(col)

        col.addView(tv("Dynamic Bar", 30f, Color.WHITE, true))
        col.addView(tv("VamzXOS · semua notifikasi & aktivitas langsung di atas layar", 13f, GRAY))

        col.addView(section("Izin"))
        val perms = card()
        perms.addView(permRow("Akses Notifikasi", listenerOn()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        perms.addView(permRow("Tampil di atas aplikasi lain", Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        perms.addView(permRow("Bebas hemat baterai", pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        col.addView(perms)
        col.addView(tv("Kalau Akses Notifikasi abu-abu: Info Aplikasi › ⋮ › Izinkan setelan terbatas.", 12f, GRAY).apply { setPadding(dp(8), dp(8), 0, 0) })

        col.addView(section("Umum"))
        val main = card()
        main.addView(switchRow("Aktifkan VamzXOS", "Master switch", Prefs.ENABLED))
        main.addView(switchRow("Semua Notifikasi", "Tampilkan setiap notifikasi masuk di pill", Prefs.ALL))
        col.addView(main)

        col.addView(section("Aktivitas Langsung"))
        val live = card()
        live.addView(switchRow("Google Maps", "Layanan navigasi", Prefs.MAPS))
        live.addView(switchRow("Pemutar Musik", "Layanan musik yang sedang diputar", Prefs.MEDIA))
        live.addView(switchRow("Layanan Lainnya", "Layanan lainnya yang mendukung notifikasi pembaruan langsung", Prefs.OTHER))
        col.addView(live)

        col.addView(section("Tampilan"))
        val look = card()
        look.addView(tv("Posisi vertikal pill", 16f, Color.WHITE).apply { setPadding(0, dp(12), 0, dp(4)) })
        val sb = SeekBar(this).apply {
            max = 60
            progress = sp.getInt(Prefs.Y, 10)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    sp.edit().putInt(Prefs.Y, s?.progress ?: 10).apply()
                    IslandService.instance?.showTest()
                }
            })
        }
        look.addView(sb)
        val test = Button(this).apply {
            text = "Tes pill"
            setOnClickListener {
                val s = IslandService.instance
                if (s == null) Toast.makeText(context, "Aktifkan Akses Notifikasi dulu", Toast.LENGTH_SHORT).show()
                else s.showTest()
            }
        }
        look.addView(test)
        col.addView(look)
        return sv
    }
}
