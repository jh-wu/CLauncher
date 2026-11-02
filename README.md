# CLauncher

A clean home screen for Google TV, inspired by Apple TV. Your apps are a simple grid of tiles over a full-screen photo, with no ads, no recommendations, and nothing you didn't put there yourself.

- **App tiles** that lift and glow when you highlight them.
- **Your order:** arrange the tiles however you like.
- **Backgrounds:** a new photo of the day, 10 built-in landscape photos, color gradients, or your own picture.
- **Hide apps** you never use, without uninstalling them.
- **Updates from inside the app.** No computer needed once it's installed.

Works on Google TV and Android TV devices running Android 12 or newer, for example Chromecast with Google TV and Google TV Streamer.

## Install

### Option 1: on the TV, no computer needed

1. On your TV, install **Downloader** by AFTVnews from the Google Play Store.
2. Allow Downloader to install apps: **Settings → Apps → Security & restrictions → Unknown sources** (the exact menu names vary a little between TVs), then turn on Downloader.
3. Open Downloader and enter this address:
   ```
   https://github.com/jh-wu/CLauncher/releases/latest/download/CLauncher.apk
   ```
4. When the download finishes, choose **Install**, then **Open**.

If **Install** does nothing when you press it, make sure Downloader is allowed to install apps (step 2) and wait a few seconds: Google Play Protect sometimes checks the app first.

### Option 2: from a computer with adb

Download `CLauncher.apk` from the [latest release](https://github.com/jh-wu/CLauncher/releases/latest), set up adb as described in [Using adb with your TV](#using-adb-with-your-tv), then run:

```shell
adb install -r CLauncher.apk
```

## Make CLauncher your home screen

CLauncher replaces the Google TV home screen with a small helper that watches for it. Whenever you press **Home**, turn on the TV, or leave an app, you land on CLauncher.

**First try it on the TV:**

1. In CLauncher, open the gear at the top right and choose **Replace Google TV home**.
2. In the accessibility settings that open, turn on **CLauncher: replace Google TV home**.
3. Go back to CLauncher. The row should now say **On**.

**If CLauncher isn't in the TV's accessibility list** (some Google TV models hide apps that weren't installed from the Play Store), turn it on once from a computer. First set up adb as described in [Using adb with your TV](#using-adb-with-your-tv), then:

```shell
# 1. See which accessibility services are already on, so you don't switch any off.
adb shell settings get secure enabled_accessibility_services

# 2a. If that printed "null" or nothing:
adb shell settings put secure enabled_accessibility_services com.jhwu.clauncher/com.jhwu.clauncher.HomeOverrideService

# 2b. If it printed something, add CLauncher to the end, separated by a colon, for example:
#     adb shell settings put secure enabled_accessibility_services <what it printed>:com.jhwu.clauncher/com.jhwu.clauncher.HomeOverrideService

# 3. Make sure accessibility is on.
adb shell settings put secure accessibility_enabled 1
```

This setting is kept through restarts and CLauncher updates. You can turn developer options off again afterwards.

> **Don't disable the Google TV launcher.** Older guides for other launchers suggest disabling `com.google.android.apps.tv.launcherx`. On current Google TV versions every app is opened through it, so with it disabled no app can be opened from CLauncher or any other launcher. CLauncher's helper keeps it out of sight instead.

## Using CLauncher

| To | Do this |
|---|---|
| Open an app | Highlight its tile and press **OK** |
| Move an app | Hold **OK** on a tile until it wiggles, move it with the arrows, then press **OK** to drop it |
| Open settings | Highlight the gear at the top right (press **Up** from the first row) and press **OK** |
| Leave settings | Press **Back** |

In settings:

- **Background:** Photo of the day (a new landscape photo from Wikimedia Commons every day), built-in photos, color gradients, or **Choose an image…** if your TV has a file picker. Photographer and license credits are shown under each photo.
- **Apps:** press **OK** on an app to show or hide it on the home screen.
- **About & updates:** shows your version and installs new releases. The first time, Android asks you to allow CLauncher to install apps. When the installer finishes, press **Open** to return to CLauncher.
- **Replace Google TV home:** see [Make CLauncher your home screen](#make-clauncher-your-home-screen).
- **Android TV settings:** opens the TV's own settings.

## Using adb with your TV

adb (Android Debug Bridge) is a free tool from Google that lets a computer send commands to your TV over your home network. You only need it if you install from a computer or if the TV doesn't list CLauncher in its accessibility settings.

**1. Turn on developer options on the TV.**
Go to **Settings → System → About**, scroll to **Android TV OS build**, and press **OK** on it 7 times until it says you're a developer. ([Google's guide](https://developer.android.com/studio/debug/dev-options))

**2. Turn on debugging.**
Go to **Settings → System → Developer options** and turn on **USB debugging**. On newer models, such as Google TV Streamer, turn on **Wireless debugging** instead.

**3. Find the TV's IP address.**
Go to **Settings → Network & Internet**, select your network, and note the IP address, for example `192.168.1.23`.

**4. Install adb on your computer.**
- macOS with [Homebrew](https://brew.sh): `brew install --cask android-platform-tools`
- Windows, macOS or Linux: download [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools), unzip it, and run the commands from that folder.

**5. Connect.**
```shell
adb connect 192.168.1.23
```
Accept the **Allow debugging?** prompt on the TV. With **Wireless debugging**, first choose **Pair device with pairing code** on the TV and run `adb pair <ip>:<port>` with the address and code it shows, then `adb connect` with the address shown on the Wireless debugging screen. ([Google's adb guide](https://developer.android.com/tools/adb))

When you're done, you can turn **USB debugging** or **Wireless debugging** off again.

## Troubleshooting

**Apps don't open when I press their tiles, and updates don't install.**
The Google TV launcher is probably disabled. Turn it back on with adb:
```shell
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```

**Pressing Home still shows the Google TV home screen.**
The helper isn't on. Open the gear → **Replace Google TV home**. If it says **Off**, follow [Make CLauncher your home screen](#make-clauncher-your-home-screen).

**I want the Google TV home screen back.**
Turn off **CLauncher: replace Google TV home** in the TV's accessibility settings, or uninstall CLauncher under **Settings → Apps**. If you turned it on with adb, you can also turn it off with adb:
```shell
adb shell settings delete secure enabled_accessibility_services
```
This switches off all accessibility services. Turn any others back on in the TV's settings.

**Start CLauncher from a computer**
```shell
adb shell am start -n com.jhwu.clauncher/.MainActivity
```

## Photo credits

The built-in photos and the photo of the day come from [Wikimedia Commons](https://commons.wikimedia.org) and are used under their Creative Commons licenses. The photographer and license for each photo are shown in **Settings → Background**.

## For developers

CLauncher is written in Kotlin with Jetpack Compose for TV. Every push to `main` is built by GitHub Actions and published as a [release](https://github.com/jh-wu/CLauncher/releases). To build it yourself, open the project in Android Studio or run `./gradlew assembleDebug` (needs JDK 17 and the Android SDK).
