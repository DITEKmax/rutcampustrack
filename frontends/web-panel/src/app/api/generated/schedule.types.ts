// AUTO-GENERATED — do not edit by hand.
// Regenerate: npm run generate:types (requires backend running).
// Source: docs/openapi/schedule.json (committed for CI drift-guard).

export interface paths {
    "/schedule/items/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить шаблон по ID */
        get: operations["getScheduleItem"];
        /** Полное обновление шаблона (PUT, HEADMAN/ADMIN) */
        put: operations["updateScheduleItem"];
        post?: never;
        /** Деактивировать шаблон расписания (HEADMAN/ADMIN) */
        delete: operations["deleteScheduleItem"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/schedule/one-off-lessons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список разовых пар для группы в диапазоне дат */
        get: operations["listOneOffLessons"];
        put?: never;
        /** Создать разовую пару (HEADMAN) */
        post: operations["createOneOffLesson"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/schedule/lessons/{id}/blockage": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Заблокировать пару старостой — запретить геоотметку, посещаемость ставит только староста вручную (HEADMAN) */
        post: operations["blockLesson"];
        /** Снять блокировку пары старостой (HEADMAN) */
        delete: operations["unblockLesson"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/schedule/lessons/mass-cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Массовая отмена уроков для группы (HEADMAN/ADMIN) */
        post: operations["massCancelLessons"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/schedule/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Список шаблонов расписания для группы и семестра */
        get: operations["listScheduleItems"];
        put?: never;
        /** Создать шаблон расписания (HEADMAN/ADMIN) */
        post: operations["createScheduleItem"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/schedule/lessons/{id}/restore": {
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
        /** Восстановить отменённый урок (HEADMAN/ADMIN) */
        patch: operations["restoreLesson"];
        trace?: never;
    };
    "/schedule/lessons/{id}/geo-block": {
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
        /** Включить/выключить гео-блокировку урока (HEADMAN/ADMIN) */
        patch: operations["toggleGeoBlock"];
        trace?: never;
    };
    "/schedule/lessons/{id}/cancel": {
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
        /** Отменить урок (HEADMAN/ADMIN). Допускается отмена PLANNED/ACTIVE/CLOSED. */
        patch: operations["cancelLesson"];
        trace?: never;
    };
    "/schedule/health-check": {
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
    "/schedule/groups/{groupId}/lessons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Получить расписание группы за диапазон дат (все роли) */
        get: operations["getLessons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/schedule/one-off-lessons/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Удалить разовую пару (HEADMAN, любая дата D-22) */
        delete: operations["deleteOneOffLesson"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        UpdateScheduleItemRequest: {
            /** Format: int64 */
            subjectId: number;
            /** Format: int32 */
            dayOfWeek: number;
            /** Format: int32 */
            lessonNumber: number;
            /** @example 14:30:00 */
            startTime: string;
            /** @example 14:30:00 */
            endTime: string;
            /** @enum {string} */
            weekType: "ALL" | "ODD" | "EVEN";
            room?: string;
        };
        EntityModelScheduleItemResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            subjectId?: number;
            /** Format: int64 */
            semesterId?: number;
            /** Format: int32 */
            dayOfWeek?: number;
            /** Format: int32 */
            lessonNumber?: number;
            /** @example 14:30:00 */
            startTime?: string;
            /** @example 14:30:00 */
            endTime?: string;
            /** @enum {string} */
            weekType?: "ALL" | "ODD" | "EVEN";
            room?: string;
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
            _links?: components["schemas"]["Links"];
        };
        CreateOneOffLessonRequest: {
            /** Format: int64 */
            groupId: number;
            /** Format: int64 */
            subjectId: number;
            /** Format: date */
            date: string;
            /** Format: int32 */
            lessonNumber: number;
            classroom?: string;
        };
        EntityModelOneOffLessonResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            subjectId?: number;
            /** Format: int64 */
            semesterId?: number;
            /** Format: date */
            date?: string;
            /** Format: int32 */
            lessonNumber?: number;
            classroom?: string;
            /** Format: int64 */
            createdBy?: number;
            /** Format: date-time */
            createdAt?: string;
            _links?: components["schemas"]["Links"];
        };
        EntityModelLessonResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            scheduleItemId?: number;
            /** Format: int64 */
            groupId?: number;
            /** Format: int64 */
            subjectId?: number;
            /** Format: date */
            date?: string;
            /** @enum {string} */
            status?: "PLANNED" | "ACTIVE" | "CLOSED" | "CANCELLED";
            /** Format: int32 */
            dayOfWeek?: number;
            /** Format: int32 */
            lessonNumber?: number;
            /** @example 14:30:00 */
            startTime?: string;
            /** @example 14:30:00 */
            endTime?: string;
            /** @enum {string} */
            weekType?: "ALL" | "ODD" | "EVEN";
            room?: string;
            geoBlocked?: boolean;
            blockedByHeadman?: boolean;
            /** Format: int64 */
            blockedByUserId?: number;
            /** Format: date-time */
            blockedAt?: string;
            cancelReason?: string;
            /** Format: date-time */
            createdAt?: string;
            _links?: components["schemas"]["Links"];
        };
        MassCancelRequest: {
            /** Format: int64 */
            groupId: number;
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            reason?: string;
        };
        MassCancelResponse: {
            /** Format: int32 */
            cancelledCount?: number;
        };
        CreateScheduleItemRequest: {
            /** Format: int64 */
            groupId: number;
            /** Format: int64 */
            subjectId: number;
            /** Format: int64 */
            semesterId: number;
            /** Format: int32 */
            dayOfWeek: number;
            /** Format: int32 */
            lessonNumber: number;
            /** @example 14:30:00 */
            startTime: string;
            /** @example 14:30:00 */
            endTime: string;
            /** @enum {string} */
            weekType: "ALL" | "ODD" | "EVEN";
            room?: string;
        };
        GeoBlockRequest: {
            blocked: boolean;
        };
        CancelLessonRequest: {
            reason?: string;
        };
        CollectionModelEntityModelOneOffLessonResponse: {
            _embedded?: {
                oneOffLessonResponseList?: components["schemas"]["EntityModelOneOffLessonResponse"][];
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
        PagedResourcesAssemblerScheduleItemResponse: {
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
        PagedModelEntityModelScheduleItemResponse: {
            _embedded?: {
                scheduleItemResponseList?: components["schemas"]["EntityModelScheduleItemResponse"][];
            };
            _links?: components["schemas"]["Links"];
            page?: components["schemas"]["PageMetadata"];
        };
        PagedResourcesAssemblerLessonResponse: {
            forceFirstAndLastRels?: boolean;
        };
        PagedModelEntityModelLessonResponse: {
            _embedded?: {
                lessonResponseList?: components["schemas"]["EntityModelLessonResponse"][];
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
    getScheduleItem: {
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
            /** @description Шаблон найден */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Шаблон не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
        };
    };
    updateScheduleItem: {
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
                "application/json": components["schemas"]["UpdateScheduleItemRequest"];
            };
        };
        responses: {
            /** @description Шаблон обновлён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Шаблон не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Конфликт данных */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Academic Service недоступен */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
        };
    };
    deleteScheduleItem: {
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
            /** @description Шаблон деактивирован */
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
            /** @description Шаблон не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    listOneOffLessons: {
        parameters: {
            query: {
                groupId: number;
                dateFrom: string;
                dateTo: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список разовых пар */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CollectionModelEntityModelOneOffLessonResponse"];
                };
            };
        };
    };
    createOneOffLesson: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateOneOffLessonRequest"];
            };
        };
        responses: {
            /** @description Разовая пара создана */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelOneOffLessonResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelOneOffLessonResponse"];
                };
            };
            /** @description Нет прав доступа (не староста этой группы) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelOneOffLessonResponse"];
                };
            };
            /** @description Слот занят активным шаблоном или дубль разовой пары */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelOneOffLessonResponse"];
                };
            };
            /** @description Academic Service недоступен */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelOneOffLessonResponse"];
                };
            };
        };
    };
    blockLesson: {
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
            /** @description Пара заблокирована */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Урок не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Нельзя заблокировать прошедшую или отменённую пару */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
        };
    };
    unblockLesson: {
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
            /** @description Блокировка снята */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Урок не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
        };
    };
    massCancelLessons: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MassCancelRequest"];
            };
        };
        responses: {
            /** @description Уроки отменены */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["MassCancelResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["MassCancelResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["MassCancelResponse"];
                };
            };
            /** @description Academic Service недоступен */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["MassCancelResponse"];
                };
            };
        };
    };
    listScheduleItems: {
        parameters: {
            query: {
                groupId: number;
                semesterId: number;
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerScheduleItemResponse"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Список шаблонов */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelScheduleItemResponse"];
                };
            };
        };
    };
    createScheduleItem: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateScheduleItemRequest"];
            };
        };
        responses: {
            /** @description Шаблон создан */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
            /** @description Academic Service недоступен */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelScheduleItemResponse"];
                };
            };
        };
    };
    restoreLesson: {
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
            /** @description Урок восстановлен */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Урок не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Недопустимый переход статуса */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
        };
    };
    toggleGeoBlock: {
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
                "application/json": components["schemas"]["GeoBlockRequest"];
            };
        };
        responses: {
            /** @description Гео-блокировка изменена */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Урок не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
        };
    };
    cancelLesson: {
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
                "application/json": components["schemas"]["CancelLessonRequest"];
            };
        };
        responses: {
            /** @description Урок отменён */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Ошибка валидации */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Нет прав доступа */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Урок не найден */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
                };
            };
            /** @description Урок уже отменён */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EntityModelLessonResponse"];
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
                    "*/*": {
                        [key: string]: string;
                    };
                };
            };
        };
    };
    getLessons: {
        parameters: {
            query: {
                dateFrom: string;
                dateTo: string;
                status?: ("PLANNED" | "ACTIVE" | "CLOSED" | "CANCELLED")[];
                pageable: components["schemas"]["Pageable"];
                assembler: components["schemas"]["PagedResourcesAssemblerLessonResponse"];
            };
            header?: never;
            path: {
                groupId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Расписание группы */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelEntityModelLessonResponse"];
                };
            };
        };
    };
    deleteOneOffLesson: {
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
            /** @description Разовая пара удалена */
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
            /** @description Разовая пара не найдена */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
