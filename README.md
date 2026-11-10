# Wallify

<a href="README.ru.md"><kbd>Русский</kbd></a>

Wallify runs HTML, CSS and JavaScript as live wallpapers on Android 8.0 and later. Import a ZIP, open a local folder or pick a wallpaper from the [catalog](https://github.com/YouRooni/Wallify-Repo).

## Make a wallpaper

Start with an `index.html` and a `manifest.json`. See the [sample](sample_wallpaper_m3_lite), [wallpaper API](WALLPAPER_API.md) and [Material 3 kit](docs/WALLIFY_M3_UI_KIT.md).

## Build

Use JDK 21 and Android SDK Platform 37. Set `ANDROID_HOME` to the SDK directory.

```sh
sh gradlew assembleDebug
```

On Windows, run `gradlew.bat assembleDebug`. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

[MIT](LICENSE) · Author: [YouRooni.t.me](https://t.me/YouRooni)
