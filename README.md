# CLauncher

A clean, Apple TV-style home screen for Google TV / Android TV 12+.

- A grid of rounded 16:9 app tiles that lift and glow when focused, over a full-screen wallpaper.
- **Reorder apps:** long-press OK on a tile. It starts to wiggle; move it with the D-pad and press OK (or Back) to drop it.
- **Change the background:** open **Settings** (top right, or the Menu button on the remote) and pick a gradient, or choose your own image if your TV has a file picker.
- Shows TV apps and sideloaded phone apps, and updates live as apps are installed or removed.

Built with Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), DataStore and Coil. Min SDK 31 (Android 12).

## Install

Every push to `main` builds APKs in GitHub Actions. Open the latest **Build APK** run, download the `clauncher-apk` artifact, and sideload it:

```shell
adb connect <tv-ip>
adb install -r app-release.apk
```

## Make it the default home screen

The easiest and safest way is to remap the remote's Home button to CLauncher with an app such as Button Mapper.

On Chromecast with Google TV you can disable the stock launcher instead. Do this at your own risk:

```shell
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
# Undo:
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```

Then press Home and choose CLauncher.

## Build locally

Open the project in Android Studio, or run `./gradlew assembleDebug` (needs JDK 17 and the Android SDK).
