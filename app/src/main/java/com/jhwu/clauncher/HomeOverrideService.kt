package com.jhwu.clauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * "Replace Google TV home": opens CLauncher instead of the stock Google TV home screen.
 *
 * Google TV routes app launches through its own launcher, so disabling that launcher to make
 * CLauncher the default breaks launching apps. Instead this accessibility service (allowed to start
 * activities from the background) does two things:
 * - Catches the remote's Home button, like Button Mapper, so the stock home never shows.
 * - Covers the stock home whenever it still comes to the front, e.g. at startup or when an app
 *   exits. The stock home packages are listed in res/xml/home_override_service.xml.
 */
class HomeOverrideService : AccessibilityService() {
    private var lastOpened = 0L

    override fun onServiceConnected() {
        Log.i(TAG, "Connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            Log.i(TAG, "Home pressed")
            openLauncher(force = true)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        Log.i(TAG, "Stock home window: ${event.packageName} ${event.className}")
        // The stock launcher may flash briefly while it hands off an app CLauncher just launched.
        if (SystemClock.elapsedRealtime() - lastAppLaunch < APP_LAUNCH_GRACE_MS) return
        openLauncher(force = false)
    }

    private fun openLauncher(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastOpened < 1_000) return
        lastOpened = now
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open CLauncher", e)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "HomeOverride"
        private const val APP_LAUNCH_GRACE_MS = 3_000L

        /** Set when CLauncher launches an app. */
        @Volatile
        var lastAppLaunch = 0L

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val component = ComponentName(context, HomeOverrideService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
