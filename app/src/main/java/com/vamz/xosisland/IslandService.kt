package com.vamz.xosisland

import android.app.Notification
import android.app.NotificationManager
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class IslandService : NotificationListenerService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        @Volatile var instance: IslandService? = null
        private val LIVE_CATS = setOf("call", "progress", "stopwatch", "alarm", "navigation", "workout")
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var view: IslandView? = null
    private val live = LinkedHashMap<String, IslandEvent>()
    private var transient: IslandEvent? = null
    private val collapse = Runnable { transient = null; render() }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onListenerConnected() {
        instance = this
        try { activeNotifications?.forEach { handle(it, false) } } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        instance = null
        h.post { remove() }
    }

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        remove()
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { handle(sbn, true) }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        live.remove(sbn.key)
        if (transient?.key == sbn.key) transient = null
        render()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        h.post {
            val sp = Prefs.sp(this)
            if (!sp.getBoolean(Prefs.ENABLED, true)) { live.clear(); transient = null }
            live.values.removeAll { !kindEnabled(it.kind) }
            remove()
            render()
        }
    }

    private fun kindEnabled(k: Kind): Boolean {
        val sp = Prefs.sp(this)
        return when (k) {
            Kind.NAV -> sp.getBoolean(Prefs.MAPS, true)
            Kind.MEDIA -> sp.getBoolean(Prefs.MEDIA, true)
            Kind.LIVE -> sp.getBoolean(Prefs.OTHER, true)
            Kind.NOTIF -> sp.getBoolean(Prefs.ALL, true)
        }
    }

    private fun prio(k: Kind) = when (k) { Kind.NAV -> 3; Kind.LIVE -> 2; Kind.MEDIA -> 1; Kind.NOTIF -> 0 }

    fun showTest() {
        val icon = try { packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
        transient = IslandEvent("test", packageName, "VamzXOS", "VamzXOS aktif",
            "Dynamic Bar siap menerima semua notifikasi", null, Kind.NOTIF, null, icon)
        h.removeCallbacks(collapse)
        h.postDelayed(collapse, 4000)
        render()
    }

    private fun handle(sbn: StatusBarNotification, popup: Boolean) {
        val sp = Prefs.sp(this)
        if (!sp.getBoolean(Prefs.ENABLED, true)) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString() ?: ""
        if (title.isEmpty() && text.isEmpty()) return

        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val cat = n.category
        val promoted = ex.getBoolean("android.requestPromotedOngoing", false)
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val progress = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0
        val isMaps = sbn.packageName == "com.google.android.apps.maps" && (ongoing || cat == "navigation")
        val isMedia = ex.containsKey("android.mediaSession") || cat == Notification.CATEGORY_TRANSPORT

        val kind = when {
            isMaps -> Kind.NAV
            isMedia -> Kind.MEDIA
            ongoing && (promoted || chrono || progress || cat in LIVE_CATS) -> Kind.LIVE
            ongoing -> return
            else -> Kind.NOTIF
        }
        if (!kindEnabled(kind)) return

        if (kind == Kind.NOTIF) {
            val rk = NotificationListenerService.Ranking()
            try {
                if (currentRanking.getRanking(sbn.key, rk) && rk.importance <= NotificationManager.IMPORTANCE_MIN) return
            } catch (_: Exception) {}
        }

        if (kind == Kind.MEDIA) {
            val playing = try {
                @Suppress("DEPRECATION")
                val tok: MediaSession.Token? =
                    if (Build.VERSION.SDK_INT >= 33) ex.getParcelable("android.mediaSession", MediaSession.Token::class.java)
                    else ex.getParcelable("android.mediaSession")
                tok?.let { MediaController(this, it).playbackState?.state == PlaybackState.STATE_PLAYING } ?: false
            } catch (e: Exception) { false }
            if (!playing) {
                live.remove(sbn.key)
                if (transient?.key == sbn.key) transient = null
                render()
                return
            }
        }

        val pm = packageManager
        val icon = try { pm.getApplicationIcon(sbn.packageName) } catch (e: Exception) { null }
        val app = try { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
        val chip = ex.getCharSequence("android.shortCriticalText")?.toString()
        val ev = IslandEvent(sbn.key, sbn.packageName, app, title, text, chip, kind, n.contentIntent, icon)

        if (kind == Kind.NOTIF) {
            if (popup) transient = ev
        } else {
            val prev = live[sbn.key]
            live[sbn.key] = ev
            val fresh = prev == null || (kind != Kind.NAV && prev.title != ev.title)
            if (popup && fresh) transient = ev
        }
        if (transient != null) {
            h.removeCallbacks(collapse)
            h.postDelayed(collapse, 5000)
        }
        render()
    }

    private fun render() {
        val ev = transient ?: live.values.maxByOrNull { prio(it.kind) }
        if (ev == null) { remove(); return }
        if (!Settings.canDrawOverlays(this)) return
        var v = view
        if (v == null) {
            val nv = IslandView(this)
            setupTouch(nv)
            try {
                wm.addView(nv, params())
                view = nv
                v = nv
            } catch (e: Exception) { return }
        }
        v.bind(ev, transient != null)
    }

    private fun remove() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
    }

    private fun params(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (Prefs.sp(this).getInt(Prefs.Y, 10) * resources.displayMetrics.density).toInt()
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.title = "VamzXOS"
        return lp
    }

    private fun setupTouch(v: View) {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val t = transient
                h.removeCallbacks(collapse)
                if (t != null) {
                    try { t.intent?.send() } catch (_: Exception) {}
                    transient = null
                } else {
                    transient = live.values.maxByOrNull { prio(it.kind) }
                    h.postDelayed(collapse, 5000)
                }
                render()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -500f) {
                    h.removeCallbacks(collapse)
                    transient = null
                    render()
                    return true
                }
                return false
            }
        })
        v.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
    }
}
