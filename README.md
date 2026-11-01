# CLauncher

A clean, Apple TV-style home screen for Google TV / Android TV 12+.

- A grid of rounded 16:9 app tiles that lift and glow when focused, over a full-screen wallpaper.
- **Reorder apps:** long-press OK on a tile. It starts to wiggle; move it with the D-pad and press OK (or Back) to drop it.
- **Settings** (the gear at the top right, or the Menu button on the remote):
  - **Background:** pick a gradient, or choose your own image if your TV has a file picker.
  - **Apps:** show or hide any app on the home screen.
  - **About & updates:** see the installed version, and download and install the latest release.
- Shows TV apps and sideloaded phone apps, and updates live as apps are installed or removed.

Built with Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), DataStore and Coil. Min SDK 31 (Android 12).

## Install

Every push to `main` publishes a new [release](../../releases/latest). Download `CLauncher.apk` from it and sideload it:

```shell
adb connect <tv-ip>
adb install -r CLauncher.apk
```

## Release signing (one-time setup)

In-app updates only install over the current app when every release is signed with the same key. Create a keystore once on any computer with Java:

```shell
keytool -genkeypair -keystore clauncher.jks -alias clauncher -keyalg RSA -keysize 4096 \
  -validity 10000 -storetype PKCS12 -dname "CN=CLauncher"
base64 -w0 clauncher.jks   # macOS: base64 -i clauncher.jks
```

Then add two repository secrets under **Settings → Secrets and variables → Actions**:

- `RELEASE_KEYSTORE_BASE64`: the base64 output
- `RELEASE_KEYSTORE_PASSWORD`: the password you chose

Keep `clauncher.jks` somewhere safe. Without these secrets the build still works, but each release is signed with a throwaway key, so updating means uninstalling first.

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
