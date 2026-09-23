# JS-TEACHER-06 — compact contract

Base: `17467aa67c4d08338b96ae22d2f848a843f3910c`

Worktree: `headman-assistants-delivery-20260922`
Risk: S3 (teacher authorization, historical assignment isolation, server aggregation).

## Goal

Преподаватель выбирает активную сейчас группу, предмет, типы занятий и семестр и
получает серверную сводку посещаемости: по студентам выбранной пары
`group × subject` и по всем разрешённым активным группам. Экран достижим в PWA и TMA через
teacher owner; перезагрузка сохраняет адресуемый контекст.

## Context / evidence

Принятая ПК-123 §4.2–4.6, §5–6 требует канонический порядок `%+`,
`%(+ и у)`, `%у`, `%н`, числитель и знаменатель в каждой метрике, только
прошедшие неотменённые пары, серверные сортировку и фильтры. Решение владельца
`docs/product/decisions/2026-09-23-teacher-replacement-and-group-attendance.md`
заменяет прежнее ограничение «только собственные пары»: активное сейчас
назначение преподавателя в группе даёт read-only доступ ко всей группе,
включая исторические пары до даты назначения и все предметы группы. Активность
проверяется независимо от выбранного семестра отчёта; историческая строка
назначения сама по себе права не даёт.

## Relevant scope

Sole writer этого WT: attendance teacher-read proto и aggregation, TeacherRead
BFF/API, shared mobile-core teacher stats client/screen/PCSS, teacher-only PWA/TMA
App/index wiring и targeted checks. Основные пути: `proto/teacher_reads.proto`,
`services/attendance-service/attendance-app`,
`services/mobile-bff/mobile-bff-api-contract`, `mobile-bff-app`,
`frontends/mobile-core/src/features/teacher`, и адресные teacher-only hunks в
`frontends/pwa-vue`/`frontends/tma-vue`. `academic.proto`, subject/group
mutation и assistant-owned hunks не меняются; при совмещении сохраняются чужие
headman changes.

## Required behavior / API

`GET /api/v1/teacher/stats` принимает `semesterId`, `scope=students|groups`;
для `students` обязательны `groupId` и `subjectId`; допускаются повторяемые
`lessonType`, серверные sort/filter параметры. BFF получает активные сейчас
группы преподавателя, строит полный набор concrete lesson IDs по выбранному
семестру для всех доступных предметов и отбирает группу/предмет/тип. Attendance
RPC получает scope-пакет от подписанного teacher context, заново проверяет
текущую активность актёра в группе и каждый lesson по schedule snapshot.
Ответ дополнительно содержит `subjectOptions` (`groupId`, `subjectId`, имя и
доступные типы), чтобы UI мог выбрать любой предмет активной группы без
фальшивых Assignment-строк.

`/teacher/journal` и `/teacher/lessons/{id}` используют ту же текущую group
read authority и dated historical roster. Личный `/teacher/day` сохраняет
расписание только собственных занятий. Ticket/excuse/attachment read остаётся
на прежнем own-lesson gate; mutation-права не расширяются.

Ответ содержит scope, `lessonsCount`, `serverNow`, `subjectOptions` и строки.
Каждая метрика — `{numerator, denominator, percent}`; строки студентов
содержат только выбранную группу и предмет, строки групп — все занятия
разрешённых активных групп, независимо от исторического владельца пары.
Нельзя выдавать частичный итог при ошибке schedule/roster/attendance authority.

## Constraints / existing patterns

Переиспользуются signed claims, текущий Academic `getActiveSemester` +
`getTeacherSubjects` для свежей active-now group authority, dated
`GroupMembers`, AttendanceReadPort, generation-bound TeacherApi и teacher-screen
PCSS/state revision. Не добавляется новый Academic RPC и не принимаются IDs от
клиента как готовое право. Лимит страниц журнала не переносится в stats.

## Acceptance / verification

Targeted tests должны подтвердить формулу, исключение cancelled/future,
историческую пару другого преподавателя при текущем active group authority,
denial для foreign/no-active group, а также server sort/filter и subjectOptions.
Из экрана статистики группа и выбранный предмет дают открываемый журнал с
конкретным lesson type; возврат сохраняет экран и route context статистики.
После reload положительные group/subject IDs сохраняются до загрузки свежих
`subjectOptions`, а доступ всё равно проверяет сервер. В разрезе групп
показываются все текущие authorized groups, включая группы без `CLOSED` пар;
для них знаменатель метрики равен нулю и экран показывает «нет данных».
Пустой lesson batch также перечитывает текущую authority; пустой студенческий
batch для foreign/no-active group должен получить отказ.
После изменений — affected mobile-core typecheck; Gradle/IT только после HEAVY
lease. Перед интеграцией нужен независимый Sol S3 review.

## Do not

Не объявлять JS-TEACHER-07/export, графики, student detail, cross-group subject
identity и runtime acceptance выполненными наличием таблицы. Экран должен быть
достижим в обеих teacher surfaces; финальный runtime отдельно подтверждает
реальный flow.
