# PROGRESS — Vue web redesign

Срез2026-10-24 Europe/Moscow. Существующий draft PR3:
https://github.com/DITEKmax/rutcampustrack/pull/3.
Владелец отдельно запустил Goal после readiness; полномочия:
[GOAL-CONTRACT.md](GOAL-CONTRACT.md). Среда/ZIP:
[READINESS-CHECK-20261024.md](READINESS-CHECK-20261024.md).

## Функциональный прогресс этой Vue итерации

**Implemented0/117 (0%). Locally verified0/117 (0%).**
Знаменатель provisional117 active, не уменьшен; reference/theme/width варианты
не являются отдельными действиями/тестами. Старый mobile код и настройка среды
не засчитываются новой web реализацией.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH | 6 | 0/6 (0%) | 0/6 (0%) |
| SHARED | 15 | 0/15 (0%) | 0/15 (0%) |
| STUDENT | 16 | 0/16 (0%) | 0/16 (0%) |
| HEADMAN | 38 | 0/38 (0%) | 0/38 (0%) |
| TEACHER | 8 | 0/8 (0%) | 0/8 (0%) |
| ADMIN | 34 | 0/34 (0%) | 0/34 (0%) |

## Текущий пакет

В работе password/session/role context/shared shell + student homework
read/completion. Параллельно общая Onest/semantic/rem PCSS foundation, light/
dark/system всех PWA/TMA экранов и backend device preference mirror с настоящей
PostgreSQL HTTP проверкой. Единственные writers — отдельные worktrees в SLOTS.
Корневые package/lock/config — web owner, tokens/mobile — theme owner,
auth/generated/backend contracts — backend_device, common docs — root.

После завершения первого сквозного пути — независимое review/исправления и
runtime обеих тем/адаптива, затем расширение ролей. Owner screen approval не
требуется. Angular сохраняется до полной функциональной замены.

## Проверено / ограничения

Figma actual context+screenshot, GitHub MCP+gh/API+Git fetch, shell/JDK/Gradle/
Docker/Chromium и проверенный ZIP доступны. Q-ENV-001/Q-INPUT-001 закрыты.
Core login/authoritative role/BFF session/group roster/logout прошли readiness.
Полный «Сегодня» НЕ подтверждён: минимальный стенд давал schedule503 и
notification unread500. Stand owner добавляет настоящие нужные сервисы.

ACT-ADMIN-GROUP-FINAL-DELETE и ACT-ADMIN-REGENERATE-PASSWORD остаются принятыми
API implementation gaps. Не исключать их из117. Candidate дополнительной
разовой правки пары не разрешает новые поля без существенного решения.
Три Q-PROD решения сохранены; новых продуктовых вопросов пока нет.

Runtime HTML-галерея новой web реализации ещё не создана; reference PNG не
выдаётся за runtime. Actual локальные проверки первого пакета ещё ожидаются.
CI/Coverage/Security/OpenAPI **PAUSED**, только workflow_dispatch, не PASS.
Нет main writes/merge/deploy/production. После пакетов обновлять карту/PR/evidence.
