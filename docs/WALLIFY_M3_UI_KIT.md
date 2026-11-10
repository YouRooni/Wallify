# Wallify M3 UI Kit

Wallify автоматически подключает UI Kit к WebView живых обоев, превью и странице
настроек. Подключать CSS или JavaScript вручную не нужно.

Версия API: `WallifyM3.version` — `1.1.0`.

## Разметка

Пометь обычный HTML-элемент атрибутом `data-wallify-m3`:

```html
<input type="range" min="0" max="100" value="45" data-wallify-m3="slider">
<input type="checkbox" data-wallify-m3="switch">
<button data-wallify-m3="button">Применить</button>
<button data-wallify-m3="tonal-button">Сбросить</button>
<button data-wallify-m3="outlined-button">Подробнее</button>
<input type="text" data-wallify-m3="text-field" placeholder="Название">
<select data-wallify-m3="select"><option>Мягко</option></select>
<section data-wallify-m3="card">Содержимое карточки</section>
<span data-wallify-m3="chip">Новый</span>
```

Поддерживаемые значения: `slider`, `switch`, `button`, `tonal-button`,
`primary-tonal-button`, `tertiary-button`, `outlined-button`,
`tertiary-outlined-button`, `text-button`, `tertiary-text-button`, `text-field`,
`select`, `card`, `card-group`, `divider`, `row`, `row-icon`, `row-content`,
`row-title`, `row-desc`, `chip`.

Используй именно `data-wallify-m3`: одинаковых компонентов на странице может быть
сколько угодно, а ID оставляй только для логики самого комплекта.

## Поведение

UI Kit не заменяет исходные элементы. Слайдер остаётся настоящим
`input[type=range]`, поэтому существующие обработчики `input` и `change` работают
без изменений. Элементы, добавленные в DOM позднее, подхватываются автоматически.

Цвета берутся из `wallpaperMetadata.accentColors`. Если обои меняют метаданные сами,
можно вручную обновить вид:

```js
WallifyM3.setColors(window.wallpaperMetadata.accentColors);
WallifyM3.refresh();
```

## Обновление набора компонентов

Единые исходники живут в приложении:

- `app/src/main/assets/wallify-ui/wallify-m3.css` — внешний вид;
- `app/src/main/assets/wallify-ui/wallify-m3.js` — поведение и регистрация компонентов.

После обновления этих двух файлов новая версия Wallify применит изменения ко всем
совместимым обоям без правок их папок.
