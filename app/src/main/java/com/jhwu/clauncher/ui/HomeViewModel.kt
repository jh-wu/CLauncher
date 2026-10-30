package com.jhwu.clauncher.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jhwu.clauncher.data.AppRepository
import com.jhwu.clauncher.data.Background
import com.jhwu.clauncher.data.LaunchableApp
import com.jhwu.clauncher.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val appRepository = AppRepository(application)
    private val settings = SettingsRepository(application)

    // While a tile is being moved, the order lives here; it is saved when the move finishes.
    private val pendingOrder = MutableStateFlow<List<String>?>(null)

    private val _movingPackage = MutableStateFlow<String?>(null)
    val movingPackage: StateFlow<String?> = _movingPackage.asStateFlow()

    val apps: StateFlow<List<LaunchableApp>> =
        combine(appRepository.apps, settings.appOrder, pendingOrder) { apps, savedOrder, pending ->
            val position = (pending ?: savedOrder).withIndex().associate { (index, pkg) -> pkg to index }
            // Stable sort: apps the user never arranged (e.g. newly installed) keep label order at the end.
            apps.sortedBy { position[it.packageName] ?: Int.MAX_VALUE }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until loaded, so the default background doesn't flash before the saved one. */
    val background: StateFlow<Background?> =
        settings.background.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val wallpaperFile: File get() = settings.wallpaperFile

    val imagePicker: ImagePicker = ImagePicker.find(application.packageManager)

    fun launch(app: LaunchableApp) = startActivity(app.launchIntent)

    fun openSystemSettings() = startActivity(Intent(Settings.ACTION_SETTINGS))

    fun startMove(packageName: String) {
        pendingOrder.value = apps.value.map { it.packageName }
        _movingPackage.value = packageName
    }

    /** Moves the tile being moved by [offset] positions. Returns false if it would leave the grid. */
    fun moveBy(offset: Int): Boolean {
        val order = pendingOrder.value ?: return false
        val from = order.indexOf(_movingPackage.value)
        val to = from + offset
        if (from < 0 || to !in order.indices) return false
        pendingOrder.value = order.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    fun finishMove() {
        val order = pendingOrder.value ?: return
        _movingPackage.value = null
        viewModelScope.launch {
            settings.setAppOrder(order)
            pendingOrder.value = null
        }
    }

    fun setPresetBackground(id: String) {
        viewModelScope.launch { settings.setPreset(id) }
    }

    fun setImageBackground(uri: Uri) {
        viewModelScope.launch { settings.setImage(uri) }
    }

    private fun startActivity(intent: Intent) {
        try {
            getApplication<Application>().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Can't start $intent", e)
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}

/** Which system image picker the TV has. Many Google TV builds ship without one. */
enum class ImagePicker {
    OpenDocument, GetContent, None;

    companion object {
        fun find(packageManager: android.content.pm.PackageManager): ImagePicker {
            fun available(action: String) = packageManager.resolveActivity(
                Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 0,
            ) != null
            return when {
                available(Intent.ACTION_OPEN_DOCUMENT) -> OpenDocument
                available(Intent.ACTION_GET_CONTENT) -> GetContent
                else -> None
            }
        }
    }
}
