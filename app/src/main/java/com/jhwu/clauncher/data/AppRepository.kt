package com.jhwu.clauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class LaunchableApp(
    val packageName: String,
    val label: String,
    /** The 16:9 TV banner, when the app provides one. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val launchIntent: Intent,
)

class AppRepository(private val context: Context) {
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    /** Installed apps, sorted by label. Emits again whenever a package is installed, removed or updated. */
    val apps: Flow<List<LaunchableApp>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }
        .conflate()
        .map { loadApps() }
        .flowOn(Dispatchers.IO)

    private fun loadApps(): List<LaunchableApp> {
        // TV apps declare LEANBACK_LAUNCHER; sideloaded phone apps only declare LAUNCHER.
        // When an app has both, its TV activity wins.
        val activities = LinkedHashMap<String, Pair<ActivityInfo, String>>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                .map { it.activityInfo }
                .filter { it.packageName != context.packageName }
                .forEach { activities.putIfAbsent(it.packageName, it to category) }
        }
        return activities.values
            .map { (info, category) -> info.toLaunchableApp(category) }
            .sortedBy { it.label.lowercase() }
    }

    private fun ActivityInfo.toLaunchableApp(category: String): LaunchableApp {
        val banner = loadBanner(packageManager) ?: applicationInfo.loadBanner(packageManager)
        val icon = if (banner == null) loadIcon(packageManager) else null
        return LaunchableApp(
            packageName = packageName,
            label = loadLabel(packageManager).toString(),
            banner = banner?.toBitmap()?.asImageBitmap(),
            icon = icon?.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)?.asImageBitmap(),
            launchIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(category)
                .setComponent(ComponentName(packageName, name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    private companion object {
        const val ICON_SIZE_PX = 192
    }
}
