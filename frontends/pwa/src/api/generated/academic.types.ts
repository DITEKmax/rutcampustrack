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
        /** Архивировать пользователя (soft delete). Только ADMIN. */
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
        /** Полное обновление предмета (PUT, HEADMAN/ADMIN) */
        put: operations["updateSubject"];
        post?: never;
        /**
         * Удалить предмет (HEADMAN/ADMIN)
         * @description Если у предмета есть уроки с историей посещаемости, возвращается 409 со счётчиками в extras. Повторный запрос с force=true пропускает pre-check и удаляет данные каскадно.
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
         * Удалить семестр с подтверждением (ADMIN)
         * @description Требует фразу подтверждения для защиты от случайного удаления активного семестра.
         */
        delete: operations["deleteSemester"];
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
        /** Полное обновление задания (PUT, HEADMAN, только автор) */
        put: operations["updateHomework"];
        post?: never;
        /** Удалить задание (HEADMAN, только автор) */
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
        /** Создать предмет (HEADMAN/ADMIN) */
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
        /** Добавить преподавателя к предмету (HEADMAN/ADMIN) */
        post: operations["addTeacher"];
        /** Удалить преподавателя из предмета (HEADMAN/ADMIN) */
        delete: operations["removeTeacher"];
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
        /** Создать домашнее задание (HEADMAN) */
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
        /** Назначить преподавателя на предмет/группу/семестр (HEADMAN/ADMIN) */
        post: operations["assignTeacher"];
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
         * Активировать семестр (ADMIN)
         * @description Деактивирует все остальные семестры и активирует указанный.
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
        /** Обновить права помощника (HEADMAN/ADMIN) */
        patch: operations["updatePermissions"];
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
    "/academic/dashboard/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Сводная статистика системы (ADMIN)
         * @description Возвращает количество пользователей, групп, активный семестр и другие ключевые показатели.
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
        /** Удалить назначение (HEADMAN/ADMIN) */
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
        UpdateUserRequest: {
            lastName?: string;
            firstName?: string;
            middleName?: string;
            /** @enum {string} */
            role: "ADMIN" | "TEACHER" | "STUDENT";
            /** Format: int64 */
            groupId?: number;
            employeeNumber?: string;
            /** Format: int64 */
            telegramId?: number;
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
            fullName?: string;
            _links?: components["schemas"]["Links"];
        };
        SetThresholdRequest: {
            /** Format: int32 */
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
        UpdateSubjectRequest: {
            name?: string;
            /** @enum {string} */
            type: "LECTURE" | "PRACTICE" | "LAB";
        };
        EntityModelSubjectResponse: {
            /** Format: int64 */
            id?: number;
            name?: string;
            /** @enum {string} */
            type?: "LECTURE" | "PRACTICE" | "LAB";
            /** Format: int64 */
            groupId?: number;
            teacherIds?: number[];
            /** Format: date-time */
            createdAt?: string;
            _links?: components["schemas"]["Links"];
        };
        UpdateSemesterRequest: {
            name?: string;
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
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
            /** Format: date-time */
            createdAt?: string;
            _links?: components["schemas"]["Links"];
        };
        UpdateHomeworkRequest: {
            title?: string;
            description?: string;
            link?: string;
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
            completed?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date */
            lessonDate?: string;
            /** Format: int32 */
            lessonNumber?: number;
            _links?: components["schemas"]["Links"];
        };
        UpdateGroupRequest: {
            name?: string;
            active?: boolean;
        };
        EntityModelGroupResponse: {
            /** Format: int64 */
            id?: number;
            name?: string;
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
            _links?: components["schemas"]["Links"];
        };
        CreateUserRequest: {
            lastName?: string;
            firstName?: string;
            middleName?: string;
            /** @enum {string} */
            role: "ADMIN" | "TEACHER" | "STUDENT";
            /** Format: int64 */
            groupId?: number;
            employeeNumber?: string;
            /** Format: int64 */
            telegramId?: number;
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
            initialPassword?: string;
            _links?: components["schemas"]["Links"];
        };
        TransferStudentRequest: {
            /** Format: int64 */
            newGroupId: number;
            reason?: string;
        };
        CreateSubjectRequest: {
            name?: string;
            /** @enum {string} */
            type: "LECTURE" | "PRACTICE" | "LAB";
            teacherIds: number[];
        };
        CreateSemesterRequest: {
            name?: string;
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
        };
        CreateHomeworkRequest: {
            title?: string;
            description?: string;
            link?: string;
            /** Format: int64 */
            subjectId: number;
            /** Format: int64 */
            groupId: number;
            /** Format: int64 */
            semesterId: number;
            /** Format: date */
            lessonDate: string;
            /** Format: int32 */
            lessonNumber: number;
        };
        CreateGroupRequest: {
            name?: string;
        };
        PrefixConflict: {
            prefix?: string;
            reason?: string;
            message?: string;
            groupIds?: number[];
        };
        PromotionPreviewItem: {
            /** Format: int64 */
            id?: number;
            from?: string;
            to?: string;
            /** @enum {string} */
            action?: "PROMOTE" | "ARCHIVE";
        };
        PromotionSummary: {
            toPromote?: components["schemas"]["PromotionPreviewItem"][];
            toArchive?: components["schemas"]["PromotionPreviewItem"][];
            conflicts?: components["schemas"]["PrefixConflict"][];
            dryRun?: boolean;
            executed?: boolean;
            _links?: components["schemas"]["Links"];
        };
        AssignAssistantRequest: {
            /** Format: int64 */
            studentId: number;
            /** Format: int64 */
            groupId: number;
            permissions?: ("MARK_ATTENDANCE" | "MANAGE_EXCUSES" | "MANAGE_HOMEWORK" | "CANCEL_LESSONS" | "VIEW_STATS")[];
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
        AssignTeacherRequest: {
            employeeNumber?: string;
            /** Format: int64 */
            subjectId: number;
            /** Format: int64 */
            groupId: number;
            /** Format: int64 */
            semesterId: number;
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
            _links?: components["schemas"]["Links"];
        };
        PatchUserRequest: {
            lastName?: string;
            firstName?: string;
            middleName?: string;
            isHeadman?: boolean;
            /** Format: int64 */
            groupId?: number;
            employeeNumber?: string;
            /** Format: int64 */
            telegramId?: number;
            /** @enum {string} */
            status?: "ACTIVE" | "EXPELLED" | "SUSPENDED" | "ARCHIVED";
        };
        UpdateAvatarRequest: {
            avatarId?: string;
        };
        UpdateAssistantPermissionsRequest: {
            permissions?: ("MARK_ATTENDANCE" | "MANAGE_EXCUSES" | "MANAGE_HOMEWORK" | "CANCEL_LESSONS" | "VIEW_STATS")[];
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
        CollectionModelEntityModelUserResponse: {
            _embedded?: {
                userResponseList?: components["schemas"]["EntityModelUserResponse"][];
            };
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
        OverlapCheckResponse: {
            overlaps?: boolean;
            conflictingName?: string;
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
            _links?: components["schemas"]["Links"];
        };
        CollectionModelEntityModelAssistantResponse: {
            _embedded?: {
                assistantResponseList?: components["schemas"]["EntityModelAssistantResponse"][];
            };
            _links?: components["schemas"]["Links"];
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
        DeleteSemesterRequest: {
            confirmation?: string;
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
        };
    };
    updateUser: {
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
                "application/json": components["schemas"]["UpdateUserRequest"];
            };
        };
        responses: {
            /** @description Пользователь обновлён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
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
            /** @description Пользователь архивирован */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Пользователь не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    patchUser: {
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
                "application/json": components["schemas"]["PatchUserRequest"];
            };
        };
        responses: {
            /** @description Пользователь обновлён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelThresholdResponse"];
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
            /** @description Предмет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Предмет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Предмет используется в расписании (есть посещаемость) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
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
        };
    };
    deleteSemester: {
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
                "application/json": components["schemas"]["DeleteSemesterRequest"];
            };
        };
        responses: {
            /** @description Семестр удалён */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Неверная фраза подтверждения */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Семестр не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    getHomework: {
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
            /** @description Задание найдено */
            200: {
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Задание не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description Группа не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
                };
            };
        };
    };
    updateGroup: {
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
                "application/json": components["schemas"]["UpdateGroupRequest"];
            };
        };
        responses: {
            /** @description Группа обновлена */
            200: {
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Группа не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Группа содержит студентов */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    listUsers: {
        parameters: {
            query: {
                /** @description Case-insensitive поиск по login / ФИО / telegramId (contains). BUG-006-1. */
                search?: string;
                role?: "ADMIN" | "TEACHER" | "STUDENT";
                status?: "ACTIVE" | "EXPELLED" | "SUSPENDED" | "ARCHIVED";
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
        };
    };
    createUser: {
        parameters: {
            query?: never;
            header?: never;
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
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserCreatedResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserCreatedResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserCreatedResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserCreatedResponse"];
                };
            };
        };
    };
    transferStudent: {
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
                "application/json": components["schemas"]["TransferStudentRequest"];
            };
        };
        responses: {
            /** @description Студент переведён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
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
            /** @description Пользователь или группа не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSubjectResponse"];
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
        requestBody?: never;
        responses: {
            /** @description Преподаватель добавлен */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Предмет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Преподаватель уже назначен */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    removeTeacher: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                teacherId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Преподаватель удалён */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Назначение не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
                };
            };
        };
    };
    listHomeworks: {
        parameters: {
            query: {
                groupId: number;
                semesterId: number;
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelHomeworkResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Задание не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Задание уже выполнено */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Задание или отметка не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelGroupResponse"];
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
        };
    };
    promote: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Результат промоушена */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PromotionSummary"];
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
        };
    };
    promotePreview: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PromotionSummary"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
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
            /** @description Семестр активирован */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelSemesterResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelUserResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OverlapCheckResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelUserResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelDashboardStatsResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelAssignmentResponse"];
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
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Помощник не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    removeAssignment: {
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
            /** @description Назначение удалено */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Назначение не найдено */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
