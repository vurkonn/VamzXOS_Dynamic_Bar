package com.vamz.xosisland

import android.content.Context

object Prefs {
    const val ENABLED = "enabled"
    const val ALL = "all"
    const val MAPS = "maps"
    const val MEDIA = "media"
    const val OTHER = "other"
    const val Y = "y"
    fun sp(c: Context) = c.getSharedPreferences("vamz", Context.MODE_PRIVATE)
}
