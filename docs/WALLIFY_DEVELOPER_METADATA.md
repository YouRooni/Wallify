# Wallify: метаданные, JS bridge и M3 UI Kit

Актуально для Wallify `1.0.0 (9)` и UI Kit `1.1.0`.

## 1. Метаданные в manifest.json

Минимальный манифест:

```json
{
  "name": "Название обоев",
  "description": "Короткое описание",
  "author": "@author",
  "version": "1.0.0",
  "minAppVersion": ">=1.0.0",
  "main": "index.html",
  "settingsPath": "settings/index.html",
  "icon": "icon.png",
  "cover": "cover.png",
  "orientations": ["portrait", "landscape"],
  "themeSupport": ["light", "dark"],
  "supportsMonet": true,
  "tags": ["material", "abstract", "particles"],
  "useGyroscope": false,
  "isLite": false,
  "animateOn": "both",
  "animationDuration": 3.0,
  "animationDurationScreenOn": 1.2,
  "animationDurationScreenOff": 0.4,
  "animationDurationUnlock": 2.5
}
```

`tags` — обычные короткие поисковые слова в нижнем регистре. Лучше использовать
несколько точных тегов (`material`, `nature`, `abstract`, `particles`, `minimal`),
а не длинное описание. `orientations` принимает `portrait` и/или `landscape`;
`themeSupport` — `light` и/или `dark`; `supportsMonet` сообщает каталогу, что
обои используют палитру Material You.

Если есть оба изображения, `icon` используется в квадратных местах каталога, а
`cover` — как широкая обложка. Если заполнено только одно, оно автоматически
становится fallback для обеих ролей.

## 2. Метаданные в JavaScript

После загрузки страницы приложение создаёт `window.WallpaperEngine`:

```js
window.addEventListener('wallpaperEngineReady', (event) => {
  const metadata = event.detail;
  console.log(metadata);
});

const metadata = window.WallpaperEngine.getMetadata();
```

Пример полной структуры (значения зависят от устройства):

```json
{
  "accentColors": {
    "primary": "#FF9153",
    "onPrimary": "#521F00",
    "primaryContainer": "#FF7A25",
    "onPrimaryContainer": "#3F1700",
    "primaryFixed": "#FF7A25",
    "primaryFixedDim": "#F26B00",
    "secondary": "#F68E32",
    "onSecondary": "#452000",
    "secondaryContainer": "#924C00",
    "onSecondaryContainer": "#FFF6F2",
    "secondaryFixed": "#FFC69D",
    "secondaryFixedDim": "#FFB379",
    "tertiary": "#FFCD73",
    "onTertiary": "#614400",
    "tertiaryContainer": "#FEBB28",
    "onTertiaryContainer": "#563B00",
    "tertiaryFixed": "#FEBB28",
    "tertiaryFixedDim": "#EFAD16",
    "background": "#1C0900",
    "onBackground": "#FFDFCB",
    "surface": "#1C0900",
    "surfaceBright": "#482200",
    "surfaceDim": "#1C0900",
    "surfaceContainerLowest": "#000000",
    "surfaceContainerLow": "#230E00",
    "surfaceContainer": "#2C1300",
    "surfaceContainerHigh": "#351800",
    "surfaceContainerHighest": "#3E1D00",
    "surfaceVariant": "#3E1D00",
    "onSurface": "#FFDFCB",
    "onSurfaceVariant": "#D89F74",
    "outline": "#9C6A44",
    "outlineVariant": "#683E1C",
    "inverseSurface": "#FFF4EF",
    "inverseOnSurface": "#492505",
    "inversePrimary": "#994100",
    "surfaceTint": "#FF9153",
    "error": "#F2B8B5",
    "onError": "#601410",
    "errorContainer": "#8C1D18",
    "onErrorContainer": "#F9DEDC",
    "scrim": "#000000"
  },
  "battery": { "isCharging": false, "level": 79, "plugged": "none" },
  "device": { "androidVersion": 36, "appVersion": "1.0.0", "manufacturer": "OnePlus", "model": "CPH2653" },
  "theme": "dark",
  "screen": "home",
  "isUnlocked": true,
  "isLite": false,
  "animateOn": "both",
  "animationDuration": 3.0,
  "animationDurationScreenOn": 1.2,
  "animationDurationScreenOff": 0.4,
  "animationDurationUnlock": 2.5
}
```

Поле `accentColors` — это вся Material You палитра, а не только три акцентных
цвета. Не дублируй её в отдельный `materialColors`: используй именно
`metadata.accentColors`.

Основные события с теми же метаданными в `event.detail`:

```js
['wallpaperEngineReady', 'wallpaperUpdate', 'wallpaperThemeChange',
 'wallpaperScreenOn', 'wallpaperScreenOff', 'wallpaperUnlock',
 'wallpaperPause', 'wallpaperResume', 'wallpaperCharging',
 'wallpaperDischarging'].forEach((name) => {
  window.addEventListener(name, (event) => {
    const metadata = event.detail;
    // metadata.battery.level, metadata.accentColors.primary, ...
  });
});
```

## 3. JS bridge доступен и в настройках

`settings/index.html` загружается в такой же WebView bridge. Там доступны те же:

```js
window.WallpaperEngine.getMetadata();
window.WallpaperEngine.getSetting('speed', 'normal');
window.WallpaperEngine.getAllSettings();
window.WallpaperEngine.saveSetting('speed', 'fast');
```

Поэтому экран настроек может использовать тот же Monet и те же события:

```js
window.addEventListener('wallpaperEngineReady', ({ detail }) => {
  document.documentElement.style.setProperty('--primary', detail.accentColors.primary);
});
```

UI Kit также автоматически инжектируется в страницу настроек. Не нужно копировать
его CSS/JS в комплект обоев.

## 4. Wallify M3 UI Kit 1.1.0

Размечай обычные элементы через `data-wallify-m3`:

```html
<input type="range" data-wallify-m3="slider" min="0" max="100" value="50">
<input type="checkbox" data-wallify-m3="switch">
<button data-wallify-m3="button">Основная</button>
<button data-wallify-m3="tonal-button">Тональная</button>
<button data-wallify-m3="primary-tonal-button">Primary tonal</button>
<button data-wallify-m3="outlined-button">Outlined</button>
<button data-wallify-m3="text-button">Text</button>
<section data-wallify-m3="card">Карточка</section>
<div data-wallify-m3="card-group"><section data-wallify-m3="card">Группа</section></div>
<div data-wallify-m3="divider"></div>
<div data-wallify-m3="row"><span data-wallify-m3="row-icon">★</span><span data-wallify-m3="row-content">Подпись</span></div>
```

Kit поддерживает `slider`, `switch`, `button`, `tonal-button`,
`primary-tonal-button`, `tertiary-button`, `outlined-button`,
`tertiary-outlined-button`, `text-button`, `tertiary-text-button`, `card`,
`card-group`, `divider`, `row`, `row-icon`, `row-content`, `row-title` и `row-desc`.
Слайдер остаётся настоящим `input`, поэтому его `input`/`change` обработчики не
ломаются. Новые элементы в DOM подхватываются автоматически.

Текущие исходники Kit:

- `E:\VSC\HTMLWallpaper\app\src\main\assets\wallify-ui\wallify-m3.css`;
- `E:\VSC\HTMLWallpaper\app\src\main\assets\wallify-ui\wallify-m3.js`.
