# Приём Figma-материалов для design packet

Этот writer не вызывает Figma. Единственный reader передаёт для каждого чтения
компактное summary и один из следующих носителей:

1. локальный путь к сохранённому tool-return raw context/screenshot/asset bytes; или
2. ссылку на private note/history с полным raw text либо image content;
3. asset URL только как источник для последующей единственной загрузки writer'ом.

Для каждой записи нужны: canonical node link, node ID, название и назначение, тип
запроса, `capturedAt`, известная revision либо `unknown`, state/theme/width,
полнота ответа, исходный MIME type и предупреждение об усечении. Не передаются
токены доступа, cookies, initData, пользовательские данные или полный transcript.

Writer переносит raw text без перефразирования в `context/`, image content в
`screenshots/`, а экспортируемые bytes в `assets/`. Для bytes фиксируются SHA-256,
MIME и размер. При URL writer сохраняет bytes до записи URL в manifest; URL не
является единственным production-источником. Нельзя повторно запрашивать уже
полученный screenshot. Неполный ответ, отсутствующий state или node link остаётся
в `gaps/`, а не заполняется по памяти.

Чтения идут только из root queue: общий предел до 4 стартов в минуту и минимум
15 секунд между стартами. При `429` сохраняется ответ и `Retry-After`; без него
используется bounded backoff. Дневной лимит останавливает очередь в checkpoint.
