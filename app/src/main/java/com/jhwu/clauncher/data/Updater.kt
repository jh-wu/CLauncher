package com.jhwu.clauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val build: Long, val apkUrl: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update from GitHub Releases. CI tags each release `build-N`, where N is also the APK's versionCode,
 * so a release is newer when its N is larger than the installed versionCode.
 */
object Updater {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/jh-wu/CLauncher/releases/latest"
    private const val APK_NAME = "CLauncher.apk"
    private const val TAG_PREFIX = "build-"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun installedBuild(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    suspend fun check(context: Context) {
        _state.value = UpdateState.Checking
        _state.value = withContext(Dispatchers.IO) {
            try {
                val release = JSONObject(fetch(LATEST_RELEASE_URL))
                val build = release.getString("tag_name").removePrefix(TAG_PREFIX).toLong()
                val assets = release.getJSONArray("assets")
                val apkUrl = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .first { it.getString("name") == APK_NAME }
                    .getString("browser_download_url")
                if (build > installedBuild(context)) UpdateState.Available(build, apkUrl) else UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Failed("Couldn't check for updates (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    /**
     * Downloads the update and opens it in the system installer. Its last screen has an "Open"
     * button, which is the only way back into CLauncher: Android 12 doesn't let an app that was
     * just replaced start itself again.
     */
    suspend fun install(context: Context, update: UpdateState.Available) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _state.value = UpdateState.Failed("Allow CLauncher to install apps, then try again")
            return
        }
        _state.value = UpdateState.Downloading(0f)
        try {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val apk = File(dir, "CLauncher-build-${update.build}.apk")
                download(update.apkUrl, apk) { _state.value = UpdateState.Downloading(it) }
                _state.value = UpdateState.Installing
                openInstaller(context, apk)
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Update failed (${e.message ?: e.javaClass.simpleName})")
        }
    }

    private fun openInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun fetch(url: String): String {
        val connection = open(url).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = open(url)
        try {
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(copied.toFloat() / total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
    }
}
