# Единый HTML просмотра

Скачайте [portable.html](portable.html) через Download raw file в GitHub и откройте
локально. Он содержит поиск и метаданные204 runtime-кадров; оригинальные PNG
загружаются из публичного GitHub по immutable commit
`08a3d1aa45b23967168589afa1c2d7ff083e1de9`, нужен интернет. Внешних скриптов и
hosting/deploy нет. Checkout содержит также обычный [index.html](index.html),
manifests и исходные PNG для полностью локального просмотра.

У каждого кадра постоянный ID, экран, состояние, тема, ширина, revision, название,
описание и SHA256. Первые22 исторических кадра помечены; reference/Figma отсутствуют.
Кадры не равны тестам: реализовано116/117, локально подтверждено113/117 действий.
PROMOTE снят на sourcee8db до последующей архивации собственной пустой группы.

[Проверка данных](portable-check.json) сверяет204 уникальных ID, безопасные пути
и хеши всех PNG. Оба новых ADMIN PNG действительно скачаны с закреплённого commit через inherited
HTTPS proxy с проверкой TLS:HTTP200 и совпадающие bytes/SHA256. Exact hashes
в portable-check.json. PROMOTE также сохранён с прежней собственной revision.
Код отрисовки и фильтров побайтно совпадает с прежним HTML; после изменения
данных/pin/count браузерная проверка не повторялась. [Прежний202 data-only receipt](portable-check-202-ac5c37ab2.json) сохранён.

[Прежний browser smoke](portable-check-200-51e46f715.json) сохраняет pin51e46f715
и хеш HTML с200 кадрами. Проверены фильтры, пустая выдача,22 исторических кадра,
реальный PNG и отсутствие JS-ошибок. Он использовал localhost HTTP: политика
браузера запрещает file://. Изображения передавались через Playwright API с CA
среды и включённой проверкой TLS; прямое доверие Chromium к proxy CA не подтверждено.
Первые ошибки транспорта и cleanup сохранены; финальная команда exit0, собственные
браузер и сервер закрыты. Новая204 browser-кампания не запускалась.

Новые source0421 кадры: fresh ADMIN home и ACTIVATE. Dashboard unavailable
metrics, общий health и downstream delivery этим пакетом не объявлены PASS.
