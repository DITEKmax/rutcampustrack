# ПК-113: статистика группы — evidence пакета

## Scope и критерии

Первый пакет открывает в общей PWA/TMA статистику своей группы для старосты и помощника с текущим `VIEW_STATS`. Включены таблицы «Вся группа» и «По предмету и типу», серверные фильтры/мультисортировка/пагинация, сводка и экспорт полного отфильтрованного набора в DOCX/PDF/PNG ZIP/HTML/XLSX. Query и export используют один расчёт Attendance, текущую группу из свежего server context и повторную проверку прав. Метрики остаются на `ReportService.StatsCounter`; заявки и источники агрегируются bulk. `STARTED` нормализуется в незавершённый `ACTIVE`; отменённые/удалённые и незавершённые занятия не увеличивают знаменатель. «Подано» включает pending/submitted, approved и rejected; draft/cancelled исключены.

График и расширенная детализация вне этого пакета. Runtime/device acceptance и TMA платформенный download adapter остаются pending.

## Baseline и evidence

Канонический MAIN baseline: `e185dc1982eadf00c909283459ce2c2b1e1510b9`. Candidate branch `codex/headman-stats-20260925` основан на `b69c41ae` (включает отдельную применимую правку Admin `9389926aaccb59f340b13260c5044c93fd9d126e`). Product/test scope — только перечисленные ниже пути; чужие изменения `.agent/orchestration-v2/RULES.md` и `LEAF-PACKET.md` оставлены вне candidate.

Focused test `HeadmanStatsServiceTest` проверяет fresh `VIEW_STATS` на read/export включая revoke, канонические числители/знаменатели по датированному roster, filter+sort+paging, unpaged HTML export, `STARTED`/cancelled/deleted exclusion и `submitted >= approved + rejected` на одобренной late-checkin заявке.

## Проверки и runtime

- `npm.cmd run typecheck --workspace @rct/mobile-core` — exit 0.
- `npm.cmd run typecheck --workspace @rct/tma-vue` — exit 0 после исправления вывода union-типа фильтров.
- `npm.cmd run typecheck --workspace @rct/pwa-vue` — exit 0, включая Admin correction из `b69c41ae`.
- Scoped ESLint — exit 0 для stats client/screen и schedule/index/PWA/TMA consumers. Это не полный lint: `AssistantActionsScreen.vue` и `StudentFeatureOwner.vue` исключены из clean-lint набора из-за обнаруженных baseline предупреждений и неиспользуемого импорта вне добавленных строк; обе поверхности прошли vue-tsc.
- Targeted Gradle `:services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.report.HeadmanStatsServiceTest` с `--system-prop=org.gradle.java.compile-classpath-packaging=true --no-problems-report --no-daemon --no-parallel --max-workers=1 --console=plain`: предыдущий focused implementation завершился exit 0 (`.agent/headman-stats-packet-gradle-verified.log`). После последующей правки семантики «подано» один rerun этого же теста ожидает освобождения HEAVY; этот результат не объявляется PASS для текущего content.
- Более ранние Gradle попытки и их причины сохранены в соседних `.agent/headman-stats-packet-gradle*.log`: sandbox shared-JAR lock, затем исправленный compile accessor и fixture filename assertion. Никакие каскадные ошибки shared-JAR не трактовались как продуктовые.
- Runtime evidence: нет подключённого live PWA/TMA/Attendance runtime в этом пакете; остаётся pending.

## Diff inventory

- Frontend: `frontends/mobile-core/src/features/headman-stats/headman-stats-client.ts`, `HeadmanStatsScreen.vue`, `headman-stats-screen.pcss`; `frontends/mobile-core/src/features/headman-group/AssistantActionsScreen.vue`, `features/schedule/HeadmanScheduleScreen.vue`, `src/index.ts`, `src/shared/components/StudentFeatureOwner.vue`; `frontends/pwa-vue/src/App.vue`, `src/auth.ts`; `frontends/tma-vue/src/App.vue`, `src/tma-session.ts`.
- API contract: `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java`; DTO `HeadmanStatsExportRequest.java`, `HeadmanStatsFilter.java`, `HeadmanStatsQueryRequest.java`, `HeadmanStatsResponse.java`, `HeadmanStatsSort.java` в `.../contract/dto/report/`.
- Attendance: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinRepository.java`, `report/ReportController.java`, `report/ReportService.java`; добавлены `report/HeadmanStatsDocxRenderer.java`, `HeadmanStatsExportModel.java`, `HeadmanStatsExportResult.java`, `HeadmanStatsFormat.java`, `HeadmanStatsReportFiles.java`, `HeadmanStatsService.java`, `HeadmanStatsTabularRenderer.java`.
- Focused test: `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanStatsServiceTest.java`.

Пакет не меняет BFF/proto, legacy React или состав/пароли. Не выполнялись push, deploy и production migration.
