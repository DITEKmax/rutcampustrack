# Baseline B1 — 2026-09-20

Заменяет прежнее «полностью закрытые истории не подсчитаны» в REPORT/numbers; не превращает bounded review в полную аттестацию продукта. Scope: accepted base3d4115f3 и сохранённые runtime evidence, isolated WIP отдельно. Все147 IDs перечислены ровно один раз в story-status.csv;145 предварительно активны,2 исключены исходным решением. Test/runtime не запускались, продукт не менялся.

| Роль | Активно | Accepted | Partial | Unknown | Подтверждённый минимум |
|---|---:|---:|---:|---:|---:|
| Студент |36|2|21|13|5.56%|
| Староста |52|0|6|46|0%|
| Преподаватель |15|0|2|13|0%|
| Администратор |26|0|18|8|0%|
| Системные |16|0|14|2|0%|
| Всего |145|2|61|82|1.38%|

2/145=1.38% — подтверждённый минимум полного выполнения историй в обозначенном scope.61/145=42.07% имеют частичную реализацию/evidence;82/145=56.55% не оценены достаточно. Неизвестное не равно отсутствующему коду. Частичным не присваиваются дробные баллы. Это НЕ утверждение, что фактический продукт готов лишь на1.38%, и не общий production процент.82unknown и незафиксированный operations checklist не позволяют такой вывод.

Accepted IDs: JS-STUDENT-01 (геоотметка с серверной eligibility) и JS-STUDENT-12 (недоступность отметки заблокированной пары), только принятый PWA scope generic mobile-or-web истории. Evidence: .agent/vertical-js-student-01/be-summary.md, integration-summary.md, runtime-evidence/summary.md; поздний review и main merge8002b9, ancestry8002b9→3d. Не приписывается acceptance web-panel/genuine Telegram. В отдельной screen/client матрице эти поверхности остаются открытыми.

Root correction: scout предлагал accepted JS-STUDENT-WEB-09 поlive14. Live14 доказывает PWA, а explicit WEB история относится к веб-панели; поэтому partial. Для HEADMAN08 не добавлены чужие требования переноса/восстановления: достаточно собственнойUI/input/generation цепочки; она ещё не integrated accepted.

Root admin/system assessment: code SemesterService/UI существуют, но full target acceptance не установлена. UserService.generateLogin в target3d иhistoricalWIP всё ещё role-prefixed student/teacher; ADMIN01 требует role-independent login. Это конкретное source расхождение, не обнаруженный runtime incident; исправления не выполнялись. Другие partial/unknown причины записаны поID, не считаются отсутствием функций.

## После каждой итерации

Обновлять story-status.csv только по новым changes/evidence/owner decisions. Публиковать accepted X/145, partial, unknown, изменения относительноB1 и закрытые IDs. Изменение знаменателя только с явным решением/дедупликацией, не ради процента. Уже принятое не проверять заново без воздействия изменения. Полные проценты экранов/сервисов публиковать после отдельного согласованного inventory и evidence, не выводить их из Story count. Общий production показатель остаётся unknown до определения release/operations checklist; его обязательные блокеры показывать всегда.

## Файлы этой оценки

Созданы READINESS-ASSESSMENT-PACKET.md; readiness-20260920/{admin-system-assessment.json,staff-assessment.json,student-assessment.json,story-status.csv,role-counts.json,BASELINE.md}. Обновлены numbers.json, docs/product/READINESS.md (обязательный формат следующих итогов), SLOTS.md. Все read-only scouts завершены. Разрешение на оценку не снимает product STOP.
