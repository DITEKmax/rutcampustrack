// AUTO-GENERATED — do not edit by hand.
// Regenerate: npm run generate:types (requires backend running).
// Source: docs/openapi/attendance.json (committed for CI drift-guard).

export interface paths {
    "/attendance/lessons/{lessonId}/students/{userId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Ручная отметка посещаемости
         * @description Староста устанавливает статус посещаемости для студента на паре.
         */
        put: operations["mark"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/marks/batch": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Пакетная отметка посещаемости (M05 P2-10/4)
         * @description Староста отмечает N студентов одним запросом. Pseudo-atomic: authorization-check'и выполняются до любого write'а. При любой ошибке авторизации весь batch отклонён.
         */
        post: operations["markBatch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/late-checkin/{requestId}/decision": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Решение старосты по запросу (веб-канал)
         * @description Староста подтверждает или отклоняет запрос из веб-клиента. Эквивалент нажатия inline-кнопки в Telegram. Идемпотентно: повторный вызов для уже решённого запроса возвращает 200 без побочных эффектов.
         */
        post: operations["decideRequest"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/late-checkin/{lessonId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Попросить старосту отметить
         * @description Студент создаёт запрос на подтверждение присутствия на активной паре. Староста получает уведомление в Telegram (если привязан) и в веб-кабинете.
         */
        post: operations["createRequest"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/excuses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Создать тикет о пропуске
         * @description STUDENT создаёт тикет со списком lessonIds своей группы, причиной и комментарием.
         */
        post: operations["createExcuse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/excuses/with-file": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Создать тикет о пропуске с файлом
         * @description STUDENT создаёт тикет и прикладывает документ (до 10 МБ). Файл пересылается старосте через notification-bot и не сохраняется на сервере.
         */
        post: operations["createExcuseWithFile"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/checkin": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Геоотметка студента
         * @description Студент отправляет координаты. Система проверяет геофенс, активную пару и временное окно.
         */
        post: operations["checkin"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/excuses/{id}/status": {
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
         * Одобрить/отклонить тикет
         * @description Староста (STUDENT + is_headman) принимает решение по тикету своей группы (D-07). Запрещено решать собственный тикет (D-13) и повторно менять решение (D-18).
         */
        patch: operations["updateStatus"];
        trace?: never;
    };
    "/attendance/reports/student/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student attendance stats */
        get: operations["getStudentStats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/reports/student/records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student attendance records */
        get: operations["getStudentRecords"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/reports/student/dashboard": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Aggregated dashboard for the student PWA/Mini-App home screen (v9.0): overall %, donut breakdown, weekly timeseries, top missed subjects */
        get: operations["getStudentDashboard"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/reports/lesson/{lessonId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lesson attendance list */
        get: operations["getLessonAttendance"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/reports/journal": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Journal grid (students x dates) */
        get: operations["getJournal"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/late-checkin/pending": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Список ожидающих запросов группы (для старосты)
         * @description Возвращает запросы со статусом PENDING, принадлежащие группе старосты. Доступно только пользователю с ролью STUDENT и is_headman=true.
         */
        get: operations["listPending"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/health-check": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["healthCheck"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/excuses/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Детали тикета
         * @description STUDENT — только свой; headman — только своей группы (D-08).
         */
        get: operations["getTicketById"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/excuses/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Мои тикеты
         * @description STUDENT видит свои тикеты, сортировка по createdAt desc (D-05).
         */
        get: operations["getMyTickets"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/excuses/group/{groupId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Тикеты группы
         * @description Староста (STUDENT + is_headman) видит тикеты своей группы (D-06).
         */
        get: operations["getGroupTickets"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        MarkRequest: {
            /** @enum {string} */
            status: "PRESENT" | "ABSENT" | "EXCUSED" | "FREE_ATTENDANCE" | "CANCELLED";
        };
        /** @description Стандартный формат ошибки API */
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
             * @example Запись с id=99 не найдена
             */
            detail?: string;
            /**
             * @description URI запроса, вызвавшего ошибку
             * @example /api/attendance/checkins/99
             */
            instance?: string;
            /**
             * Format: date-time
             * @description Время возникновения ошибки
             */
            timestamp?: string;
            /** @description Ошибки валидации полей (только для 400) */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        FieldError: {
            /**
             * @description Имя поля
             * @example lessonId
             */
            field?: string;
            /** @description Отклонённое значение */
            rejectedValue?: Record<string, never>;
            /**
             * @description Сообщение об ошибке
             * @example Поле не может быть пустым
             */
            message?: string;
        };
        EntityModelMarkResponse: {
            /** @enum {string} */
            status?: "PRESENT" | "ABSENT" | "EXCUSED" | "FREE_ATTENDANCE" | "CANCELLED";
            /** Format: int64 */
            lessonId?: number;
            /** Format: int64 */
            userId?: number;
            /** Format: date-time */
            timestamp?: string;
            _links?: components["schemas"]["Links"];
        };
        MarkResponse: {
            /** @enum {string} */
            status?: "PRESENT" | "ABSENT" | "EXCUSED" | "FREE_ATTENDANCE" | "CANCELLED";
            /** Format: int64 */
            lessonId?: number;
            /** Format: int64 */
            userId?: number;
            /** Format: date-time */
            timestamp?: string;
            _links?: components["schemas"]["Links"];
        };
        MarkBatchItem: {
            /** Format: int64 */
            lessonId: number;
            /** Format: int64 */
            userId: number;
            /** @enum {string} */
            status: "PRESENT" | "ABSENT" | "EXCUSED" | "FREE_ATTENDANCE" | "CANCELLED";
        };
        MarkBatchRequest: {
            items: components["schemas"]["MarkBatchItem"][];
        };
        MarkBatchResponse: {
            items?: components["schemas"]["MarkResponse"][];
            /** Format: int32 */
            processed?: number;
            _links?: components["schemas"]["Links"];
        };
        /** @description Решение старосты по запросу поздней отметки */
        LateCheckinDecisionRequest: {
            /** @description true — подтвердить присутствие, false — отклонить */
            approved: boolean;
        };
        EntityModelLateCheckinRequestResponse: {
            id?: string;
            /** Format: int64 */
            studentId?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            lessonId?: number;
            studentName?: string;
            status?: string;
            /** Format: int64 */
            decisionBy?: number;
            /** Format: date-time */
            decisionAt?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
            _links?: components["schemas"]["Links"];
        };
        CreateExcuseRequest: {
            lessonIds?: number[];
            /** @enum {string} */
            excuseType: "ILLNESS" | "SUMMONS" | "UNIVERSITY_ORDER" | "EXEMPTION" | "FREE_ATTENDANCE" | "OTHER";
            comment?: string;
        };
        EntityModelExcuseTicketResponse: {
            id?: string;
            /** Format: int64 */
            studentId?: number;
            /** Format: int64 */
            groupId?: number;
            studentName?: string;
            lessonIds?: number[];
            /** @enum {string} */
            excuseType?: "ILLNESS" | "SUMMONS" | "UNIVERSITY_ORDER" | "EXEMPTION" | "FREE_ATTENDANCE" | "OTHER";
            comment?: string;
            status?: string;
            /** Format: int64 */
            decisionBy?: number;
            decisionComment?: string;
            /** Format: date-time */
            decisionAt?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
            _links?: components["schemas"]["Links"];
        };
        CheckinRequest: {
            /** Format: double */
            lat: number;
            /** Format: double */
            lng: number;
        };
        EntityModelCheckinResponse: {
            /** @enum {string} */
            status?: "PRESENT" | "ABSENT" | "EXCUSED" | "FREE_ATTENDANCE" | "CANCELLED";
            /** Format: int64 */
            lessonId?: number;
            /** Format: date-time */
            timestamp?: string;
            _links?: components["schemas"]["Links"];
        };
        UpdateExcuseStatusRequest: {
            /** @enum {string} */
            status: "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";
            decisionComment?: string;
        };
        EntityModelStudentStatsResponse: {
            subjects?: components["schemas"]["SubjectStats"][];
            overall?: components["schemas"]["OverallStats"];
            _links?: components["schemas"]["Links"];
        };
        OverallStats: {
            /** Format: int32 */
            total?: number;
            /** Format: int32 */
            attended?: number;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            excused?: number;
            /** Format: double */
            percentage?: number;
        };
        SubjectStats: {
            /** Format: int64 */
            subjectId?: number;
            subjectName?: string;
            /** Format: int32 */
            total?: number;
            /** Format: int32 */
            attended?: number;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            excused?: number;
            /** Format: double */
            percentage?: number;
        };
        CollectionModelEntityModelAttendanceRecordEntry: {
            _embedded?: {
                attendanceRecordEntryList?: components["schemas"]["EntityModelAttendanceRecordEntry"][];
            };
            _links?: components["schemas"]["Links"];
        };
        EntityModelAttendanceRecordEntry: {
            /** Format: int64 */
            lessonId?: number;
            /** Format: int64 */
            subjectId?: number;
            lessonDate?: string;
            /** Format: int32 */
            lessonNumber?: number;
            status?: string;
            symbol?: string;
            source?: string;
            excuseReason?: string;
            _links?: components["schemas"]["Links"];
        };
        EntityModelStudentDashboardResponse: {
            overall?: components["schemas"]["OverallStats"];
            breakdown?: components["schemas"]["StatusBreakdown"];
            weekly?: components["schemas"]["WeeklyStat"][];
            topMissed?: components["schemas"]["TopMissedSubject"][];
            _links?: components["schemas"]["Links"];
        };
        StatusBreakdown: {
            /** Format: int32 */
            present?: number;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            excused?: number;
            /** Format: int32 */
            freeAttendance?: number;
            /** Format: int32 */
            forgotToCheckin?: number;
            /** Format: int32 */
            cancelled?: number;
        };
        TopMissedSubject: {
            /** Format: int64 */
            subjectId?: number;
            subjectName?: string;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            total?: number;
        };
        WeeklyStat: {
            /** Format: int32 */
            weekOfSemester?: number;
            /** Format: int32 */
            isoWeek?: number;
            label?: string;
            /** Format: int32 */
            total?: number;
            /** Format: int32 */
            attended?: number;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            excused?: number;
            /** Format: double */
            percentage?: number;
        };
        EntityModelLessonAttendanceResponse: {
            /** Format: int64 */
            lessonId?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            subjectId?: number;
            lessonDate?: string;
            entries?: components["schemas"]["StudentAttendanceEntry"][];
            _links?: components["schemas"]["Links"];
        };
        StudentAttendanceEntry: {
            /** Format: int64 */
            userId?: number;
            displayName?: string;
            status?: string;
            symbol?: string;
            source?: string;
            excuseReason?: string;
        };
        EntityModelJournalResponse: {
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            subjectId?: number;
            dates?: string[];
            students?: components["schemas"]["JournalStudentRow"][];
            _links?: components["schemas"]["Links"];
        };
        JournalCell: {
            /** Format: int64 */
            lessonId?: number;
            date?: string;
            /** Format: int32 */
            lessonNumber?: number;
            status?: string;
            symbol?: string;
            excuseReason?: string;
        };
        JournalStudentRow: {
            /** Format: int64 */
            userId?: number;
            displayName?: string;
            records?: components["schemas"]["JournalCell"][];
        };
        CollectionModelEntityModelLateCheckinRequestResponse: {
            _embedded?: {
                lateCheckinRequestResponseList?: components["schemas"]["EntityModelLateCheckinRequestResponse"][];
            };
            _links?: components["schemas"]["Links"];
        };
        Pageable: {
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            sort?: string[];
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
        PagedModelEntityModelExcuseTicketResponse: {
            _embedded?: {
                excuseTicketResponseList?: components["schemas"]["EntityModelExcuseTicketResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
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
    mark: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                lessonId: number;
                userId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MarkRequest"];
            };
        };
        responses: {
            /** @description Статус успешно установлен */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelMarkResponse"];
                };
            };
            /** @description Неверный статус или запрос */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён — не старosta */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Пара или студент не найдены */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    markBatch: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MarkBatchRequest"];
            };
        };
        responses: {
            /** @description Все отметки сохранены */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["MarkBatchResponse"];
                };
            };
            /** @description Неверный запрос (пустой batch, размер > 100, CANCELLED status) */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Доступ запрещён — не староста, пара чужой группы или студент не в группе */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Пара не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    decideRequest: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                requestId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LateCheckinDecisionRequest"];
            };
        };
        responses: {
            /** @description Решение применено */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLateCheckinRequestResponse"];
                };
            };
            /** @description Тело запроса невалидно */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Только староста той же группы может решать */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Запрос не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createRequest: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                lessonId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Запрос создан */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLateCheckinRequestResponse"];
                };
            };
            /** @description Старосты не создают запросы (самоотметка через журнал) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Занятие не найдено или не принадлежит группе студента */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Запрос на этот урок уже существует или студент уже отмечен */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Пара уже не активна */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createExcuse: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateExcuseRequest"];
            };
        };
        responses: {
            /** @description Тикет создан */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelExcuseTicketResponse"];
                };
            };
            /** @description Некорректный запрос */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Староста не создаёт тикеты (D-12) или доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description На один из уроков уже есть активный тикет (D-11) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createExcuseWithFile: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    request: components["schemas"]["CreateExcuseRequest"];
                    /** Format: binary */
                    file?: string;
                };
            };
        };
        responses: {
            /** @description Тикет создан, файл поставлен в очередь на отправку */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelExcuseTicketResponse"];
                };
            };
            /** @description Некорректный запрос или файл */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Староста не создаёт тикеты (D-12) или доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description На один из уроков уже есть активный тикет (D-11) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Файл больше 10 МБ */
            413: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    checkin: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CheckinRequest"];
            };
        };
        responses: {
            /** @description Отметка успешно поставлена */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelCheckinResponse"];
                };
            };
            /** @description Геоотметка заблокирована для данной пары */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Активная пара не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Отметка уже существует */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Координаты вне зоны геофенса */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Превышен лимит запросов */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateExcuseStatusRequest"];
            };
        };
        responses: {
            /** @description Решение принято */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelExcuseTicketResponse"];
                };
            };
            /** @description Некорректный статус (только APPROVED или REJECTED) */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Не староста или чужая группа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Тикет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Свой тикет (D-13) или решение уже принято (D-18) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getStudentStats: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Stats retrieved */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelStudentStatsResponse"];
                };
            };
        };
    };
    getStudentRecords: {
        parameters: {
            query?: {
                subjectId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Records retrieved */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CollectionModelEntityModelAttendanceRecordEntry"];
                };
            };
        };
    };
    getStudentDashboard: {
        parameters: {
            query?: {
                topLimit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Dashboard retrieved */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelStudentDashboardResponse"];
                };
            };
        };
    };
    getLessonAttendance: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                lessonId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Attendance list retrieved */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonAttendanceResponse"];
                };
            };
            /** @description Access denied */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Lesson not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getJournal: {
        parameters: {
            query: {
                groupId: number;
                subjectId: number;
                dateFrom: string;
                dateTo: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Journal retrieved */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelJournalResponse"];
                };
            };
            /** @description Access denied */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listPending: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список запросов */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CollectionModelEntityModelLateCheckinRequestResponse"];
                };
            };
            /** @description Только староста группы */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    healthCheck: {
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
                    "*/*": string;
                };
            };
        };
    };
    getTicketById: {
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
            /** @description Тикет найден */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelExcuseTicketResponse"];
                };
            };
            /** @description Доступ запрещён (D-14) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Тикет не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getMyTickets: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
                status?: "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список тикетов */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelExcuseTicketResponse"];
                };
            };
            /** @description Доступ запрещён */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getGroupTickets: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
                status?: "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";
            };
            header?: never;
            path: {
                groupId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список тикетов группы */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelExcuseTicketResponse"];
                };
            };
            /** @description Не староста или чужая группа (D-14) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
}
