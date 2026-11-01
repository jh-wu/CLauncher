package com.jhwu.clauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * "Replace Google TV home": whenever the stock Google TV home screen comes to the front (Home
 * button, boot, leaving an app), bring CLauncher up over it.
 *
 * Google TV routes app launches through its own launcher, so disabling that launcher to make
 * CLauncher the default breaks launching apps. Watching for it with an accessibility service keeps
 * it enabled; accessibility services are also allowed to start activities from the background.
 * The stock home packages are listed in res/xml/home_override_service.xml.
 */
class HomeOverrideService : AccessibilityService() {
    private var lastOpened = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Only whole screens (activities), not the stock launcher's dialogs or panels.
        if (event.className?.endsWith("Activity") != true) return
        val now = SystemClock.elapsedRealtime()
        // The stock launcher may flash briefly while it hands off an app CLauncher just launched.
        if (now - lastAppLaunch < APP_LAUNCH_GRACE_MS || now - lastOpened < 1_000) return
        lastOpened = now
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onInterrupt() = Unit

    companion object {
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
