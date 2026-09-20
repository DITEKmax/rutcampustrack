# Отмена и восстановление пары — исходные факты для следующего контракта

07.09.2026. Scope относится к завершению роли студента, но не входит в текущие Homework generated-contract и requests domain repair. Код root не менял.

Принятый Р-7 (backend-conflicts.md:161–166): возможна отмена задним числом; отметки недействительны, не удаляются; пара видна как отменённая с причиной, исключена из знаменателя; связанное ДЗ архивируется; восстановление даёт пустую сетку.

Root открыл оригиналы:
- Schedule LessonService.cancelLesson:111–135 сохраняет CANCELLED и audit tuple, публикует LessonCancelledEvent с lesson/group/subject/date/number/times/reason/actor/time. Текущий event не содержит semester_id.
- LessonService.restoreLesson:144–157 возвращает PLANNED и очищает cancellation tuple, но не публикует событие восстановления. Следующий scheduler может сразу перевести прошедшую пару в ACTIVE/CLOSED.
- Attendance LessonEventService.processLessonCancelled переводит существующие записи в CANCELLED. OneOff cancellation и physically deleted lessons удаляют attendance records. Эти старые пути не являются доказательством соответствия Р-7.
- AttendanceDocument имеет одну unique запись lesson/user, status/source, но нет отдельной invalidation history/generation. Будущий контракт должен определить пустую сетку после restore без удаления истории и без восстановления старых отметок.
- Academic Homework привязан tuple group/subject/semester/date/lessonNumber; lessonId/archived flag отсутствуют. Academic consumer для lesson.cancelled/restored поиском не найден. Архивирование ДЗ пока не реализовано.

Требуется bounded Sol xhigh source/architecture decision перед writer: минимальная модель invalidation/restore, защита от повторных и переставленных lifecycle events, согласованность с pair locks и requests approval, канонический Homework archive/filter. Не воскресить CANCELLED запись из EXCUSE approval. Не менять текущих writers и не выполнять production migration/очистку. Ремонт текущей FREE_ATTENDANCE priority отдельно сохраняет CANCELLED до этого контракта.
