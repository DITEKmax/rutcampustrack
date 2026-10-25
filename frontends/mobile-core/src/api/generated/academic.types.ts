// AUTO-GENERATED — do not edit by hand.
// Regenerate: npm run generate:types (requires backend running).
// Source: docs/openapi/academic.json (committed for CI drift-guard).

export interface paths {
    "/academic/users/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить пользователя по ID */
        get: operations["getUser"];
        /** Полное обновление пользователя (PUT). Только ADMIN. */
        put: operations["updateUser"];
        post?: never;
        /** Устаревший маршрут: используйте защищённое архивирование */
        delete: operations["archiveUser"];
        options?: never;
        head?: never;
        /**
         * Частичное обновление пользователя (PATCH)
         * @description Обновляет только переданные поля. Используется для смены головы, перевода, статуса.
         */
        patch: operations["patchUser"];
        trace?: never;
    };
    "/academic/users/{id}/roles/{role}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Добавить или обновить роль пользователя
         * @description Атомарно изменяет один durable role grant. Только ADMIN.
         */
        put: operations["updateRoleGrant"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/{id}/admin-access": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get"];
        /** Изменить полномочия администратора */
        put: operations["put"];
        post?: never;
        /** Отозвать полномочия администратора */
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/thresholds/subject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Установить порог для предмета в группе (HEADMAN/ADMIN)
         * @description Наиболее специфичный порог — переопределяет группу и глобальный.
         */
        put: operations["setSubjectThreshold"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/thresholds/group": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Установить порог для группы (HEADMAN/ADMIN)
         * @description Переопределяет глобальный порог для указанной группы.
         */
        put: operations["setGroupThreshold"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/thresholds/global": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Установить глобальный порог посещаемости (ADMIN)
         * @description Применяется ко всем группам и предметам, если не переопределён на уровне группы или предмета.
         */
        put: operations["setGlobalThreshold"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/subjects/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить предмет по ID */
        get: operations["getSubject"];
        /** Полное обновление предмета (PUT, HEADMAN) */
        put: operations["updateSubject"];
        post?: never;
        /**
         * Удалить предмет (HEADMAN)
         * @description Удаление отклоняется при наличии назначений или ссылок расписания; force не обходит историю.
         */
        delete: operations["deleteSubject"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semesters/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить семестр по ID */
        get: operations["getSemester"];
        /** Полное обновление семестра (PUT, ADMIN) */
        put: operations["updateSemester"];
        post?: never;
        /**
         * Окончательно удалить семестр (ADMIN)
         * @description Требует свежий preview, пароль ADMIN и ключ идемпотентности.
         */
        delete: operations["deleteSemester"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/floors/{floorId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Изменить код и название этажа в его корпусе */
        put: operations["updateFloor"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/buildings/{buildingId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Изменить код и название корпуса */
        put: operations["updateBuilding"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/homeworks/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить задание по ID */
        get: operations["getHomework"];
        /** Обновить ДЗ своей группы (староста или помощник с manage_homework) */
        put: operations["updateHomework"];
        post?: never;
        /** Архивировать задание (HEADMAN или помощник с manage_homework) */
        delete: operations["deleteHomework"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить группу по ID */
        get: operations["getGroup"];
        /** Полное обновление группы (PUT, ADMIN) */
        put: operations["updateGroup"];
        post?: never;
        /** Удалить группу (ADMIN) */
        delete: operations["deleteGroup"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/{id}/headman": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Назначить старосту (ADMIN) */
        put: operations["assignHeadman"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Список пользователей
         * @description Постраничный список с опциональным поиском и фильтрами. Только ADMIN. Пример: /academic/users?search=иван&role=STUDENT&page=0&size=20
         */
        get: operations["listUsers"];
        put?: never;
        /**
         * Создать пользователя
         * @description Создаёт нового пользователя с автоматической генерацией логина и начального пароля. Только ADMIN.
         */
        post: operations["createUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/{id}/transfer": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Перевести студента в другую группу */
        post: operations["transferStudent"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/{id}/restore": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Восстановить аккаунт без активных полномочий; роли назначаются заново */
        post: operations["restoreUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/{id}/archive": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Архивировать пользователя по подтверждённому предпросмотру */
        post: operations["archiveUserProtected"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/subjects": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список предметов (HEADMAN — только своя группа, ADMIN — все) */
        get: operations["listSubjects"];
        put?: never;
        /** Создать предмет (HEADMAN) */
        post: operations["createSubject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/subjects/{id}/teachers/{teacherId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Добавить преподавателя к предмету (HEADMAN) */
        post: operations["addTeacher"];
        /**
         * Закрыть назначение преподавателя (HEADMAN)
         * @description Сохраняет историю и будущие пары без преподавателя с указанной даты; нужен UUID Idempotency-Key.
         */
        delete: operations["removeTeacher"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/subjects/{id}/deletion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirm"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/subjects/{id}/deletion-challenge": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["challenge"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semesters": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список семестров */
        get: operations["listSemesters"];
        put?: never;
        /** Создать семестр (ADMIN) */
        post: operations["createSemester"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semesters/{id}/restore": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Начать или продолжить восстановление семестра (ADMIN)
         * @description Восстановление открывает разрешённые записи и не активирует семестр.
         */
        post: operations["restoreSemester"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semesters/{id}/archive": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Начать или продолжить архивацию семестра (ADMIN)
         * @description UUID в Idempotency-Key закрепляет actor, semester и action. Повтор возвращает прежнюю операцию.
         */
        post: operations["archiveSemester"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/report-download-tickets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["legacy"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/registry-exports/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["users"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/registry-exports/groups": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["groups"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/plans": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Создать или заменить схему по кодам корпуса и этажа одной операцией
         * @description Коды: ASCII [1-9][0-9]*, максимум 64 символа, без пробелов. Название схемы: максимум 255 символов. Нужен PNG или SVG. Не переданный формат сохраняется из текущей версии.
         */
        post: operations["uploadPlan"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/plans/target/deletion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["deletePlan"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/floors": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список этажей корпуса */
        get: operations["listFloors"];
        put?: never;
        /** Добавить этаж */
        post: operations["createFloor"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/floors/{floorId}/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Загрузить новую версию схемы этажа */
        post: operations["uploadVersion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/floors/{floorId}/deletion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Окончательно удалить этаж и все версии его схемы после подтверждения паролем */
        post: operations["deleteFloor"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/buildings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список корпусов и этажей */
        get: operations["listBuildings"];
        put?: never;
        /** Добавить корпус */
        post: operations["createBuilding"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/buildings/{buildingId}/deletion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Окончательно удалить пустой корпус после подтверждения паролем */
        post: operations["deleteBuilding"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/identity-operations/{operationId}/reconcile": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Восстановить неизменную failed-safe операцию пользователя или группы */
        post: operations["reconcileGroupOperation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/homeworks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список заданий для группы и семестра */
        get: operations["listHomeworks"];
        put?: never;
        /** Создать домашнее задание (HEADMAN или помощник с manage_homework) */
        post: operations["createHomework"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/homeworks/{id}/complete": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Отметить задание выполненным (STUDENT)
         * @description Создаёт запись HomeworkCompletion для текущего студента.
         */
        post: operations["markComplete"];
        /** Снять отметку выполнения (STUDENT) */
        delete: operations["unmarkComplete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список групп: фильтр по статусу + поиск (ADMIN/TEACHER) */
        get: operations["listGroups"];
        put?: never;
        /** Создать группу (ADMIN) */
        post: operations["createGroup"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/{id}/restore": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Восстановить группу из архива (ADMIN) */
        post: operations["restoreGroup"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/{id}/deletions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirmGroupDeletion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/{id}/deletions/{operationId}/resolve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Разрешить исход удаления по исходному UUID
         * @description Возвращает исходную принятую операцию. Если приём ещё не состоялся, атомарно сохраняет CANCELLED/NOT_ACCEPTED и исключает поздний приём этого UUID. Не отменяет уже принятую операцию. Пароль и повторное подтверждение удаления не передаются.
         */
        post: operations["resolveGroupDeletion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/registry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Реестр групп ADMIN */
        get: operations["listAdminGroups"];
        put?: never;
        /** Создать группу в реестре ADMIN */
        post: operations["createAdminGroup"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/promote": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Выполнить промоушен групп (ADMIN). Запускать после preview. */
        post: operations["promote"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/promote/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Preview промоушена групп (dry-run, ADMIN) */
        post: operations["promotePreview"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/export-jobs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/export-jobs/{id}/ticket": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["ticket"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assistants": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список помощников старосты для группы */
        get: operations["listAssistants"];
        put?: never;
        /** Назначить помощника старосты (HEADMAN/ADMIN) */
        post: operations["assignAssistant"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assignments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список назначений для группы и семестра */
        get: operations["listAssignments"];
        put?: never;
        /** Назначить преподавателя на предмет/группу/семестр (HEADMAN) */
        post: operations["assignTeacher"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assignments/{id}/resume-adoption": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Продолжить принятое назначение (HEADMAN) */
        post: operations["resumeAdoption"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assignments/{id}/replace": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Заменить преподавателя с даты (HEADMAN) */
        post: operations["replaceAssignment"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/admin/attendance/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["generate"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/me/avatar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Сменить аватар текущего пользователя (BUG-004)
         * @description Сохраняет идентификатор пресета. Передайте пустой avatarId чтобы сбросить.
         */
        patch: operations["updateMyAvatar"];
        trace?: never;
    };
    "/academic/semesters/{id}/activate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Активировать семестр или запланировать его активацию (ADMIN)
         * @description В московском диапазоне дат активирует указанный семестр. До начала сохраняет activationScheduled=true, оставляя текущий семестр активным.
         */
        patch: operations["activateSemester"];
        trace?: never;
    };
    "/academic/assistants/{id}/permissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Обновить права помощника (HEADMAN/ADMIN)
         * @description expectedPermissions задаёт условное сравнение набора прав под блокировкой. Пустые permissions со снимком отзывают помощника и возвращают active=false; без снимка пустой набор запрещён.
         */
        patch: operations["updatePermissions"];
        trace?: never;
    };
    "/academic/users/{id}/archive-preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Предпросмотр архивирования; количества наблюдаются отдельно по сервисам */
        get: operations["previewUserArchive"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/teachers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Список преподавателей (без постраничной разбивки)
         * @description Возвращает всех активных преподавателей. Доступно студентам (для выбора преподавателя предмета).
         */
        get: operations["listTeachers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/teachers/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Поиск преподавателей для предмета
         * @description Постраничный поиск по фрагменту ФИО или табельного номера. Возвращает только пользователей с действующим durable TEACHER grant; доступно студентам.
         */
        get: operations["searchTeachers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить профиль текущего пользователя (из X-User-Id header) */
        get: operations["getMe"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/users/by-ids": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Batch-резолв display-имён пользователей по ID
         * @description Возвращает только {id, fullName} для каждого найденного пользователя. Доступно STUDENT/TEACHER/ADMIN — намеренно не отдаёт PII (login/email/telegramId/role). Используется для подстановки имён в audit-местах (cancelledBy, и т.п.). Cap: max 100 ids на один запрос. Несуществующие ID молча пропускаются.
         */
        get: operations["getUsersByIds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/thresholds": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список всех настроенных порогов */
        get: operations["listThresholds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/thresholds/resolve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Разрешить эффективный порог
         * @description Возвращает применяемый порог для комбинации группа/предмет согласно иерархии.
         */
        get: operations["resolveThreshold"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/subjects/{id}/deletion-preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["preview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/subject-deletion-operations/{operationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["status"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semesters/{id}/delete-preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Предварительный расчёт последствий окончательного удаления семестра (ADMIN) */
        get: operations["previewSemesterDeletion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semesters/{id}/archive/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Текущее состояние архивации семестра (ADMIN) */
        get: operations["getSemesterArchiveStatus"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semesters/overlap": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Проверка пересечения дат семестра (ADMIN)
         * @description Используется frontend async-валидатором semester-dialog для предварительной проверки дат перед сабмитом.
         */
        get: operations["checkOverlap"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semester-deletions/{operationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить сохранённый результат удаления по operationId (ADMIN) */
        get: operations["getSemesterDeletionOperation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/semester-archive-operations/{operationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Прочитать сохранённую операцию архивации (ADMIN) */
        get: operations["getSemesterArchiveOperation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/report-download/{ticket}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["ticketFile"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/registry-exports/users/formats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["userFormats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/registry-exports/groups/formats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["groupFormats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/plans/target": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Прочитать текущую схему по номерам корпуса и этажа без создания каталога */
        get: operations["getPlanTarget"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/plans/target/deletion-preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["previewPlanDeletion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/floors/{floorId}/versions/{version}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Прочитать версию схемы */
        get: operations["getVersion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/floors/{floorId}/versions/{version}/assets/{format}/{assetId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Скачать администраторскую копию ассета */
        get: operations["downloadAsset"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/floors/{floorId}/deletion-preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["previewFloorDeletion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/map/buildings/{buildingId}/deletion-preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["previewBuildingDeletion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/identity-operations/{operationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Проверить состояние сохранённой операции изменения пользователя или группы */
        get: operations["getIdentityOperation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/identity-operations/{operationId}/reconcile-preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить новое подтверждение для восстановления failed-safe архивирования пользователя */
        get: operations["getArchiveReconciliationPreview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/homeworks/{id}/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** История изменений ДЗ, включая архивные задания */
        get: operations["history"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/{id}/headman/roster": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Состав группы для назначения старосты (ADMIN) */
        get: operations["getHeadmanRoster"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/{id}/headman/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Предпросмотр назначения старосты (ADMIN) */
        get: operations["previewHeadman"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/{id}/deletion-preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["previewGroupDeletion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/options": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["options"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/groups/my/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Участники группы текущего пользователя (STUDENT/HEADMAN) */
        get: operations["getMyGroupMembers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/group-deletions/{operationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getGroupDeletionStatus"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/export-jobs/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["status_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/export-jobs/{id}/file": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["file"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/dashboard/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Сводная статистика системы (ADMIN)
         * @description Возвращает число пользователей с активной ролью, число действующих групп и название активного семестра.
         */
        get: operations["getStats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/dashboard/activity": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Реальная активность системы (ADMIN)
         * @description Требуется DASHBOARD_VIEW. Московские сутки/недели; история ограничена сохранёнными действиями. Недоступные зависимости и неполная история обозначаются явно.
         */
        get: operations["getActivity"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assistants/permissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Каталог прав помощника */
        get: operations["listPermissionCatalog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assistants/me/permissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Текущие права помощника для signed пользователя */
        get: operations["listMyPermissionCatalog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assignments/replacements/{operationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Статус замены преподавателя */
        get: operations["getReplacementStatus"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assignments/my": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Мои назначения (TEACHER)
         * @description Возвращает все предметы и группы текущего преподавателя для активного семестра.
         */
        get: operations["getMyAssignments"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/admin/attendance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["read"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/admin/attendance/semesters": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["semesters"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/admin/attendance/formats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["formats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/admin-access/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/admin-access/catalog": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["catalog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assistants/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Отозвать помощника (HEADMAN/ADMIN) */
        delete: operations["revokeAssistant"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/academic/assignments/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Закрыть назначение (HEADMAN)
         * @description Сохраняет историю и будущие пары без преподавателя с указанной даты; повторяется с тем же UUID Idempotency-Key.
         */
        delete: operations["removeAssignment"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Полное обновление пользователя (PUT, ADMIN-only) */
        UpdateUserRequest: {
            /**
             * @description Фамилия
             * @example Иванов
             */
            lastName: string;
            /**
             * @description Имя
             * @example Иван
             */
            firstName: string;
            /**
             * @description Отчество (опционально)
             * @example Иванович
             */
            middleName?: string;
            /**
             * @description Роль пользователя
             * @example STUDENT
             * @enum {string}
             */
            role: "ADMIN" | "TEACHER" | "STUDENT";
            /**
             * Format: int64
             * @description ID группы (обязателен для STUDENT)
             * @example 42
             */
            groupId?: number;
            /**
             * @description Табельный номер (обязателен для TEACHER)
             * @example EMP-00123
             */
            employeeNumber?: string;
            /**
             * Format: int64
             * @description Telegram user ID
             * @example 123456789
             */
            telegramId?: number;
        };
        /** @description Состояние сохранённой операции изменения пользователя; без секретов и внутренней команды */
        IdentityOperationStatus: {
            /** Format: uuid */
            operationId?: string;
            /** @enum {string} */
            status?: "PENDING" | "SUCCEEDED" | "REJECTED" | "FAILED_SAFE";
            /** Format: int32 */
            retryAfterSeconds?: number;
        };
        /** @description Стандартный формат ошибки API (RFC 9457 Problem Details) */
        ErrorResponse: {
            /**
             * Format: int32
             * @description HTTP статус код
             * @example 404
             */
            status?: number;
            /**
             * @description URI типа ошибки
             * @example https://api.rutcampustrack.ru/problems/resource-not-found
             */
            type?: string;
            /**
             * @description Краткое описание ошибки
             * @example Ресурс не найден
             */
            title?: string;
            /**
             * @description Детальное описание
             * @example Группа с id=99 не найдена
             */
            detail?: string;
            /**
             * @description URI запроса, вызвавшего ошибку
             * @example /api/academic/groups/99
             */
            instance?: string;
            /**
             * Format: date-time
             * @description Время возникновения ошибки (UTC)
             * @example 2026-04-24T10:15:30Z
             */
            timestamp?: string;
            /**
             * @description Correlation ID из MDC (для трассировки в логах)
             * @example abc-trace-123
             */
            traceId?: string;
            /** @description Ошибки валидации полей body DTO (только для 400) */
            fieldErrors?: components["schemas"]["FieldError"][];
            /**
             * @description Имя поля DTO, вызвавшего конфликт (только для 409, BUG-006-2)
             * @example login
             */
            field?: string;
            /**
             * @description Дополнительные данные для клиента (счётчики каскадного удаления, retry-after, и т.п.)
             * @example {
             *       "scheduleItemsCount": 3
             *     }
             */
            extras?: {
                [key: string]: unknown;
            };
        };
        /** @description Ошибка валидации одного поля DTO (RFC 9457 Extension Member) */
        FieldError: {
            /**
             * @description Имя поля DTO
             * @example displayName
             */
            field?: string;
            /** @description Отклонённое значение */
            rejectedValue?: unknown;
            /**
             * @description Локализованное сообщение об ошибке
             * @example Имя не может быть пустым
             */
            message?: string;
        };
        EntityModelUserResponse: {
            /** Format: int64 */
            id?: number;
            login?: string;
            lastName?: string;
            firstName?: string;
            middleName?: string;
            /** @enum {string} */
            role?: "ADMIN" | "TEACHER" | "STUDENT";
            /** @enum {string} */
            status?: "ACTIVE" | "EXPELLED" | "SUSPENDED" | "ARCHIVED";
            /** Format: int64 */
            groupId?: number;
            headman?: boolean;
            employeeNumber?: string;
            /** Format: int64 */
            telegramId?: number;
            /** Format: date-time */
            createdAt?: string;
            avatarId?: string;
            initialPassword?: string;
            roles?: components["schemas"]["RoleGrantViewResponse"][];
            fullName?: string;
            _links?: components["schemas"]["Links"];
        };
        RoleGrantViewResponse: {
            role?: string;
            status?: string;
            /** Format: int64 */
            groupId?: number;
            groupName?: string;
            selectable?: boolean;
            readOnly?: boolean;
            applicableStatuses?: string[];
            canUpdate?: boolean;
            blockedReason?: string;
        };
        RoleGrantUpdateRequest: {
            /** @enum {string} */
            status: "ACTIVE" | "EXPELLED" | "GRADUATED" | "SUSPENDED" | "DISMISSED" | "ARCHIVED";
            /**
             * Format: int64
             * @description Группа только для добавления STUDENT
             */
            groupId?: number;
            /** @description Табельный номер при добавлении TEACHER */
            employeeNumber?: string;
            /**
             * Format: int64
             * @description Telegram ID при добавлении STUDENT
             */
            telegramId?: number;
        };
        Put: {
            mode?: string;
            permissions?: string[];
            /** Format: int64 */
            expectedVersion?: number;
        };
        Profile: {
            /** Format: int64 */
            userId?: number;
            assigned?: boolean;
            mode?: string;
            permissions?: string[];
            /** Format: int64 */
            version?: number;
        };
        /** @description Запрос на установку порога посещаемости (0..100%); используется для global/group/subject */
        SetThresholdRequest: {
            /**
             * Format: int32
             * @description Минимальный процент посещаемости (0..100)
             * @example 75
             */
            minPercentage: number;
        };
        EntityModelThresholdResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            subjectId?: number;
            /** Format: int32 */
            minPercentage?: number;
            /** Format: date-time */
            updatedAt?: string;
            _links?: components["schemas"]["Links"];
        };
        /** @description Запрос на полное обновление предмета (PUT) */
        UpdateSubjectRequest: {
            /**
             * @description Название предмета
             * @example Математика
             */
            name: string;
            /**
             * @description Тип предмета (LECTURE / PRACTICE / LAB)
             * @example LECTURE
             * @enum {string}
             */
            type: "LECTURE" | "PRACTICE" | "LAB";
            /** @description Канонические типы занятий; отсутствие означает singleton type */
            lessonTypes?: ("LECTURE" | "PRACTICE" | "LAB")[];
        };
        AssignmentSummaryResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            teacherId?: number;
            /** Format: int64 */
            semesterId?: number;
            /** @enum {string} */
            lessonType?: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: date */
            validFrom?: string;
            /** Format: date */
            validUntilExclusive?: string;
        };
        EntityModelSubjectResponse: {
            /** Format: int64 */
            id?: number;
            name?: string;
            /** @enum {string} */
            type?: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: int64 */
            groupId?: number;
            lessonTypes?: ("LECTURE" | "PRACTICE" | "LAB")[];
            teacherIds?: number[];
            assignments?: components["schemas"]["AssignmentSummaryResponse"][];
            createdAssignmentIds?: number[];
            /** Format: date-time */
            createdAt?: string;
            pendingAdoptions?: components["schemas"]["PendingAdoptionResponse"][];
            pendingClosures?: components["schemas"]["PendingClosureResponse"][];
            _links?: components["schemas"]["Links"];
        };
        PendingAdoptionResponse: {
            /** Format: int64 */
            targetAssignmentId?: number;
            /** Format: uuid */
            adoptionOperationId?: string;
            state?: string;
            /** Format: int64 */
            teacherId?: number;
            /** Format: int64 */
            subjectId?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            semesterId?: number;
            /** @enum {string} */
            lessonType?: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: date */
            validFrom?: string;
            /** Format: date */
            validUntilExclusive?: string;
        };
        PendingClosureResponse: {
            /** Format: uuid */
            operationId?: string;
            /** Format: int64 */
            assignmentId?: number;
            /** Format: uuid */
            requestKey?: string;
            /** Format: date */
            validUntilExclusive?: string;
            /** Format: int64 */
            teacherId?: number;
            /** Format: int64 */
            subjectId?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            semesterId?: number;
            /** @enum {string} */
            lessonType?: "LECTURE" | "PRACTICE" | "LAB";
        };
        /** @description Запрос на полное обновление семестра (PUT) */
        UpdateSemesterRequest: {
            /**
             * @description Совместимое название; тип и учебный год задают серверное название
             * @example Осенний 2026/2027
             */
            name: string;
            /**
             * Format: date
             * @description Дата начала семестра
             * @example 2026-02-01
             */
            dateFrom: string;
            /**
             * Format: date
             * @description Дата окончания семестра
             * @example 2026-06-30
             */
            dateTo: string;
            /**
             * @description Обязательный тип семестра
             * @enum {string}
             */
            semesterType: "AUTUMN" | "SPRING";
            /**
             * Format: int32
             * @description Первый год учебного года, видимый и редактируемый в форме; вместе с типом задаёт серверное название
             * @example 2026
             */
            academicYear: number;
        };
        EntityModelSemesterResponse: {
            /** Format: int64 */
            id?: number;
            name?: string;
            /** Format: date */
            dateFrom?: string;
            /** Format: date */
            dateTo?: string;
            active?: boolean;
            /** @description Активация запрошена и ожидает московской даты начала семестра */
            activationScheduled?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** @enum {string} */
            semesterType?: "AUTUMN" | "SPRING";
            /** Format: int32 */
            academicYear?: number;
            archived?: boolean;
            /** @enum {string} */
            transition?: "NONE" | "ARCHIVING" | "RESTORING" | "DELETING";
            /** Format: int64 */
            stateVersion?: number;
            releasePending?: boolean;
            /** @description Запись блокирует изменения, пока семестр архивирован или переход не завершён */
            writeBlocked?: boolean;
            _links?: components["schemas"]["Links"];
        };
        CampusMapUpdateInventoryRequest: {
            code: string;
            label?: string;
        };
        CampusMapFloor: {
            id?: string;
            buildingId?: string;
            code?: string;
            label?: string;
            currentPlan?: components["schemas"]["CampusMapPlan"];
            /**
             * Format: int64
             * @description Number of distinct floor openings in the labelled period
             * @example 42
             */
            openCount?: number;
            /**
             * @description Period used for openCount
             * @example all_time
             */
            openCountPeriod?: string;
        };
        CampusMapFormatSlot: {
            /** @enum {string} */
            format?: "png" | "svg";
            /** @enum {string} */
            state?: "absent" | "processing" | "ready" | "failed";
            contentType?: string;
            id?: string;
            /** Format: int64 */
            bytes?: number;
            sha256?: string;
            /** Format: int32 */
            width?: number;
            /** Format: int32 */
            height?: number;
        };
        CampusMapPlan: {
            buildingId?: string;
            floorId?: string;
            version?: string;
            label?: string;
            catalogRevision?: string;
            png?: components["schemas"]["CampusMapFormatSlot"];
            svg?: components["schemas"]["CampusMapFormatSlot"];
        };
        CampusMapBuilding: {
            id?: string;
            code?: string;
            label?: string;
            floors?: components["schemas"]["CampusMapFloor"][];
        };
        /** @description Запрос на полное обновление домашнего задания (PUT) */
        UpdateHomeworkRequest: {
            /**
             * @description Название задания
             * @example Интегралы 1-5
             */
            title: string;
            /**
             * @description Описание задания (опционально)
             * @example Решить задачи 1-5 из учебника Демидовича
             */
            description?: string;
            /**
             * @description Ссылка на материалы (опционально)
             * @example https://example.com/homework.pdf
             */
            link?: string;
            /** @enum {string} */
            bindingMode?: "LESSON" | "DATE";
            /** Format: date */
            lessonDate?: string;
            /** Format: int32 */
            lessonNumber?: number;
            /** Format: uuid */
            requestKey: string;
            /** Format: int64 */
            expectedRevision: number;
            /** @enum {string} */
            lessonType?: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: int64 */
            selectedOccurrenceId?: number;
            /** Format: int64 */
            selectedOccurrenceRevision?: number;
        };
        EntityModelHomeworkResponse: {
            /** Format: int64 */
            id?: number;
            title?: string;
            description?: string;
            link?: string;
            /** Format: int64 */
            subjectId?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            semesterId?: number;
            /** Format: int64 */
            publishedBy?: number;
            /** Format: int64 */
            bindingId?: number;
            /** Format: uuid */
            requestKey?: string;
            completed?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date */
            lessonDate?: string;
            /** Format: int32 */
            lessonNumber?: number;
            /** @enum {string} */
            bindingMode?: "LESSON" | "DATE";
            /** @enum {string} */
            lessonType?: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: int64 */
            revision?: number;
            archived?: boolean;
            _links?: components["schemas"]["Links"];
        };
        /** @description Запрос на полное обновление группы (PUT) */
        UpdateGroupRequest: {
            /**
             * @description Название группы в формате ХХ(х)-NNN
             * @example УИТ-311
             */
            name: string;
            /**
             * @description Флаг активности группы (false = архив)
             * @example true
             */
            active?: boolean;
            /**
             * Format: int32
             * @description Явный исправленный срок обучения; при отсутствии сохраняется прежний
             */
            trainingDurationYears?: number;
        };
        EntityModelGroupResponse: {
            /** Format: int64 */
            id?: number;
            name?: string;
            alphabeticCode?: string;
            numericCode?: string;
            /** Format: int32 */
            currentCourse?: number;
            /** Format: int32 */
            trainingDurationYears?: number;
            durationStatus?: string;
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
            _links?: components["schemas"]["Links"];
        };
        /** @description Назначение старосты с проверкой ожидающего значения */
        AssignHeadmanRequest: {
            /**
             * Format: int64
             * @description ID студента из состава группы
             */
            studentId: number;
            /**
             * Format: int64
             * @description Текущий ID старосты; null означает ожидание отсутствия старосты
             */
            expectedHeadmanId?: number;
        };
        /** @description Результат назначения старосты */
        HeadmanAssignmentResponse: {
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            headmanId?: number;
            headmanFio?: string;
            changed?: boolean;
            activatesDraft?: boolean;
            /** Format: int32 */
            assistantsRevoked?: number;
        };
        AdminAccessInput: {
            mode?: string;
            permissions?: string[];
        };
        /** @description Запрос на создание нового пользователя (ADMIN-only) */
        CreateUserRequest: {
            /**
             * @description Фамилия пользователя
             * @example Иванов
             */
            lastName: string;
            /**
             * @description Имя пользователя
             * @example Иван
             */
            firstName: string;
            /**
             * @description Отчество (опционально)
             * @example Иванович
             */
            middleName?: string;
            /**
             * @description Роль пользователя в системе
             * @example STUDENT
             * @enum {string}
             */
            role: "ADMIN" | "TEACHER" | "STUDENT";
            /**
             * Format: int64
             * @description ID группы (обязателен для STUDENT)
             * @example 42
             */
            groupId?: number;
            /**
             * @description Табельный номер (обязателен для TEACHER)
             * @example EMP-00123
             */
            employeeNumber?: string;
            /**
             * Format: int64
             * @description Telegram user ID (опционально, для OTP и бот-уведомлений)
             * @example 123456789
             */
            telegramId?: number;
            adminAccess?: components["schemas"]["AdminAccessInput"];
        };
        EntityModelUserCreatedResponse: {
            /** Format: int64 */
            id?: number;
            login?: string;
            lastName?: string;
            firstName?: string;
            middleName?: string;
            /** @enum {string} */
            role?: "ADMIN" | "TEACHER" | "STUDENT";
            /** @enum {string} */
            status?: "ACTIVE" | "EXPELLED" | "SUSPENDED" | "ARCHIVED";
            /** Format: int64 */
            groupId?: number;
            headman?: boolean;
            employeeNumber?: string;
            /** Format: int64 */
            telegramId?: number;
            /** Format: date-time */
            createdAt?: string;
            /**
             * @description Одноразовый начальный пароль (per D-10). Показывается только при создании, впоследствии недоступен. Admin обязан передать безопасным каналом.
             * @example Kx9mQ7wP2n
             */
            initialPassword?: string;
            roles?: components["schemas"]["RoleGrantViewResponse"][];
            _links?: components["schemas"]["Links"];
        };
        /** @description Запрос на перевод студента в другую группу */
        TransferStudentRequest: {
            /**
             * Format: int64
             * @description ID новой группы
             * @example 43
             */
            newGroupId: number;
            /**
             * @description Причина перевода (для audit log)
             * @example Перевод по заявлению студента
             */
            reason: string;
        };
        RestoreRequest: {
            /** Format: uuid */
            operationId: string;
        };
        ArchiveRequest: {
            /** Format: uuid */
            operationId: string;
            previewDigest: string;
            password?: string;
        };
        /** @description Запрос на создание нового предмета (HEADMAN/ADMIN) */
        CreateSubjectRequest: {
            /**
             * @description Название предмета
             * @example Математика
             */
            name: string;
            /**
             * @description Тип предмета (LECTURE / PRACTICE / LAB)
             * @example LECTURE
             * @enum {string}
             */
            type: "LECTURE" | "PRACTICE" | "LAB";
            /**
             * @description Канонические типы занятий (1-3)
             * @example [
             *       "LECTURE",
             *       "PRACTICE"
             *     ]
             */
            lessonTypes?: ("LECTURE" | "PRACTICE" | "LAB")[];
            /** @description Начальные назначения, создаваемые атомарно */
            initialAssignments?: components["schemas"]["InitialAssignmentRequest"][];
            /** @description Устаревший список преподавателей; непустой список отклоняется */
            teacherIds?: number[];
        };
        InitialAssignmentRequest: {
            /** Format: int64 */
            teacherId: number;
            /** Format: int64 */
            semesterId: number;
            /** @enum {string} */
            lessonType: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: date */
            validFrom: string;
            /** Format: date */
            validUntilExclusive?: string;
        };
        AddSubjectTeacherRequest: {
            /** Format: int64 */
            semesterId: number;
            /** @enum {string} */
            lessonType: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: date */
            validFrom: string;
            /** Format: date */
            validUntilExclusive?: string;
        };
        EntityModelAssignmentResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            teacherId?: number;
            teacherName?: string;
            /** Format: int64 */
            subjectId?: number;
            subjectName?: string;
            /** Format: int64 */
            groupId?: number;
            groupName?: string;
            /** Format: int64 */
            semesterId?: number;
            /** @enum {string} */
            lessonType?: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: date */
            validFrom?: string;
            /** Format: date */
            validUntilExclusive?: string;
            _links?: components["schemas"]["Links"];
        };
        ConfirmRequest: {
            /** Format: uuid */
            operationId: string;
            previewDigest: string;
            challengeId: string;
            code: string;
        };
        Operation: {
            /** Format: uuid */
            operationId?: string;
            subjectId?: string;
            semesterId?: string;
            /** @enum {string} */
            phase?: "PREPARING" | "SEALED" | "COMMITTING" | "COMPLETED" | "RELEASING" | "CANCELLED";
            retryable?: boolean;
            reason?: string;
        };
        ChallengeRequest: {
            /** Format: uuid */
            operationId: string;
            previewDigest: string;
            password: string;
        };
        Challenge: {
            challengeId?: string;
            /** Format: date-time */
            expiresAt?: string;
            /** Format: int32 */
            attemptsRemaining?: number;
        };
        /** @description Запрос на создание нового академического семестра (ADMIN) */
        CreateSemesterRequest: {
            /**
             * @description Совместимое название; когда указаны тип и учебный год, сервер формирует его сам
             * @example Осенний 2026/2027
             */
            name: string;
            /**
             * Format: date
             * @description Дата начала семестра
             * @example 2026-02-01
             */
            dateFrom: string;
            /**
             * Format: date
             * @description Дата окончания семестра
             * @example 2026-06-30
             */
            dateTo: string;
            /**
             * @description Обязательный тип семестра
             * @enum {string}
             */
            semesterType: "AUTUMN" | "SPRING";
            /**
             * Format: int32
             * @description Первый год учебного года, видимый и редактируемый в форме; вместе с типом задаёт серверное название
             * @example 2026
             */
            academicYear: number;
        };
        /** @description Состояние одной команды архивации или восстановления семестра */
        SemesterArchiveOperationResponse: {
            /** Format: uuid */
            operationId?: string;
            /** Format: int64 */
            semesterId?: number;
            /** @enum {string} */
            action?: "ARCHIVE" | "RESTORE" | "DELETE";
            /** @enum {string} */
            operationState?: "PENDING" | "COMPLETED" | "ERROR";
            retryable?: boolean;
            /** Format: int64 */
            stateVersion?: number;
            active?: boolean;
            archived?: boolean;
            /** @enum {string} */
            transition?: "NONE" | "ARCHIVING" | "RESTORING" | "DELETING";
            releasePending?: boolean;
            /** @enum {string} */
            academic?: "NOT_STARTED" | "PENDING" | "READY" | "PREPARED_RESTORE" | "RELEASE_PENDING" | "RELEASED" | "DELETED";
            /** @enum {string} */
            schedule?: "NOT_STARTED" | "PENDING" | "READY" | "PREPARED_RESTORE" | "RELEASE_PENDING" | "RELEASED" | "DELETED";
            /** @enum {string} */
            attendance?: "NOT_STARTED" | "PENDING" | "READY" | "PREPARED_RESTORE" | "RELEASE_PENDING" | "RELEASED" | "DELETED";
            blockingReason?: string;
        };
        AdminGroupAttendanceParameters: {
            semesterId: string;
            search: string;
            sort: string;
            direction: string;
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
        };
        AdminGroupsParameters: {
            status?: string;
            search?: string;
            /** Format: int32 */
            course?: number;
            studyMode?: string;
            educationLevel?: string;
            sorts?: components["schemas"]["RegistrySort"][];
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
        };
        AdminUsersParameters: {
            search?: string;
            role?: string;
            status?: string;
            roleStatus?: string;
            /** Format: int64 */
            groupId?: number;
            sorts?: components["schemas"]["RegistrySort"][];
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
        };
        /** @description Current headman's own group roster export; group is selected by the server */
        HeadmanGroupCompositionParameters: {
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
        };
        /** @description A bounded text or numeric headman statistics filter */
        HeadmanStatsFilter: {
            field: string;
            contains?: string;
            minimum?: number;
            maximum?: number;
        };
        /** @description Headman current-group statistics export parameters */
        HeadmanStatsParameters: {
            /** Format: int64 */
            subjectId?: number;
            lessonTypes?: string[];
            sorts?: components["schemas"]["HeadmanStatsSort"][];
            filters?: components["schemas"]["HeadmanStatsFilter"][];
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
            /** Format: int64 */
            semesterId?: number;
        };
        /** @description One priority-ordered headman statistics sort field */
        HeadmanStatsSort: {
            field: string;
            descending?: boolean;
        };
        /** @description Current-group headman trend chart export parameters */
        HeadmanStatsTrendParameters: {
            /** @enum {string} */
            mode: "SEMESTER" | "WEEK" | "SUBJECT";
            /** Format: date */
            weekStart?: string;
            /** Format: int64 */
            subjectId?: number;
            lessonTypes?: string[];
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
            /** Format: int64 */
            semesterId?: number;
            /** @enum {string} */
            bucketUnit?: "DAY" | "WEEK";
        };
        HeadmanSubjectJournalParameters: {
            /** Format: int64 */
            subjectId: number;
            lessonTypes: string[];
            weekStarts?: string[];
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
        };
        /** @description Current headman weekly export parameters */
        HeadmanWeeklyCurrentParameters: {
            /** Format: date */
            weekStart: string;
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
        };
        /** @description Selected headman weekly export parameters */
        HeadmanWeeklySelectedParameters: {
            weekStarts: string[];
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
        };
        /** @description One allowlisted report and its bounded export parameters */
        IssueReportDownloadTicketRequest: {
            /** @enum {string} */
            kind: "TEACHER_JOURNAL" | "TEACHER_STATS" | "HEADMAN_WEEKLY_CURRENT" | "HEADMAN_WEEKLY_SELECTED" | "HEADMAN_STATS" | "HEADMAN_STATS_TREND" | "HEADMAN_GROUP_COMPOSITION" | "HEADMAN_SUBJECT_JOURNAL" | "ADMIN_USERS" | "ADMIN_GROUPS" | "ADMIN_GROUP_ATTENDANCE";
            teacherJournal?: components["schemas"]["TeacherJournalParameters"];
            teacherStats?: components["schemas"]["TeacherStatsParameters"];
            headmanWeeklyCurrent?: components["schemas"]["HeadmanWeeklyCurrentParameters"];
            headmanWeeklySelected?: components["schemas"]["HeadmanWeeklySelectedParameters"];
            headmanStats?: components["schemas"]["HeadmanStatsParameters"];
            headmanStatsTrend?: components["schemas"]["HeadmanStatsTrendParameters"];
            headmanGroupComposition?: components["schemas"]["HeadmanGroupCompositionParameters"];
            headmanSubjectJournal?: components["schemas"]["HeadmanSubjectJournalParameters"];
            adminUsers?: components["schemas"]["AdminUsersParameters"];
            adminGroups?: components["schemas"]["AdminGroupsParameters"];
            adminGroupAttendance?: components["schemas"]["AdminGroupAttendanceParameters"];
        };
        RegistrySort: {
            field: string;
            descending?: boolean;
        };
        /** @description Complete teacher subject journal parameters */
        TeacherJournalParameters: {
            /** Format: int64 */
            semesterId: number;
            /** Format: int64 */
            groupId: number;
            /** Format: int64 */
            subjectId: number;
            lessonTypes: string[];
            /** Format: date */
            dateFrom?: string;
            /** Format: date */
            dateTo?: string;
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
            pastOnly?: boolean;
            lessonId?: string;
        };
        /** @description Teacher statistics export parameters */
        TeacherStatsParameters: {
            /** Format: int64 */
            semesterId: number;
            scope: string;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            subjectId?: number;
            lessonTypes?: string[];
            sorts?: string[];
            filters?: string[];
            /** @enum {string} */
            format: "docx" | "pdf" | "png" | "html" | "xlsx";
        };
        /** @description Short-lived report download capability */
        ReportDownloadTicketResponse: {
            downloadPath?: string;
            /** Format: date-time */
            expiresAt?: string;
            suggestedFilename?: string;
        };
        SortOrder: {
            field: string;
            descending?: boolean;
        };
        Users: {
            search?: string;
            role?: string;
            /** @enum {string} */
            status?: "ACTIVE" | "EXPELLED" | "SUSPENDED" | "ARCHIVED";
            roleStatus?: string;
            /** Format: int64 */
            groupId?: number;
            sorts?: components["schemas"]["SortOrder"][];
            format: string;
        };
        Groups: {
            /**
             * @description Разрез реестра групп администратора
             * @enum {string}
             */
            status?: "ACTIVE" | "DRAFT" | "ARCHIVED";
            search?: string;
            /** Format: int32 */
            course?: number;
            /** @enum {string} */
            studyMode?: "FULL_TIME" | "PART_TIME" | "EXTRAMURAL";
            /** @enum {string} */
            educationLevel?: "BACHELOR" | "SPECIALIST" | "MASTER";
            sorts?: components["schemas"]["SortOrder"][];
            format: string;
        };
        CampusMapUploadPlanResponse: {
            /** @enum {string} */
            status?: "created" | "replaced";
            buildingCreated?: boolean;
            floorCreated?: boolean;
            plan?: components["schemas"]["CampusMapPlan"];
        };
        PlanDeleteRequest: {
            buildingCode: string;
            floorCode: string;
            /** @enum {string} */
            format?: "png" | "svg";
            /** Format: uuid */
            operationId: string;
            previewDigest: string;
            password: string;
        };
        PlanDeletionResult: {
            /** Format: uuid */
            operationId?: string;
            buildingCode?: string;
            floorCode?: string;
            /** @enum {string} */
            format?: "png" | "svg";
            status?: string;
        };
        CampusMapCreateFloorRequest: {
            /**
             * @description Owning building id
             * @example 1
             */
            buildingId: string;
            /**
             * @description Numeric floor code
             * @example 2
             */
            code: string;
            /** @description Human-readable label; defaults to the code */
            label?: string;
        };
        DeleteRequest: {
            /** Format: uuid */
            operationId: string;
            previewDigest: string;
            password: string;
        };
        DeletionResult: {
            /** Format: uuid */
            operationId?: string;
            /** @enum {string} */
            targetType?: "FLOOR" | "BUILDING";
            targetId?: string;
            status?: string;
        };
        CampusMapCreateBuildingRequest: {
            /**
             * @description Numeric campus building code
             * @example 20
             */
            code: string;
            /** @description Human-readable label; defaults to the code */
            label?: string;
        };
        ReconcileRequest: {
            archivePreviewDigest?: string;
            password?: string;
        };
        /** @description Запрос на создание нового домашнего задания (HEADMAN/ASSISTANT) */
        CreateHomeworkRequest: {
            /**
             * @description Название задания
             * @example Интегралы 1-5
             */
            title: string;
            /**
             * @description Описание задания (опционально)
             * @example Решить задачи 1-5 из учебника Демидовича
             */
            description?: string;
            /**
             * @description Ссылка на материалы (опционально)
             * @example https://example.com/homework.pdf
             */
            link?: string;
            /**
             * Format: int64
             * @description ID предмета
             * @example 42
             */
            subjectId: number;
            /**
             * Format: int64
             * @description ID группы
             * @example 42
             */
            groupId: number;
            /**
             * Format: int64
             * @description ID семестра
             * @example 42
             */
            semesterId: number;
            /**
             * Format: date
             * @description Дата пары (не в прошлом)
             * @example 2026-04-24
             */
            lessonDate: string;
            /**
             * Format: int32
             * @description Номер пары (1..8)
             * @example 3
             */
            lessonNumber?: number;
            /**
             * Format: uuid
             * @description Ключ идемпотентности команды (повтор запроса продолжает ту же публикацию)
             * @example 4d1f2f1f-42f0-4e9a-a9d4-fd5cbf9f9e1f
             */
            requestKey?: string;
            /**
             * @description Привязка к паре LESSON (по умолчанию) или к календарному дню DATE
             * @enum {string}
             */
            bindingMode?: "LESSON" | "DATE";
            /**
             * @description Тип занятия для DATE; для LESSON должен совпадать с выбранной парой
             * @enum {string}
             */
            lessonType?: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: int64 */
            selectedOccurrenceId?: number;
            /** Format: int64 */
            selectedOccurrenceRevision?: number;
        };
        HomeworkPublicationPendingResponse: {
            homeworkId?: string;
            bindingId?: string;
            /** Format: uuid */
            requestKey?: string;
            /** @enum {string} */
            state?: "PENDING" | "ACTIVE" | "ARCHIVED";
        };
        /** @description Запрос на создание новой студенческой группы (ADMIN) */
        CreateGroupRequest: {
            /**
             * @description Название группы в формате ХХ(х)-NNN
             * @example УИТ-311
             */
            name: string;
        };
        ConfirmAdminGroupDeletionRequest: {
            password: string;
            previewDigest: string;
        };
        AdminGroupDeletionOperation: {
            /** Format: uuid */
            operationId?: string;
            groupId?: string;
            /** @enum {string} */
            phase?: "PREPARING" | "COMMITTING" | "COMPLETED" | "RELEASING" | "CANCELLED";
            retryable?: boolean;
            counts?: {
                [key: string]: number;
            };
            failure?: string;
        };
        /** @description Создание группы в реестре ADMIN */
        CreateAdminGroupRequest: {
            /**
             * @description Буквенная часть кода группы
             * @example ИВТ
             */
            alphabeticCode: string;
            /**
             * @description Трёхзначная цифровая часть; первая цифра — текущий курс
             * @example 311
             */
            numericCode: string;
            /**
             * Format: int32
             * @description Срок обучения в годах
             * @example 4
             */
            trainingDurationYears: number;
        };
        /** @description Строка реестра групп администратора */
        AdminGroupResponse: {
            /** Format: int64 */
            id?: number;
            name?: string;
            alphabeticCode?: string;
            numericCode?: string;
            /** Format: int32 */
            currentCourse?: number;
            /** Format: int32 */
            programTypeDigit?: number;
            /** @enum {string} */
            studyMode?: "FULL_TIME" | "PART_TIME" | "EXTRAMURAL";
            /** @enum {string} */
            educationLevel?: "BACHELOR" | "SPECIALIST" | "MASTER";
            /** Format: int32 */
            trainingDurationYears?: number;
            durationStatus?: string;
            /**
             * @description Разрез реестра групп администратора
             * @enum {string}
             */
            status?: "ACTIVE" | "DRAFT" | "ARCHIVED";
            draftReason?: string;
            /** Format: int64 */
            studentCount?: number;
            headmanFio?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        PromotionExecuteRequest: {
            /** Format: int64 */
            cycleSemesterId: number;
            previewVersion: string;
            /** Format: int64 */
            groupId?: number;
        };
        /** @description Конфликт для отдельного префикса (весь префикс пропущен: name_conflict / unknown_type / parse_error / invalid_code / unrepresentable_next_course) */
        PrefixConflict: {
            prefix?: string;
            reason?: string;
            message?: string;
            groupIds?: number[];
        };
        /** @description Одна запись в плане промоушена групп (переименование или архивация) */
        PromotionPreviewItem: {
            /** Format: int64 */
            id?: number;
            from?: string;
            to?: string;
            /** @enum {string} */
            action?: "PROMOTE" | "ARCHIVE";
            /** Format: int64 */
            studentCount?: number;
        };
        /** @description Группа, пропущенная в цикле перевода */
        PromotionSkippedItem: {
            /** Format: int64 */
            id?: number;
            name?: string;
            /** Format: int64 */
            studentCount?: number;
            /** @enum {string} */
            reason?: "ALREADY_PROCESSED" | "CREATED_AFTER_CYCLE_END";
            /** @enum {string} */
            previousAction?: "PROMOTE" | "ARCHIVE";
            previousFrom?: string;
            previousTo?: string;
        };
        /** @description Итог операции промоушена групп (preview или execute, HATEOAS Level 3 с _links) */
        PromotionSummary: {
            toPromote?: components["schemas"]["PromotionPreviewItem"][];
            toArchive?: components["schemas"]["PromotionPreviewItem"][];
            skipped?: components["schemas"]["PromotionSkippedItem"][];
            conflicts?: components["schemas"]["PrefixConflict"][];
            /** Format: int64 */
            cycleSemesterId?: number;
            /** Format: date */
            cycleDateTo?: string;
            /** Format: int64 */
            groupId?: number;
            previewVersion?: string;
            dryRun?: boolean;
            executed?: boolean;
            /** Format: int32 */
            promoteCount?: number;
            /** Format: int32 */
            archiveCount?: number;
            /** Format: int64 */
            promotedStudentCount?: number;
            /** Format: int64 */
            archivedStudentCount?: number;
            /** Format: int32 */
            skippedGroupCount?: number;
            /** Format: int64 */
            skippedStudentCount?: number;
            /** Format: int32 */
            conflictCount?: number;
            _links?: components["schemas"]["Links"];
        };
        PromotionPreviewRequest: {
            /** Format: int64 */
            groupId?: number;
        };
        Create: {
            /** Format: uuid */
            requestId: string;
            report: components["schemas"]["IssueReportDownloadTicketRequest"];
        };
        Status: {
            /** Format: uuid */
            jobId: string;
            /** @enum {string} */
            state: "queued" | "preparing" | "ready" | "error";
            suggestedFilename: string;
            /** Format: date-time */
            expiresAt: string;
            errorCode: string | null;
        };
        /** @description Запрос на назначение помощника старосты с начальным набором прав */
        AssignAssistantRequest: {
            /**
             * Format: int64
             * @description ID студента, назначаемого помощником
             * @example 42
             */
            studentId: number;
            /**
             * Format: int64
             * @description ID группы, в которой действуют права
             * @example 42
             */
            groupId: number;
            /** @description Набор делегируемых прав (минимум один элемент) */
            permissions: ("MARK_ATTENDANCE" | "MANAGE_EXCUSES" | "MANAGE_HOMEWORK" | "CANCEL_LESSONS" | "VIEW_STATS")[];
        };
        EntityModelAssistantResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            studentId?: number;
            studentName?: string;
            fullName?: string;
            login?: string;
            /** Format: int64 */
            groupId?: number;
            permissions?: ("MARK_ATTENDANCE" | "MANAGE_EXCUSES" | "MANAGE_HOMEWORK" | "CANCEL_LESSONS" | "VIEW_STATS")[];
            active?: boolean;
            /** Format: date-time */
            assignedAt?: string;
            /** Format: date-time */
            revokedAt?: string;
            _links?: components["schemas"]["Links"];
        };
        /** @description Запрос на назначение преподавателя на предмет в группе и семестре */
        AssignTeacherRequest: {
            /**
             * @description Табельный номер преподавателя
             * @example EMP-00123
             */
            employeeNumber: string;
            /**
             * Format: int64
             * @description ID предмета
             * @example 42
             */
            subjectId: number;
            /**
             * Format: int64
             * @description ID группы
             * @example 42
             */
            groupId: number;
            /**
             * Format: int64
             * @description ID семестра
             * @example 42
             */
            semesterId: number;
            /**
             * @description Тип занятия
             * @example LECTURE
             * @enum {string}
             */
            lessonType: "LECTURE" | "PRACTICE" | "LAB";
            /**
             * Format: date
             * @description Дата начала действия назначения
             */
            validFrom: string;
            /**
             * Format: date
             * @description Дата окончания действия назначения (исключительно)
             */
            validUntilExclusive?: string;
        };
        ReplaceAssignmentRequest: {
            /**
             * @description ID преподавателя, который примет назначение
             * @example 501
             */
            replacementTeacherId: string;
            /**
             * Format: date
             * @description Дата начала нового назначения, без нормализации
             * @example 2026-10-01
             */
            effectiveFrom: string;
            /**
             * Format: uuid
             * @description UUID для повтора этой же операции
             * @example b9c6d13f-3326-4333-b59d-e7555f5ffeca
             */
            requestKey: string;
        };
        AssignmentReplacementResponse: {
            /** Format: uuid */
            operationId?: string;
            /** Format: int64 */
            sourceAssignmentId?: number;
            /** Format: int64 */
            targetAssignmentId?: number;
            /** Format: int64 */
            sourceTeacherId?: number;
            /** Format: int64 */
            targetTeacherId?: number;
            /** Format: int64 */
            subjectId?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            semesterId?: number;
            lessonType?: string;
            /** Format: date */
            effectiveFrom?: string;
            /** Format: date */
            sourceValidUntilExclusive?: string;
            /** Format: date */
            targetValidUntilExclusive?: string;
            state?: string;
            scheduleReceiptState?: string;
            /** Format: int64 */
            movedCount?: number;
            /** Format: int64 */
            skippedCount?: number;
        };
        Export: {
            semesterId: string;
            search: string;
            /** @enum {string} */
            sort: "GROUP_NAME" | "PRESENT_PERCENT" | "PRESENT_OR_EXCUSED_PERCENT" | "COUNTED_LESSONS" | "PRESENT" | "ABSENT" | "EXCUSED";
            /** @enum {string} */
            direction: "ASC" | "DESC";
            /** @enum {string} */
            format: "html" | "pdf" | "docx" | "xlsx" | "png";
        };
        /** @description Частичное обновление пользователя (PATCH) */
        PatchUserRequest: {
            /**
             * @description Фамилия
             * @example Иванов
             */
            lastName?: string;
            /**
             * @description Имя
             * @example Иван
             */
            firstName?: string;
            /**
             * @description Отчество; пустая строка очищает значение
             * @example Иванович
             */
            middleName?: string;
            /**
             * @description Флаг старосты (только для STUDENT)
             * @example true
             */
            isHeadman?: boolean;
            /**
             * Format: int64
             * @description Новый ID группы
             * @example 42
             */
            groupId?: number;
            /**
             * @description Табельный номер; пустая строка очищает значение, кроме аккаунтов с ролью TEACHER
             * @example EMP-00123
             */
            employeeNumber?: string;
            /**
             * Format: int64
             * @description Telegram user ID
             * @example 123456789
             */
            telegramId?: number;
            /**
             * @description Статус учётной записи
             * @example ACTIVE
             * @enum {string}
             */
            status?: "ACTIVE" | "EXPELLED" | "SUSPENDED" | "ARCHIVED";
        };
        /** @description Смена аватара пользователя (BUG-004) */
        UpdateAvatarRequest: {
            /**
             * @description ID пресет-аватара формата avatar_NN (NN — 2 цифры). NULL или пустая строка — сбросить в дефолт (инициалы)
             * @example avatar_03
             */
            avatarId?: string;
        };
        /** @description Запрос на обновление прав помощника старосты */
        UpdateAssistantPermissionsRequest: {
            /** @description Новый набор прав. Пустой набор отзывает помощника только при переданном expectedPermissions. */
            permissions: ("MARK_ATTENDANCE" | "MANAGE_EXCUSES" | "MANAGE_HOMEWORK" | "CANCEL_LESSONS" | "VIEW_STATS")[];
            /** @description Ранее прочитанный полный набор прав. Сравнение без учёта порядка атомарно; несовпадение возвращает 409 ASSISTANT_PERMISSIONS_CONFLICT. Без поля сохраняется прежнее безусловное обновление. */
            expectedPermissions?: ("MARK_ATTENDANCE" | "MANAGE_EXCUSES" | "MANAGE_HOMEWORK" | "CANCEL_LESSONS" | "VIEW_STATS")[];
        };
        Pageable: {
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            sort?: string[];
        };
        PagedResourcesAssemblerUserResponse: {
            forceFirstAndLastRels?: boolean;
        };
        PageMetadata: {
            /** Format: int64 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
            /** Format: int64 */
            number?: number;
        };
        PagedModelEntityModelUserResponse: {
            _embedded?: {
                userResponseList?: components["schemas"]["EntityModelUserResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        Preview: {
            /** Format: int64 */
            userId?: number;
            /** Format: int64 */
            linkedGroupCount?: number;
            /** Format: int64 */
            attendanceMarksCount?: number;
            /** Format: int64 */
            activeHeadmanGroupCount?: number;
            /** Format: int64 */
            soleTeacherAssignmentCount?: number;
            requiresPassword?: boolean;
            /** Format: date-time */
            academicObservedAt?: string;
            /** Format: date-time */
            attendanceObservedAt?: string;
            attendanceSnapshotDigest?: string;
            previewDigest?: string;
            /** Format: date-time */
            expiresAt?: string;
        };
        CollectionModelEntityModelUserResponse: {
            _embedded?: {
                userResponseList?: components["schemas"]["EntityModelUserResponse"][];
            };
            _links?: components["schemas"]["Links"];
        };
        PagedResourcesAssemblerTeacherLookupResponse: {
            forceFirstAndLastRels?: boolean;
        };
        EntityModelTeacherLookupResponse: {
            /** Format: int64 */
            id?: number;
            fullName?: string;
            employeeNumber?: string;
            _links?: components["schemas"]["Links"];
        };
        PagedModelEntityModelTeacherLookupResponse: {
            _embedded?: {
                teacherLookupResponseList?: components["schemas"]["EntityModelTeacherLookupResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        CollectionModelEntityModelUserSummaryResponse: {
            _embedded?: {
                userSummaryResponseList?: components["schemas"]["EntityModelUserSummaryResponse"][];
            };
            _links?: components["schemas"]["Links"];
        };
        EntityModelUserSummaryResponse: {
            /**
             * Format: int64
             * @description ID пользователя
             */
            id?: number;
            /** @description Display name: «Иванов Иван Иванович» или «Иванов Иван» если отчества нет */
            fullName?: string;
            _links?: components["schemas"]["Links"];
        };
        PagedResourcesAssemblerThresholdResponse: {
            forceFirstAndLastRels?: boolean;
        };
        PagedModelEntityModelThresholdResponse: {
            _embedded?: {
                thresholdResponseList?: components["schemas"]["EntityModelThresholdResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        EntityModelResolvedThresholdResponse: {
            /** Format: int32 */
            minPercentage?: number;
            level?: string;
            /** Format: int64 */
            sourceId?: number;
            _links?: components["schemas"]["Links"];
        };
        PagedResourcesAssemblerSubjectResponse: {
            forceFirstAndLastRels?: boolean;
        };
        PagedModelEntityModelSubjectResponse: {
            _embedded?: {
                subjectResponseList?: components["schemas"]["EntityModelSubjectResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        PagedResourcesAssemblerSemesterResponse: {
            forceFirstAndLastRels?: boolean;
        };
        PagedModelEntityModelSemesterResponse: {
            _embedded?: {
                semesterResponseList?: components["schemas"]["EntityModelSemesterResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        /** @description Количество удаляемых записей по доменам */
        SemesterDeletionCounts: {
            /** Format: int64 */
            scheduleTemplates?: number;
            /** Format: int64 */
            oneOffLessons?: number;
            /** Format: int64 */
            lessons?: number;
            /** Format: int64 */
            assignments?: number;
            /** Format: int64 */
            homeworks?: number;
            /** Format: int64 */
            attendanceMarks?: number;
            /** Format: int64 */
            studentRequests?: number;
        };
        /** @description Предварительный расчёт удаления семестра без блокировки записей */
        SemesterDeletionPreviewResponse: {
            /** Format: int64 */
            semesterId?: number;
            semesterName?: string;
            /** @enum {string} */
            priorState?: "ACTIVE" | "INACTIVE" | "ARCHIVED";
            /** Format: int64 */
            stateVersion?: number;
            previewDigest?: string;
            counts?: components["schemas"]["SemesterDeletionCounts"];
        };
        /** @description Текущее состояние семестра и последняя команда архивации */
        SemesterArchiveStatusResponse: {
            /** Format: int64 */
            semesterId?: number;
            /** Format: int64 */
            stateVersion?: number;
            active?: boolean;
            archived?: boolean;
            /** @enum {string} */
            transition?: "NONE" | "ARCHIVING" | "RESTORING" | "DELETING";
            releasePending?: boolean;
            writeBlocked?: boolean;
            operation?: components["schemas"]["SemesterArchiveOperationResponse"];
        };
        /** @description Результат проверки перекрытия диапазона дат с существующими семестрами */
        OverlapCheckResponse: {
            /**
             * @description true — диапазон пересекает существующий семестр
             * @example false
             */
            overlaps?: boolean;
            /**
             * @description Название первого конфликтующего семестра или null
             * @example Весенний 2026
             */
            conflictingName?: string;
        };
        /** @description Состояние операции окончательного удаления семестра */
        SemesterDeletionOperationResponse: {
            /** Format: uuid */
            operationId?: string;
            /** Format: int64 */
            semesterId?: number;
            /** @enum {string} */
            phase?: "PREPARING" | "RELEASING" | "DELETING" | "COMPLETED" | "CANCELLED";
            retryable?: boolean;
            /** Format: int64 */
            stateVersion?: number;
            counts?: components["schemas"]["SemesterDeletionCounts"];
            reason?: string;
            blockingReason?: string;
            refreshedPreview?: components["schemas"]["SemesterDeletionPreviewResponse"];
        };
        FormatOption: {
            code?: string;
            label?: string;
            contentType?: string;
            extension?: string;
        };
        PlanTargetResponse: {
            buildingCode?: string;
            floorCode?: string;
            /** @enum {string} */
            status?: "found" | "absent";
            floor?: components["schemas"]["CampusMapFloor"];
        };
        PlanDeletionPreview: {
            buildingCode?: string;
            floorCode?: string;
            /** @enum {string} */
            format?: "png" | "svg";
            label?: string;
            /** Format: int64 */
            files?: number;
            /** Format: int64 */
            bytes?: number;
            previewDigest?: string;
            /** @enum {string|null} */
            deletionScope?: "ALL_VERSIONS" | "CURRENT_FORMAT" | null;
        };
        DeletionPreview: {
            /** @enum {string} */
            targetType?: "FLOOR" | "BUILDING";
            targetId?: string;
            buildingId?: string;
            label?: string;
            /** Format: int64 */
            versions?: number;
            /** Format: int64 */
            assets?: number;
            /** Format: int64 */
            bytes?: number;
            /** Format: int64 */
            openCount?: number;
            /** Format: int64 */
            remainingFloors?: number;
            previewDigest?: string;
        };
        PagedResourcesAssemblerHomeworkResponse: {
            forceFirstAndLastRels?: boolean;
        };
        PagedModelEntityModelHomeworkResponse: {
            _embedded?: {
                homeworkResponseList?: components["schemas"]["EntityModelHomeworkResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        HomeworkHistoryResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            revision?: number;
            /** Format: int64 */
            actorId?: number;
            actorDisplayName?: string;
            /** Format: date-time */
            occurredAt?: string;
            action?: string;
            before?: components["schemas"]["HomeworkSnapshot"];
            after?: components["schemas"]["HomeworkSnapshot"];
        };
        HomeworkSnapshot: {
            title?: string;
            description?: string;
            link?: string;
            /** @enum {string} */
            bindingMode?: "LESSON" | "DATE";
            /** Format: date */
            lessonDate?: string;
            /** Format: int32 */
            lessonNumber?: number;
            /** @enum {string} */
            lessonType?: "LECTURE" | "PRACTICE" | "LAB";
        };
        PageHomeworkHistoryResponse: {
            /** Format: int32 */
            totalPages?: number;
            /** Format: int64 */
            totalElements?: number;
            first?: boolean;
            last?: boolean;
            /** Format: int32 */
            size?: number;
            content?: components["schemas"]["HomeworkHistoryResponse"][];
            /** Format: int32 */
            number?: number;
            sort?: components["schemas"]["SortObject"];
            /** Format: int32 */
            numberOfElements?: number;
            pageable?: components["schemas"]["PageableObject"];
            empty?: boolean;
        };
        PageableObject: {
            /** Format: int64 */
            offset?: number;
            sort?: components["schemas"]["SortObject"];
            paged?: boolean;
            unpaged?: boolean;
            /** Format: int32 */
            pageSize?: number;
            /** Format: int32 */
            pageNumber?: number;
        };
        SortObject: {
            empty?: boolean;
            sorted?: boolean;
            unsorted?: boolean;
        };
        PagedResourcesAssemblerGroupResponse: {
            forceFirstAndLastRels?: boolean;
        };
        PagedModelEntityModelGroupResponse: {
            _embedded?: {
                groupResponseList?: components["schemas"]["EntityModelGroupResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        /** @description Кандидат в старосты */
        HeadmanCandidateResponse: {
            /** Format: int64 */
            id?: number;
            fio?: string;
            current?: boolean;
        };
        /** @description Состав группы и текущий староста */
        HeadmanRosterResponse: {
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            currentHeadmanId?: number;
            currentHeadmanFio?: string;
            candidates?: components["schemas"]["HeadmanCandidateResponse"][];
            /** Format: int32 */
            activeAssistantCount?: number;
        };
        /** @description Предпросмотр назначения старосты */
        HeadmanAssignmentPreviewResponse: {
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            currentHeadmanId?: number;
            currentHeadmanFio?: string;
            /** Format: int64 */
            candidateId?: number;
            candidateFio?: string;
            sameHeadman?: boolean;
            activatesDraft?: boolean;
            /** Format: int32 */
            assistantsToRevoke?: number;
        };
        AdminGroupDeletionPreview: {
            groupId?: string;
            groupName?: string;
            previewDigest?: string;
            counts?: {
                [key: string]: number;
            };
        };
        /** @description Страница реестра групп администратора */
        AdminGroupRegistryResponse: {
            items?: components["schemas"]["AdminGroupResponse"][];
            /** Format: int32 */
            number?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            /** Format: int64 */
            activeCount?: number;
            /** Format: int64 */
            draftCount?: number;
            /** Format: int64 */
            archivedCount?: number;
        };
        GroupOptionsResponse: {
            items?: components["schemas"]["Item"][];
            /** Format: int32 */
            number?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
        };
        Item: {
            /** Format: int64 */
            id?: number;
            name?: string;
            /** @description Группа архивирована */
            archived: boolean;
        };
        /** @description Суточный прирост карточек масштаба к концу вчерашнего дня Europe/Moscow */
        DashboardDailyGrowthResponse: {
            /** @enum {string} */
            status?: "AVAILABLE" | "UNKNOWN_HISTORY";
            /** Format: date */
            baselineDate?: string;
            /** Format: int64 */
            studentsDelta?: number | null;
            /** Format: int64 */
            teachersDelta?: number | null;
            /** Format: int64 */
            groupsDelta?: number | null;
        };
        EntityModelDashboardStatsResponse: {
            /** Format: int64 */
            totalStudents?: number;
            /** Format: int64 */
            totalTeachers?: number;
            /** Format: int64 */
            totalGroups?: number;
            /** Format: int64 */
            activeGroups?: number;
            activeSemesterName?: string;
            dailyGrowth?: components["schemas"]["DashboardDailyGrowthResponse"];
            _links?: components["schemas"]["Links"];
        };
        Comparison: {
            /** @enum {string} */
            state?: "AVAILABLE" | "UNAVAILABLE" | "INSUFFICIENT_HISTORY";
            range?: components["schemas"]["Range"];
            /** Format: int64 */
            previousValue?: number;
            /** Format: int64 */
            absoluteDelta?: number;
            relativeDeltaPercent?: number;
            previousPercent?: number;
            percentPointDelta?: number;
        };
        Coverage: {
            kind?: string;
            /** Format: date-time */
            from?: string;
        };
        DashboardActivityResponse: {
            /** Format: date-time */
            observedAt?: string;
            timeZone?: string;
            period?: string;
            bucket?: string;
            historyScope?: string;
            consistency?: string;
            summary?: components["schemas"]["Section"];
            series?: components["schemas"]["Section"][];
        };
        Metric: {
            /** @enum {string} */
            state?: "AVAILABLE" | "UNAVAILABLE" | "INSUFFICIENT_HISTORY";
            /** Format: int64 */
            value?: number;
            /** Format: int64 */
            numerator?: number;
            /** Format: int64 */
            denominator?: number;
            percent?: number;
            coverage?: components["schemas"]["Coverage"];
            reason?: string;
            comparison?: components["schemas"]["Comparison"];
        };
        Range: {
            /** Format: date-time */
            from?: string;
            /** Format: date-time */
            to?: string;
        };
        Section: {
            range?: components["schemas"]["Range"];
            metrics?: {
                [key: string]: components["schemas"]["Metric"];
            };
        };
        CollectionModelEntityModelAssistantResponse: {
            _embedded?: {
                assistantResponseList?: components["schemas"]["EntityModelAssistantResponse"][];
            };
            _links?: components["schemas"]["Links"];
        };
        AssistantPermissionOption: {
            /** @enum {string} */
            code?: "MARK_ATTENDANCE" | "MANAGE_EXCUSES" | "MANAGE_HOMEWORK" | "CANCEL_LESSONS" | "VIEW_STATS";
            label?: string;
        };
        PagedResourcesAssemblerAssignmentResponse: {
            forceFirstAndLastRels?: boolean;
        };
        PagedModelEntityModelAssignmentResponse: {
            _embedded?: {
                assignmentResponseList?: components["schemas"]["EntityModelAssignmentResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        Page: {
            semester?: components["schemas"]["Semester"];
            search?: string;
            /** @enum {string} */
            sort?: "GROUP_NAME" | "PRESENT_PERCENT" | "PRESENT_OR_EXCUSED_PERCENT" | "COUNTED_LESSONS" | "PRESENT" | "ABSENT" | "EXCUSED";
            /** @enum {string} */
            direction?: "ASC" | "DESC";
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
            /** Format: date-time */
            observedAt?: string;
            rows?: components["schemas"]["Row"][];
        };
        Row: {
            groupId?: string;
            groupName?: string;
            /** Format: int64 */
            countedLessons?: number;
            /** Format: int64 */
            presentCount?: number;
            /** Format: int64 */
            absentCount?: number;
            /** Format: int64 */
            excusedCount?: number;
            /** Format: int64 */
            opportunities?: number;
            /** Format: double */
            presentPercent?: number | null;
            /** Format: double */
            presentOrExcusedPercent?: number | null;
            /** @enum {string} */
            dataState?: "AVAILABLE" | "NO_DATA";
        };
        Semester: {
            id?: string;
            name?: string;
            /** Format: date */
            dateFrom?: string;
            /** Format: date */
            dateTo?: string;
            active?: boolean;
            archived?: boolean;
        };
        Semesters: {
            items?: components["schemas"]["Semester"][];
        };
        Catalog: {
            permissions?: components["schemas"]["Entry"][];
        };
        Entry: {
            code?: string;
            label?: string;
            description?: string;
            group?: string;
            requires?: string[];
            fullOnly?: boolean;
        };
        Delete: {
            /** Format: int64 */
            expectedVersion?: number;
        };
        /** @description Запрос на окончательное удаление семестра */
        DeleteSemesterRequest: {
            /** @description Digest актуального предварительного расчёта */
            previewDigest: string;
            /** @description Текущий пароль ADMIN; не сохраняется и не выводится в журнал */
            password: string;
        };
        Link: {
            href?: string;
            hreflang?: string;
            title?: string;
            type?: string;
            deprecation?: string;
            profile?: string;
            name?: string;
            templated?: boolean;
        };
        Links: {
            [key: string]: components["schemas"]["Link"];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    getUser: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Пользователь найден */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Пользователь не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateUser: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateUserRequest"];
            };
        };
        responses: {
            /** @description Пользователь обновлён */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Пользователь не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    archiveUser: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется защищённый POST /{id}/archive: protectedroute_required */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    patchUser: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PatchUserRequest"];
            };
        };
        responses: {
            /** @description Пользователь обновлён */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Пользователь не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateRoleGrant: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
                role: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RoleGrantUpdateRequest"];
            };
        };
        responses: {
            /** @description Роль сохранена */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Недопустимая роль или статус */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Пользователь не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт role-data */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Profile"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    put: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Put"];
            };
        };
        responses: {
            /** @description Операция завершена */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Profile"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    delete: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Delete"];
            };
        };
        responses: {
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Операция завершена */
            204: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    setSubjectThreshold: {
        parameters: {
            query: {
                subjectId: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SetThresholdRequest"];
            };
        };
        responses: {
            /** @description Порог установлен */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    setGroupThreshold: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SetThresholdRequest"];
            };
        };
        responses: {
            /** @description Порог установлен */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    setGlobalThreshold: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SetThresholdRequest"];
            };
        };
        responses: {
            /** @description Порог установлен */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getSubject: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Предмет найден */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Предмет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateSubject: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateSubjectRequest"];
            };
        };
        responses: {
            /** @description Предмет обновлён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Предмет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deleteSubject: {
        parameters: {
            query?: {
                force?: boolean;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Предмет удалён */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Предмет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Предмет используется в расписании (есть посещаемость) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getSemester: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Семестр найден */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateSemester: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateSemesterRequest"];
            };
        };
        responses: {
            /** @description Семестр обновлён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deleteSemester: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeleteSemesterRequest"];
            };
        };
        responses: {
            /** @description Семестр удалён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterDeletionOperationResponse"];
                };
            };
            /** @description Удаление продолжается; результат доступен по operationId */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterDeletionOperationResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterDeletionOperationResponse"];
                };
            };
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterDeletionOperationResponse"];
                };
            };
            /** @description Preview устарел; возвращён свежий preview */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterDeletionOperationResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Auth или участник недоступен; операция не подтверждена */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterDeletionOperationResponse"];
                };
            };
        };
    };
    updateFloor: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                floorId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CampusMapUpdateInventoryRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapFloor"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateBuilding: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                buildingId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CampusMapUpdateInventoryRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapBuilding"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getHomework: {
        parameters: {
            query?: {
                archived?: boolean;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Задание найдено */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelHomeworkResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Задание не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelHomeworkResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateHomework: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateHomeworkRequest"];
            };
        };
        responses: {
            /** @description Задание обновлено */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelHomeworkResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelHomeworkResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelHomeworkResponse"];
                };
            };
            /** @description Задание не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelHomeworkResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deleteHomework: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Задание удалено */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Задание не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getGroup: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Группа найдена */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Группа не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateGroup: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateGroupRequest"];
            };
        };
        responses: {
            /** @description Группа обновлена */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Группа не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deleteGroup: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Группа удалена */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Группа не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Группа содержит студентов */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    assignHeadman: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AssignHeadmanRequest"];
            };
        };
        responses: {
            /** @description Староста назначен */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanAssignmentResponse"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Группа или студент не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Устаревшее состояние или конфликт назначения */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listUsers: {
        parameters: {
            query: {
                /** @description Case-insensitive поиск по login / ФИО / telegramId (contains). BUG-006-1. */
                search?: string;
                role?: string;
                status?: "ACTIVE" | "EXPELLED" | "SUSPENDED" | "ARCHIVED";
                roleStatus?: string;
                /** @description Положительный ID группы в сохранённой связи STUDENT/HEADMAN. Не состав на дату; неизвестный ID даёт пустой список. */
                groupId?: number;
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerUserResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список пользователей */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelUserResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createUser: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateUserRequest"];
            };
        };
        responses: {
            /** @description Пользователь создан */
            201: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserCreatedResponse"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    transferStudent: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TransferStudentRequest"];
            };
        };
        responses: {
            /** @description Студент переведён */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Пользователь или группа не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    restoreUser: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RestoreRequest"];
            };
        };
        responses: {
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Операция завершена */
            204: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    archiveUserProtected: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ArchiveRequest"];
            };
        };
        responses: {
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Операция завершена */
            204: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listSubjects: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerSubjectResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список предметов */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelSubjectResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createSubject: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSubjectRequest"];
            };
        };
        responses: {
            /** @description Предмет создан */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    addTeacher: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                teacherId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AddSubjectTeacherRequest"];
            };
        };
        responses: {
            /** @description Преподаватель добавлен */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Предмет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Преподаватель уже назначен */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    removeTeacher: {
        parameters: {
            query: {
                assignmentId: number;
                validUntilExclusive: string;
            };
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: number;
                teacherId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Закрытие подтверждено */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Назначение не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт назначения или содержимого принятого ключа */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ожидает повторения той же принятой команды */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    confirm: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConfirmRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Operation"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    challenge: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChallengeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Challenge"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listSemesters: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerSemesterResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список семестров */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelSemesterResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createSemester: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSemesterRequest"];
            };
        };
        responses: {
            /** @description Семестр создан */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    restoreSemester: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Восстановление завершено и все write gates released */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Восстановление ожидает release всех доменов */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Переход или ключ идемпотентности конфликтует */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Участник недоступен; операция сохранена для повтора */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
        };
    };
    archiveSemester: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Архивация завершена */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Архивация ожидает завершения принятых эффектов */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Переход или ключ идемпотентности конфликтует */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Участник недоступен; операция сохранена для повтора */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
        };
    };
    legacy: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["IssueReportDownloadTicketRequest"];
            };
        };
        responses: {
            /** @description Report download ticket issued */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReportDownloadTicketResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Missing or invalid access token */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReportDownloadTicketResponse"];
                };
            };
            /** @description No current selectable report session */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReportDownloadTicketResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ticket issuance limit exceeded */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReportDownloadTicketResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Current authority or ticket store unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    users: {
        parameters: {
            query?: never;
            header?: {
                "X-Export-Job-Id"?: string;
                "X-Export-Job-Lease"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Users"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    groups: {
        parameters: {
            query?: never;
            header?: {
                "X-Export-Job-Id"?: string;
                "X-Export-Job-Lease"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Groups"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    uploadPlan: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    buildingCode: string;
                    floorCode: string;
                    label?: string;
                    /** Format: binary */
                    png?: string;
                    /** Format: binary */
                    svg?: string;
                };
            };
        };
        responses: {
            /** @description Текущая схема заменена новой версией */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapUploadPlanResponse"];
                };
            };
            /** @description Первая схема опубликована */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapUploadPlanResponse"];
                };
            };
            /** @description Некорректные коды, название или файл; каталог не изменён */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapUploadPlanResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нужна роль ADMIN и право CAMPUS_MAP_MANAGE */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapUploadPlanResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Код принадлежит неактивному корпусу или этажу, либо конфликт публикации */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapUploadPlanResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deletePlan: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PlanDeleteRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PlanDeletionResult"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listFloors: {
        parameters: {
            query?: {
                buildingId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Ordered floor inventory */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapFloor"][];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createFloor: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CampusMapCreateFloorRequest"];
            };
        };
        responses: {
            /** @description Этаж создан */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapFloor"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нужна роль ADMIN */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapFloor"];
                };
            };
            /** @description Корпус не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapFloor"];
                };
            };
            /** @description Этаж с таким кодом уже существует */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapFloor"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    uploadVersion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                floorId: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    label?: string;
                    /** Format: binary */
                    png?: string;
                    /** Format: binary */
                    svg?: string;
                };
            };
        };
        responses: {
            /** @description Версия опубликована; отсутствующий формат остаётся explicit absent */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapPlan"];
                };
            };
            /** @description Файл не прошёл MIME/signature/SVG validation */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapPlan"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нужна роль ADMIN */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapPlan"];
                };
            };
            /** @description Этаж не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapPlan"];
                };
            };
            /** @description Конфликт публикации */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapPlan"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deleteFloor: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                floorId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeleteRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DeletionResult"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listBuildings: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Ordered campus map inventory */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapBuilding"][];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createBuilding: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CampusMapCreateBuildingRequest"];
            };
        };
        responses: {
            /** @description Корпус создан */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapBuilding"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нужна роль ADMIN */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapBuilding"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Код корпуса уже используется */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapBuilding"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deleteBuilding: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                buildingId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeleteRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DeletionResult"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    reconcileGroupOperation: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ReconcileRequest"];
            };
        };
        responses: {
            /** @description Операция завершена */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listHomeworks: {
        parameters: {
            query: {
                groupId: number;
                semesterId: number;
                archived?: boolean;
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerHomeworkResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список заданий */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelHomeworkResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createHomework: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateHomeworkRequest"];
            };
        };
        responses: {
            /** @description Задание создано */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description Задание сохранено, привязка ожидает подтверждения; повторить с тем же requestKey */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HomeworkPublicationPendingResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Schedule временно недоступен до сохранения содержимого */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": Record<string, never>;
                };
            };
        };
    };
    markComplete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Задание отмечено выполненным */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Задание не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Задание уже выполнено */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    unmarkComplete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Отметка снята */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Задание или отметка не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listGroups: {
        parameters: {
            query: {
                active?: boolean;
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerGroupResponse"];
                status?: "ACTIVE" | "ARCHIVED" | "ALL";
                search?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список групп */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelGroupResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createGroup: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateGroupRequest"];
            };
        };
        responses: {
            /** @description Группа создана */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Группа с таким кодом уже существует */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    restoreGroup: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Группа восстановлена или уже активна */
            204: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Группа не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Имя или код группы заняты либо архивное имя не распознано */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    confirmGroupDeletion: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConfirmAdminGroupDeletionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupDeletionOperation"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    resolveGroupDeletion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupDeletionOperation"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listAdminGroups: {
        parameters: {
            query: {
                status?: "ACTIVE" | "DRAFT" | "ARCHIVED";
                search?: string;
                course?: number;
                studyMode?: "FULL_TIME" | "PART_TIME" | "EXTRAMURAL";
                educationLevel?: "BACHELOR" | "SPECIALIST" | "MASTER";
                pageable: components["schemas"]["Pageable"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Страница реестра групп */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupRegistryResponse"];
                };
            };
            /** @description Некорректный фильтр или сортировка */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupRegistryResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupRegistryResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createAdminGroup: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateAdminGroupRequest"];
            };
        };
        responses: {
            /** @description Группа создана как черновик */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Код группы уже существует */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    promote: {
        parameters: {
            query?: never;
            header?: {
                /** @description Необязательный стабильный канонический UUID попытки. Повторять ту же команду с тем же ключом; без ключа сервер выдаёт X-Operation-Id. */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PromotionExecuteRequest"];
            };
        };
        responses: {
            /** @description Результат промоушена */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PromotionSummary"];
                };
            };
            /** @description Операция сохранена; проверьте Location перед повтором команды с прежним ключом */
            202: {
                headers: {
                    "Cache-Control"?: string;
                    /** @description Задержка перед проверкой состояния в секундах */
                    "Retry-After"?: number;
                    /** @description UUID сохранённой операции */
                    "X-Operation-Id"?: string;
                    /** @description Адрес проверки состояния операции */
                    Location?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Весенний семестр или группа не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Предпросмотр устарел или группа уже обработана */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    promotePreview: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PromotionPreviewRequest"];
            };
        };
        responses: {
            /** @description План промоушена */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PromotionSummary"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PromotionSummary"];
                };
            };
            /** @description Группа не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PromotionSummary"];
                };
            };
            /** @description Нет завершённого весеннего семестра */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PromotionSummary"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Create"];
            };
        };
        responses: {
            /** @description Durable report accepted; poll the returned job until ready or error */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Status"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Current authority or durable export storage unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    ticket: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReportDownloadTicketResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listAssistants: {
        parameters: {
            query: {
                groupId: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список помощников */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CollectionModelEntityModelAssistantResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    assignAssistant: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AssignAssistantRequest"];
            };
        };
        responses: {
            /** @description Помощник назначен */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Студент или группа не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Студент уже является помощником в этой группе */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listAssignments: {
        parameters: {
            query: {
                groupId: number;
                semesterId: number;
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerAssignmentResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список назначений */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelAssignmentResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    assignTeacher: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AssignTeacherRequest"];
            };
        };
        responses: {
            /** @description Назначение создано */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Преподаватель/предмет/группа/семестр не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Назначение уже существует */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    resumeAdoption: {
        parameters: {
            query?: {
                operationId?: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Назначение подтверждено */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Назначение ещё ожидает подтверждения */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssignmentResponse"];
                };
            };
        };
    };
    replaceAssignment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReplaceAssignmentRequest"];
            };
        };
        responses: {
            /** @description Замена подготовлена и применена */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AssignmentReplacementResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет права старосты */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AssignmentReplacementResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт назначения или операции */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AssignmentReplacementResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    generate: {
        parameters: {
            query?: never;
            header: {
                "X-Export-Job-Id": string;
                "X-Export-Job-Lease": string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Export"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateMyAvatar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateAvatarRequest"];
            };
        };
        responses: {
            /** @description Аватар обновлён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Неверный avatarId */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    activateSemester: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Семестр активирован или активация запланирована */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Завершённый диапазон, пересечение дат или переход состояния */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updatePermissions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateAssistantPermissionsRequest"];
            };
        };
        responses: {
            /** @description Права обновлены */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Помощник не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelAssistantResponse"];
                };
            };
            /** @description Помощник отозван или права изменились; extras.code=ASSISTANT_PERMISSIONS_CONFLICT при несовпадении снимка */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    previewUserArchive: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Preview"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listTeachers: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список преподавателей */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CollectionModelEntityModelUserResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    searchTeachers: {
        parameters: {
            query: {
                /** @description Фрагмент ФИО или табельного номера */
                search?: string;
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerTeacherLookupResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Страница преподавателей */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelTeacherLookupResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelTeacherLookupResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getMe: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Профиль получен */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getUsersByIds: {
        parameters: {
            query: {
                /** @description Список ID пользователей через запятую (1..100 элементов) */
                ids: number[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список найденных профилей (порядок не гарантируется) */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CollectionModelEntityModelUserSummaryResponse"];
                };
            };
            /** @description Превышен лимит 100 ids или ids пуст */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CollectionModelEntityModelUserSummaryResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listThresholds: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerThresholdResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список порогов */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelThresholdResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    resolveThreshold: {
        parameters: {
            query?: {
                groupId?: number;
                subjectId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Эффективный порог */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelResolvedThresholdResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    preview: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Preview"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    status: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Operation"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    previewSemesterDeletion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterDeletionPreviewResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getSemesterArchiveStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Состояние прочитано */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveStatusResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveStatusResponse"];
                };
            };
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveStatusResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    checkOverlap: {
        parameters: {
            query: {
                from: string;
                to: string;
                excludeId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Результат проверки */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OverlapCheckResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OverlapCheckResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getSemesterDeletionOperation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterDeletionOperationResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getSemesterArchiveOperation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Операция найдена */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Операция не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SemesterArchiveOperationResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    ticketFile: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                ticket: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Report artifact redeemed by its short-lived ticket */
            200: {
                headers: {
                    "Cache-Control"?: "no-store";
                    /** @description Attachment filename */
                    "Content-Disposition"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": string;
                    "application/pdf": string;
                    "application/zip": string;
                    "image/png": string;
                    "text/html": string;
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": string;
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    userFormats: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["FormatOption"][];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    groupFormats: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["FormatOption"][];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getPlanTarget: {
        parameters: {
            query: {
                buildingCode: string;
                floorCode: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PlanTargetResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    previewPlanDeletion: {
        parameters: {
            query: {
                buildingCode: string;
                floorCode: string;
                format?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PlanDeletionPreview"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getVersion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                floorId: string;
                version: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Version metadata */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapPlan"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Версия не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CampusMapPlan"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    downloadAsset: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                floorId: string;
                version: string;
                format: string;
                assetId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Validated asset bytes */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ассет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Ассет не готов */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    previewFloorDeletion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                floorId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DeletionPreview"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    previewBuildingDeletion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                buildingId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DeletionPreview"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getIdentityOperation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Текущее сохранённое состояние операции */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    "X-Operation-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IdentityOperationStatus"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется действующая сессия */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Операция не найдена или недоступна текущему пользователю */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальное состояние зависимости недоступно; исход операции нельзя считать отклонённым */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getArchiveReconciliationPreview: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Текущее подтверждение архивирования */
            200: {
                headers: {
                    "Cache-Control"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Preview"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет актуального права USERS_ARCHIVE */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Операция не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет подтверждённого failed-safe архивирования */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Актуальный snapshot недоступен */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    history: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PageHomeworkHistoryResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getHeadmanRoster: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Состав группы */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanRosterResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanRosterResponse"];
                };
            };
            /** @description Группа не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanRosterResponse"];
                };
            };
            /** @description Группа архивна или содержит конфликтующие данные */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanRosterResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    previewHeadman: {
        parameters: {
            query: {
                studentId: number;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Предпросмотр */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanAssignmentPreviewResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanAssignmentPreviewResponse"];
                };
            };
            /** @description Группа или студент не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanAssignmentPreviewResponse"];
                };
            };
            /** @description Студент не состоит в группе */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HeadmanAssignmentPreviewResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    previewGroupDeletion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupDeletionPreview"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    options: {
        parameters: {
            query?: {
                search?: string;
                page?: number;
                size?: number;
                includeArchived?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["GroupOptionsResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getMyGroupMembers: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerUserResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список участников */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelUserResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelUserResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getGroupDeletionStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminGroupDeletionOperation"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    status_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Status"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    file: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Current authorized report artifact */
            200: {
                headers: {
                    "Cache-Control"?: "no-store";
                    /** @description Attachment filename */
                    "Content-Disposition"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": string;
                    "application/pdf": string;
                    "application/zip": string;
                    "image/png": string;
                    "text/html": string;
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": string;
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getStats: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Статистика получена */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelDashboardStatsResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelDashboardStatsResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getActivity: {
        parameters: {
            query?: {
                period?: string;
                bucket?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DashboardActivityResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listPermissionCatalog: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Допустимые права и подписи */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AssistantPermissionOption"][];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listMyPermissionCatalog: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Фактически разрешённые действия */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AssistantPermissionOption"][];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getReplacementStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Состояние durable operation */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AssignmentReplacementResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getMyAssignments: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerAssignmentResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список назначений */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelAssignmentResponse"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelAssignmentResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    read: {
        parameters: {
            query: {
                semesterId: string;
                search?: string;
                sort?: "GROUP_NAME" | "PRESENT_PERCENT" | "PRESENT_OR_EXCUSED_PERCENT" | "COUNTED_LESSONS" | "PRESENT" | "ABSENT" | "EXCUSED";
                direction?: "ASC" | "DESC";
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Page"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    semesters: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Semesters"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    formats: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["FormatOption"][];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    me: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Profile"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    catalog: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Catalog"];
                };
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Ресурс не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    revokeAssistant: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Помощник отозван */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Помощник не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    removeAssignment: {
        parameters: {
            query: {
                validUntilExclusive: string;
            };
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Закрытие подтверждено обоими сервисами */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Ошибка валидации запроса */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Требуется аутентификация */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Назначение не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Конфликт назначения или содержимого принятого ключа */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Внутренняя ошибка сервера */
            500: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Принятое закрытие ожидает повторения той же команды */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
