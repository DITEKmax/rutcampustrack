# Correction packet после REVIEW-7f85860ef и узкий SIGTERM recheck

Первый независимый review: **CHANGES REQUIRED, 1P1 + 2P2**. Исходный commit
`7f85860ef` и его validation failures сохранены; review не переименован в PASS.
Исправления остаются source PREP. Cloud group execution отменён, owner-local
group gate **NOT_RUN** до фактического output владельца и его проверки.

| Finding | Исправление | Достаточный targeted control |
| --- | --- | --- |
| P1: `private` уже существует | `prepared-private.cjs` допускает только существующий exact nonsymlink directory mode0700 с UID текущего пользователя; `native-cohort.cjs` использует этот admission. Состояние не удаляется/не создаётся заново. Original intent по-прежнему создаётся exclusive/fsynced. | Filesystem-only: prepared dir проходит, второй original intent получает EEXIST и не заменяет первый; symlink и ослабленные permissions отклоняются. |
| P2: startup/mount inputs не frozen | `runtime.source()` сначала проверяет generated package и private input-binding manifest/hash. Initial `local.env`, config/password, PKI/keys, directed tokens, foreign baseline связаны с exact source/nonce без вывода значений. `mount_pins()` фиксирует каждый bind source/destination/RW, named volume и image tag→ID; `ownership()` проверяет actual container Image/полный mount set/nonce/exact service name. `ready()` требует все12 прежних healthy IDs и isolation receipt перед API. Start/frontends входят через этот source admission. | Изменённый private env отклоняется; changed package блокирует start/frontends до действий; fake own Image/mount drift и чужой nonce/volume/network отклоняются. |
| P2: detached readiness failure оставляет сервисы | Startup `try` охватывает compose, guarded detached readiness, narrow isolation и final ready. Timeout, early reserve, exited service, exception и Ctrl-C проходят `close_startup()`; перед stop сохраняется exact-ID intent, затем closure. Unknown metadata даёт HOLD без stop. `stop_owned()` допускает partial known startup без ready/БД; original/fixture unknown сохраняет HOLD. Volumes/data не удаляются. | Fake child interrupt/disk/timeout; fake detached reserve/timeout/interrupt вызывают только exact known ID stop; unknown metadata не вызывает stop; partial startup stop работает без ready; unknown case stop запрещён. |

Дополнительно в том же scope: non-TTY getpass отклоняется; existing180f Schedule/
Mongo isolation probes портированы как finite local prerequisite с rollback/abort,
без cloud run. DB password для этих probes передаётся в stdin собственного
контейнера, не Docker CLI argument. Live teaching clock и новый own semester
сохраняются; OS/Auth/cloud clock не меняются.

Original UUID, one confirm/status-only, numeric preview/current password, passive
six-view hint/request-start/focus/draft assertions, Auth durable handles, отдельно
квалифицируемый TMA successor, SQL/Mongo preservation и exact own browser SID cleanup
не ослаблены. В 595-line cohort изменена только prepared-private admission строка;
его прежние runtime/backend outcomes не объявлены новым local PASS.

Targeted harness первого correction: Python syntax6, Node syntax7, prepared-directory4,
ownership11, private-input tamper1, startup-freeze2, child process guards3,
detached/partial/HOLD lifecycle6. Настоящие Docker/Auth/API/DB/browser/process signals на этом этапе
и backend tests/builds —0. Narrow isolation **исходники подготовлены**, фактический
local isolation/result ещё NOT_RUN. Новый независимый source recheck обязателен
для закрытия findings; его результат Root фиксирует отдельно.

## Остаточный P2 из RECHECK-4b9c0867

Recheck закрыл private/input/image/mount boundaries и прежние reserve/timeout/
Ctrl-C paths, но оставил **CHANGES REQUIRED**: default parent SIGTERM обходил
Python cleanup, generic resources exception не reap отдельный child group.
Эти результаты review сохранены, source PASS самостоятельно не объявляется.

Узкое исправление в `local.py`: `install_handlers()` вызывается первым в main,
SIGTERM/SIGINT входят в прежний interruption/UNKNOWN path. Во время Popen→registry
handover сигнал откладывается до регистрации точного дочернего process object.
`guarded()` всегда проходит finally→bounded TERM/reap (KILL только своего group
после таймаута), сохраняет PID/exit/reaped/error receipt и лишь затем передаёт
ошибку в существующий metadata-qualified startup closure. На reap/fsync boundary
orderly signal откладывается до сохранения receipt. Frontends используют тот же
ограниченный registry своих трёх children. Новых realm/DB/Auth cleanup/retry
действий и изменения original UUID/passive six-view протокола нет.

Новая гипотеза проверки отличается от прежнего fake KeyboardInterrupt:
`check_signal_guard.py` запускает настоящие короткоживущие Python worker/child
в свежем `/tmp` control directory, с **fake Docker и fake ports**. Проверяются
фактический SIGTERM после регистрации, SIGTERM внутри Popen return window и
generic resources-poll exception при живом child. Каждый child реально получает
TERM и reap; qualified startup stop вызывается только для synthetic exact ID,
с durable intent/closure, до services-ready. За успешный run —5 SIGTERM только
этим новым synthetic process groups, signals настоящим сервисам —0.

Первый новый control обнаружил TEST fixture gap: `ports_free` не был замокан,
sandbox отклонил socket.bind с PermissionError до создания child. Отдельный
diagnostic worker подтвердил причину; Docker/Auth/DB/start не выполнялись.
После исправления fake три controls PASS. После усиления reap/receipt boundary
повторены только они; затем один run изменённого публичного `check_handoff.py`
подтвердил интеграцию controls. Старые P1/private freeze review и backend результаты
переиспользованы; owner-local runtime остаётся NOT_RUN. Новый narrow source
recheck Root/reviewer требуется для закрытия последнего P2.
