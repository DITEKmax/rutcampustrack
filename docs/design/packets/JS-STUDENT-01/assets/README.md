# Активы

`asset-index.json` сопоставляет 58 actual generated-code Figma URL с 16
каноническими SVG-байтами в `sha256/`. Per-call manifests `assets-r*.json` сохраняют
каждый source URL, call и hash; один хеш хранится на диске лишь один раз.

Все SVG были проверены при загрузке как UTF-8 XML с SVG root. Figma URL краткоживущие
и не являются единственным источником локального байта.

`asset-download-diagnostics.json` описывает исключённый boilerplate example URL:
это не production asset и не gap.
