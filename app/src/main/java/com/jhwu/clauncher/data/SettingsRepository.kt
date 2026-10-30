package com.jhwu.clauncher.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "settings")

sealed interface Background {
    data class Preset(val id: String) : Background

    /** A user-picked image, copied to [SettingsRepository.wallpaperFile]. [version] changes on every pick. */
    data class Image(val version: Long) : Background
}

class SettingsRepository(private val context: Context) {
    val wallpaperFile: File get() = File(context.filesDir, "wallpaper")

    /** Package names in the order the user arranged them. Apps not in the list go at the end. */
    val appOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[APP_ORDER]?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
    }

    val background: Flow<Background> = context.dataStore.data.map { prefs ->
        val value = prefs[BACKGROUND].orEmpty()
        when {
            value.startsWith(IMAGE_PREFIX) ->
                Background.Image(value.removePrefix(IMAGE_PREFIX).toLongOrNull() ?: 0L)
            value.startsWith(PRESET_PREFIX) -> Background.Preset(value.removePrefix(PRESET_PREFIX))
            else -> Background.Preset(DEFAULT_PRESET_ID)
        }
    }

    suspend fun setAppOrder(packageNames: List<String>) {
        context.dataStore.edit { it[APP_ORDER] = packageNames.joinToString("\n") }
    }

    suspend fun setPreset(id: String) {
        context.dataStore.edit { it[BACKGROUND] = PRESET_PREFIX + id }
    }

    /** Copies the image into app storage, so it survives the source app revoking access. */
    suspend fun setImage(uri: Uri) {
        withContext(Dispatchers.IO) {
            val temp = File(context.filesDir, "wallpaper.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: return@withContext
            temp.renameTo(wallpaperFile)
        }
        if (wallpaperFile.exists()) {
            context.dataStore.edit { it[BACKGROUND] = IMAGE_PREFIX + System.currentTimeMillis() }
        }
    }

    companion object {
        const val DEFAULT_PRESET_ID = "midnight"
        private val APP_ORDER = stringPreferencesKey("app_order")
        private val BACKGROUND = stringPreferencesKey("background")
        private const val PRESET_PREFIX = "preset:"
        private const val IMAGE_PREFIX = "image:"
    }
}
