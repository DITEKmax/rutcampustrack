Следующий один блок: **PWA remote session revoke + D002 warm cached homework**, два исходных теста `mobile-real-server.spec.ts:260` и `:367`, Chromium390×844/1worker/0retry (viewport после project.use spread).

| Критерий | Существующий oracle | Матрица |
|---|---|---|
| Отзыв browser-session из второй своей сессии | DELETE204; reload login/no navigation; прежний bearer401 | CLIENT-FE-AUTH-04, JS-STUDENT-25 |
| Реальная SW/offline read-only граница | SW.ready/controller; warm offline reload после server-revoke; homework сохранено, slider disabled | D002, CLIENT-FE-OFFLINE-01, BOUNDARY-47 |
| Reconnect/пользователь/logout | reconnect login; foreign marker absent; logout+offline no marker/nav | CLIENT-FE-OFFLINE-03/AUTH-04 |

Семь auth-smoke проверяют SW:block и сетевой toggle; этот блок добавляет production SW/warm reload/read-only/cached owner isolation. Не повторяем9smoke, FIRST3 или browser availability. Attendance roster503 не является требованием этих assertions; исходный seed всё ещё требует реальный Academic/Schedule/publication stack.

Минимальная будущая запись — только собственные private config + clock-only wrapper + параметризованный sibling принятого b78/r1 caller. Исходные260/367, dataset/privacy/SafeReporter/product/configs сохраняются. Existing FIRST3 config/observer/count3 непосредственно неприменимы. Source write/review и actual live8 binding/Root browser grant ещё нужны.

Предложено360s active+60cleanup, исходные180s seed/60s per test, Node512MiB managed heap/1.5GiB sampledRSS/32MiB sampledTMP. Same accepted fixed educational browserDate, real timers/serversecurity. Истечение сессии по времени, расписание/Moscow rollover, TMA/geo и полные родительские families остаются UNKNOWN.

**Сейчас READONLY SOURCE PREPARATION / runtime NOT_RUN; исходники не изменены.**
