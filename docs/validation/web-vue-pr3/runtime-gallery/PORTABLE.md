# Единый HTML просмотра

Скачайте [portable.html](portable.html) через Download raw file в GitHub и откройте
локально. HTML200946bytes содержит метаданные/фильтры200 оригинальных runtime
кадров. PNG загружаются из публичного raw.githubusercontent.com по неизменному
commit51e46f715b9bface9a7e17111fe709110135940a; для изображений нужен интернет.
External JS, hosting/deploy и новый PR не используются. Для полностью локального
просмотра checkout/ZIP ветки содержит обычный [index.html](index.html) и PNG.

У каждого кадра постоянный ID, screen/state/theme/width, revision реализации,
название/описание, SHA256 и исходный manifest.22 первых/hover кадров явно
исторические; их ограничения сохраняются. Галерея не содержит reference/Figma,
число200 не является числом действий/тестов. Функциональный checkpoint остаётся
115/117 implemented и110/117 locallyverified.

[Проверка HTML](portable-check.json):200cards→auth/light/300 onecard, emptysearch0,
historical22, realPNG300 и noJSerrors. Bytes/hash/dimensions/safe paths всех200
PNG отдельно сверены. Browser smoke выполнялся через временный localhostHTTP:
облачная browser policy запрещаетfile://. NativeChromium не доверяет proxyCA,
поэтому картинки в smoke переданы через Playwright API с наследуемым HTTPSproxy
и доверенным sessionCA; TLSverification включена. Обычный curl того же pinned
PNG дал200,127618bytes,SHA2566068a05d43f229823f39ba6314ac3a5aeabccd255b02e1b161965408456e1b34.
Это не утверждение об unmodified Chromium CA trust. Первые отказы/ошибка cleanup
не скрыты; финальная команда native0. Все собственные browser/server закрыты.
