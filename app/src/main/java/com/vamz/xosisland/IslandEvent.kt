package com.vamz.xosisland

import android.app.PendingIntent
import android.graphics.drawable.Drawable

enum class Kind { NAV, MEDIA, LIVE, NOTIF }

data class IslandEvent(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val chip: String?,
    val kind: Kind,
    val intent: PendingIntent?,
    val icon: Drawable?
)
