package com.jhwu.clauncher.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.IntentCompat
import com.jhwu.clauncher.MainActivity
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

    /** Downloads the update and hands it to the system installer, which asks the user to confirm. */
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
                val apk = File(context.cacheDir, APK_NAME)
                download(update.apkUrl, apk) { _state.value = UpdateState.Downloading(it) }
                _state.value = UpdateState.Installing
                commitInstall(context, apk)
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Update failed (${e.message ?: e.javaClass.simpleName})")
        }
    }

    internal fun onInstallFailed(message: String) {
        _state.value = UpdateState.Failed(message)
    }

    private fun commitInstall(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // An activity, not a broadcast: the installer is a system process, so it may start it even
            // after the update has killed CLauncher, which lets the new version reopen itself.
            val callback = PendingIntent.getActivity(
                context,
                sessionId,
                Intent(context, InstallResultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                // Mutable so the installer can add its result extras; the intent is explicit.
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(callback.intentSender)
        }
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

/** Receives the result of the install session started by [Updater.install]. Shows no UI of its own. */
class InstallResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { startActivity(it) }
            }
            // The update replaced and stopped CLauncher; open the new version.
            PackageInstaller.STATUS_SUCCESS -> startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.onInstallFailed("Update cancelled")
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Updater.onInstallFailed(
                    "This version is signed with a different key. Uninstall CLauncher and install the new APK once by hand",
                )
            else -> Updater.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Update failed (status $status)",
            )
        }
        finish()
    }
}
