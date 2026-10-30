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
import com.jhwu.clauncher.data.BundledPhoto
import com.jhwu.clauncher.data.BundledPhotos
import com.jhwu.clauncher.data.DailyPhoto
import com.jhwu.clauncher.data.DailyPhotoRepository
import com.jhwu.clauncher.data.LaunchableApp
import com.jhwu.clauncher.data.SettingsRepository
import com.jhwu.clauncher.data.UpdateState
import com.jhwu.clauncher.data.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    private val orderedApps =
        combine(appRepository.apps, settings.appOrder, pendingOrder) { apps, savedOrder, pending ->
            val position = (pending ?: savedOrder).withIndex().associate { (index, pkg) -> pkg to index }
            // Stable sort: apps the user never arranged (e.g. newly installed) keep label order at the end.
            apps.sortedBy { position[it.packageName] ?: Int.MAX_VALUE }
        }

    val hiddenApps: StateFlow<Set<String>> =
        settings.hiddenApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Every installed app, hidden or not, for the Apps settings page. */
    val allApps: StateFlow<List<LaunchableApp>> =
        orderedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The apps shown on the home screen. */
    val apps: StateFlow<List<LaunchableApp>> =
        combine(allApps, hiddenApps) { apps, hidden -> apps.filter { it.packageName !in hidden } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until loaded, so the default background doesn't flash before the saved one. */
    val background: StateFlow<Background?> =
        settings.background.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val wallpaperFile: File get() = settings.wallpaperFile

    val dailyPhoto: StateFlow<DailyPhoto?> = DailyPhotoRepository.photo

    init {
        // Keep the photo of the day current while it's the background; checking hourly is cheap.
        viewModelScope.launch {
            settings.background.collectLatest { background ->
                while (background == Background.Daily) {
                    DailyPhotoRepository.refresh(getApplication())
                    delay(60 * 60 * 1000L)
                }
            }
        }
    }

    val imagePicker: ImagePicker = ImagePicker.find(application.packageManager)

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
        val visibleOrder = pendingOrder.value ?: return
        _movingPackage.value = null
        viewModelScope.launch {
            // Hidden apps keep their relative order, after the visible ones.
            val hidden = allApps.value.map { it.packageName }.filter { it in hiddenApps.value }
            settings.setAppOrder(visibleOrder + hidden)
            pendingOrder.value = null
        }
    }

    fun setHidden(packageName: String, hidden: Boolean) {
        viewModelScope.launch { settings.setHidden(packageName, hidden) }
    }

    val updateState: StateFlow<UpdateState> = Updater.state

    val versionName: String = Updater.installedVersionName(application)

    fun checkForUpdates() {
        viewModelScope.launch { Updater.check(getApplication()) }
    }

    fun installUpdate(update: UpdateState.Available) {
        viewModelScope.launch { Updater.install(getApplication(), update) }
    }

    /** The home screen to hand over to when quitting, normally the stock Google TV launcher. */
    fun otherHomeIntent(): Intent? {
        val app = getApplication<Application>()
        return app.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo }
            .firstOrNull { it.packageName != app.packageName }
            ?.let {
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClassName(it.packageName, it.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }

    val bundledPhotos: List<BundledPhoto> = BundledPhotos.all(application)

    fun setBundledBackground(asset: String) {
        viewModelScope.launch { settings.setBundled(asset) }
    }

    fun setDailyBackground() {
        viewModelScope.launch { settings.setDaily() }
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
