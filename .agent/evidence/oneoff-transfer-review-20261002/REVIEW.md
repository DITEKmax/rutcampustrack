# Independent source reviews — 2026-10-02

## Schedule producer/core
Reviewer: review_academic_homework_1002. Stable diff60c0ff8b..5ed698ea. Report:

PASS — независимое source review `60c0ff8b..5ed698ea`. Подтверждённых P1/P2 нет.

Проверены точная ONE_OFF identity без template, сохранение старой physical-пары и origin tuple, текущие pointers/projection, V26 вместе с критичными исходными guards V17–V25, права, порядок блокировок, атомарность history/batches/replay и выбор v2 при публикации и восстановлении из сохранённого payload. Recurring v1 сохраняет прежний snapshot.

Ограничения: compile и runtime не запускались. Реальное применение ДЗ/посещаемости consumers и завершение через их ACK требуют комбинированной приёмки; тестовые ACK этого не подтверждают.

## Academic/Attendance consumers
Reviewer: review_homework_schedule_code_1002. Frozen source.diff subsequently committed26bdcd84; root confirmed exact normalized diff correspondence: 38,704 characters, reviewed source.diff equals git diff26bdcd84^..26bdcd84 -- services. Report:

PASS source — frozen `source.diff` SHA256 `BEB4885A…`, producer `5ed698ea`. Существенных P1/P2 не найдено.

Проверены v2 ONE_OFF origin/scope/physical IDs/generation, legacy Attendance receipt без версии → v1, неизменный Academic v1 hash, v2 hash immutable header/snapshots, точный replay и существующие транзакционные fences. Перенос использует прежние business paths, сохраняющие identity, completion и историю.

Предел заключения: Academic защищает origin/version на уровне сообщения и duplicate batch; operation-wide cross-batch origin proof не заявляется. Compile и Mongo/PG runtime pending; добавленные сценарии прочитаны, но не запущены.

## Integration boundary
Root observed combined eabb5a9c in admin-group-promotion-20260927. These are source review results, not runtime acceptance or main integration. Single heavy lease active: exec22375, combined eabb5a9c, gradle-combined-r1.log in writer evidence; no production operations.
