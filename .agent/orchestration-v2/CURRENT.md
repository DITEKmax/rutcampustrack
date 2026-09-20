# CURRENT — product STOP, process update 2026-09-20

Владелец разрешил пересмотр инструкций. Это не GO на продукт. Единственная точка общения — главный чат оркестратора. Новая policy: RULES.md; решение docs/product/decisions/2026-09-20-delivery-policy.md. Исторические записи не являются разрешением продолжать работу.

## Сохранённый продукт
- Все4 направления и product leaves STOP_ACK, процессы завершены; heavy FREE. Checkpoint: checkpoints/2026-09-20-retrospective-stop/REPORT.md.
- Принятая база3d4115f3 (service identity), parent13e5fd; main/E не продвинуты.
- v2-l5b-recurring-writer:37 frozen source files, H102 PASS. H104 JSON/PWA/web types partial; mini-app/sibling restore/final TS остаются. H105 NEVER SPAWNED/NOTRUN.
- v2-l5b-historical-attendance:35 frozen source files. H103 exit1/tests NOTRUN: HistoricalMembershipIT:179,182 AtomicBooleanAssert.hasValue(boolean). Исправление не выполнено.
- v2-l5b-retrospective-union чистый3d, feature import/review/combined runtime NOTRUN.
- v2-requests-harness: RetrospectiveOnly WIP, source-check predates last chooseCurrentSemester edit; runtime NOTRUN. Suspected role-grant mismatch требует адресной проверки, не SQL обхода.

## После явного product GO
Прочитать checkpoint и текущие RULES. Старые packets остаются картой scope/команд, но новый dispatch получает текущий RULES hash и краткое дополнение новой policy. Не переписывать исторические hashes/evidence.
Закончить два независимых хвоста сохранённых веток; использовать известные commands из packets/H103 plan. Проверить легитимную выдачу ролей рано, закончить harness минимально, интегрировать → risk review → реальная цепочка создания/Н/отмены/статистики. Не повторять принятые неизменённые проверки. Остальные функции из backlog не запускать ради занятости.

## Текущая разрешённая работа
Delivery-policy docs завершены; source lookup completed/released. Product files/tests не изменены; все slots FREE. Текущие slots: SLOTS.md. Полная предыдущая хронология: archive/2026-09-20-before-delivery-policy/.agent/orchestration-v2/CURRENT.md.inactive. Readiness: docs/product/READINESS.md; прежние приблизительные проценты не измеренная база.


Readiness B1: readiness-20260920/BASELINE.md и story-status.csv.2 accepted(PWA scope),61partial,82unknown из145active.1.38% — подтверждённый минимум, НЕ общий production процент. После каждой итерации обновлять эту матрицу и delta; productSTOP сохраняется.
