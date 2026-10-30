package com.jhwu.clauncher.data

import android.content.Context
import org.json.JSONArray

/** A landscape photo shipped in assets/photos, with the credit its license requires. */
data class BundledPhoto(val asset: String, val title: String, val credit: String) {
    val uri: String get() = "file:///android_asset/photos/$asset"
}

object BundledPhotos {
    @Volatile private var cached: List<BundledPhoto>? = null

    /** Reads assets/photos/credits.json, written by tools/fetch_photos.py. */
    fun all(context: Context): List<BundledPhoto> = cached ?: runCatching {
        val json = JSONArray(context.assets.open("photos/credits.json").bufferedReader().use { it.readText() })
        (0 until json.length()).map {
            val entry = json.getJSONObject(it)
            BundledPhoto(entry.getString("asset"), entry.getString("title"), entry.getString("credit"))
        }
    }.getOrDefault(emptyList()).also { cached = it }

    fun find(context: Context, asset: String) = all(context).firstOrNull { it.asset == asset }
}
