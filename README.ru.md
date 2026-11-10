# Wallify

<a href="README.md"><kbd>English</kbd></a>

Wallify запускает HTML, CSS и JavaScript как живые обои на Android 8.0 и новее. Обои можно импортировать из ZIP, открыть из папки или выбрать в [каталоге](https://github.com/YouRooni/Wallify-Repo).

## Свои обои

Начните с `index.html` и `manifest.json`. В репозитории есть [пример](sample_wallpaper_m3_lite), [описание API](WALLPAPER_API.md) и [компоненты Material 3](docs/WALLIFY_M3_UI_KIT.md).

## Сборка

Нужны JDK 21 и Android SDK Platform 37. Укажите путь к SDK в `ANDROID_HOME`.

```sh
sh gradlew assembleDebug
```

На Windows используйте `gradlew.bat assembleDebug`. APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

[MIT](LICENSE) · Author: [YouRooni.t.me](https://t.me/YouRooni)
