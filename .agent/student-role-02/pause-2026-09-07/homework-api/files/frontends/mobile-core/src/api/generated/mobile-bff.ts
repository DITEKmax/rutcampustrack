/* eslint-disable */
// Generated from docs/openapi/mobile-bff.json. Do not edit.
// Contract: JS-STUDENT-01-r1; SHA-256: 5c7161832192dfc72dfea6c4fa7fb1285da71bda5df7a848bf6ce2ed1b69bc75

export interface paths {
    "/api/v1/student/homework": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Student homework feed
         * @description Active-semester homework for the authenticated student's own group. Missing bounds default to Moscow today through semester end.
         */
        get: operations["getHomework"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    [path: `/api/v1/student/homework/${string}/completion`]: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Set homework completion state
         * @description Desired-state mutation for the authenticated student; repeated requests are safe.
         */
        put: operations["setHomeworkCompletion"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    [path: `/api/v1/student/lessons/${string}/checkin`]: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Attempt student geo check-in
         * @description The attendance domain owns the window, geofence, cooldown, idempotency and automatic request.
         */
        post: operations["checkin"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/schedule": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Server-scoped semester schedule for PWA offline reads */
        get: operations["getSchedule"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/session": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Current student session projection */
        get: operations["getSession"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/today": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Today projection with current or nearest lesson */
        get: operations["getToday"];
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
        Attendance: {
            /** Format: date-time */
            markedAt: string | null;
            /** @enum {string} */
            source: "STUDENT_GEO" | "LATE_CHECKIN" | "HEADMAN" | "TEACHER" | "SYSTEM";
            /** @enum {string} */
            status: "PRESENT" | "ABSENT" | "EXCUSED";
        } | null;
        AutomaticCheckinRequest: {
            id: string;
            /** @enum {string} */
            origin: "AUTO_GEO_FAILURE";
            /** @enum {string|null} */
            resolutionReason: "GEO_CONFIRMED" | "HEADMAN_APPROVED" | "HEADMAN_REJECTED" | "STUDENT_CANCELLED" | null;
            /** @enum {string} */
            status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
        } | null;
        CheckinEligibility: {
            allowed: boolean;
            /** @enum {string} */
            reason: "ELIGIBLE" | "ALREADY_PRESENT" | "LESSON_CANCELLED" | "TOO_EARLY" | "WINDOW_CLOSED" | "GEO_BLOCKED" | "PENDING_CONFIRMATION" | "COOLDOWN" | "HEADMAN_ABSENT_REQUIRES_APPEAL" | "HEADMAN_USES_JOURNAL" | "DEPENDENCY_UNAVAILABLE";
            /** Format: date-time */
            retryAt: string | null;
        };
        CoordinatesGeo: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "COORDINATES";
            /** Format: double */
            latitude: number;
            /** Format: double */
            longitude: number;
        };
        GeoInput: components["schemas"]["CoordinatesGeo"] | components["schemas"]["UnavailableGeo"];
        GroupSummary: {
            id: string;
            name: string;
        } | null;
        LessonSchedule: {
            /** Format: date */
            date: string;
            /** @example 14:30:00 */
            endsAt: string;
            id: string;
            /** Format: int32 */
            lessonNumber: number;
            room: components["schemas"]["Room"];
            /** @example 14:30:00 */
            startsAt: string;
            /** @enum {string} */
            status: "PLANNED" | "ACTIVE" | "CLOSED" | "CANCELLED";
            subject: components["schemas"]["Subject"];
        };
        Link: {
            /** Format: uri */
            href: string;
        };
        /** @description RFC 9457 Problem Details with a stable machine code */
        MobileProblemDetails: {
            /** @enum {string} */
            code: "INVALID_REQUEST" | "INVALID_IDEMPOTENCY_KEY" | "INVALID_SESSION" | "WRONG_ROLE" | "OUT_OF_SCOPE" | "HOMEWORK_NOT_FOUND" | "LESSON_NOT_FOUND" | "CHECKIN_COOLDOWN" | "MANUAL_ABSENCE_REQUIRES_APPEAL" | "IDEMPOTENCY_PAYLOAD_MISMATCH" | "CHECKIN_NOT_ELIGIBLE" | "DEPENDENCY_UNAVAILABLE";
            detail: string;
            extras?: {
                [key: string]: string | null;
            } | null;
            /** Format: uri */
            instance: string;
            /** Format: date-time */
            retryAt?: string | null;
            /** Format: int32 */
            status: number;
            /** Format: date-time */
            timestamp: string;
            title: string;
            traceId?: string | null;
            /** Format: uri */
            type: string;
        };
        Room: {
            /** @enum {string} */
            changeState: "UNCHANGED" | "CHANGED" | "UNKNOWN";
            current: string | null;
            previous: string | null;
        };
        SemesterSummary: {
            /** Format: date */
            endsOn: string;
            id: string;
            name: string;
            /** Format: date */
            startsOn: string;
        };
        StudentCheckinAck: {
            _links: {
                [key: string]: components["schemas"]["Link"];
            };
            attendance: components["schemas"]["Attendance"] | null;
            lessonId: string;
            /** @enum {string} */
            outcome: "PRESENT" | "PENDING_CONFIRMATION";
            request: components["schemas"]["AutomaticCheckinRequest"] | null;
            /** Format: date-time */
            retryAt: string | null;
            /** Format: date-time */
            serverNow: string;
        };
        StudentCheckinCommand: {
            geo: components["schemas"]["GeoInput"];
        };
        StudentHomework: {
            /** Format: date */
            from: string;
            items: components["schemas"]["StudentHomeworkItem"][];
            semester: components["schemas"]["StudentHomeworkSemester"];
            /** Format: date-time */
            serverNow: string;
            /** Format: date */
            to: string;
        };
        StudentHomeworkCompletion: {
            completed: boolean;
            id: string;
        };
        StudentHomeworkCompletionCommand: {
            completed: boolean;
        };
        StudentHomeworkItem: {
            completed: boolean;
            description: string;
            id: string;
            /** Format: date */
            lessonDate: string;
            /** Format: int32 */
            lessonNumber: number;
            link: string | null;
            subject: components["schemas"]["StudentHomeworkSubject"];
            title: string;
        };
        StudentHomeworkSemester: {
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            id: string;
            name: string;
        };
        StudentHomeworkSubject: {
            id: string;
            name: string;
        };
        StudentSemesterSchedule: {
            _links: {
                [key: string]: components["schemas"]["Link"];
            };
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            lessons: components["schemas"]["LessonSchedule"][];
            semester: components["schemas"]["SemesterSummary"];
            /** Format: date-time */
            updatedAt: string;
        };
        StudentSession: {
            _links: {
                [key: string]: components["schemas"]["Link"];
            };
            /** @enum {string} */
            activeRole: "STUDENT";
            capabilities: ("TODAY" | "GEO_CHECKIN" | "OFFLINE_SEMESTER_SCHEDULE")[];
            group: components["schemas"]["GroupSummary"] | null;
            semester: components["schemas"]["SemesterSummary"] | null;
            /** Format: date-time */
            serverNow: string;
            user: components["schemas"]["StudentUser"];
        };
        StudentToday: {
            _links: {
                [key: string]: components["schemas"]["Link"];
            };
            /** Format: date */
            date: string;
            lessons: components["schemas"]["TodayLesson"][];
            /** Format: date-time */
            serverNow: string;
            timeZone: string;
        };
        StudentUser: {
            displayName: string;
            id: string;
        };
        Subject: {
            id: string;
            name: string;
            /** @enum {string} */
            type: "LECTURE" | "PRACTICE" | "LAB";
        };
        TodayLesson: {
            attendance: components["schemas"]["Attendance"] | null;
            checkinEligibility: components["schemas"]["CheckinEligibility"];
            request: components["schemas"]["AutomaticCheckinRequest"] | null;
            schedule: components["schemas"]["LessonSchedule"];
        };
        UnavailableGeo: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "UNAVAILABLE";
            /** @enum {string} */
            reason: "PERMISSION_DENIED" | "POSITION_UNAVAILABLE" | "TIMEOUT";
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
    getHomework: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Homework feed */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentHomework"];
                };
            };
            /** @description Invalid or out-of-semester date range */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Wrong role or group scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Homework scope was not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Academic dependency unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
    setHomeworkCompletion: {
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
                "application/json": components["schemas"]["StudentHomeworkCompletionCommand"];
            };
        };
        responses: {
            /** @description Completion state */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentHomeworkCompletion"];
                };
            };
            /** @description Invalid request */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Wrong role, group or semester scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Homework not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Academic dependency unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
    checkin: {
        parameters: {
            query?: never;
            header: {
                /** @description Opaque 16-128 ASCII character command key */
                "Idempotency-Key": string;
            };
            path: {
                lessonId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StudentCheckinCommand"];
            };
        };
        responses: {
            /** @description PRESENT or atomically persisted PENDING_CONFIRMATION */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentCheckinAck"];
                };
            };
            /** @description Invalid input or idempotency key */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Wrong role or group scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Lesson not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Manual absence, ineligible state or idempotency payload mismatch */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Durable 300-second pair cooldown */
            429: {
                headers: {
                    /** @description Whole seconds until retry */
                    "Retry-After"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Mandatory dependency unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
    getSchedule: {
        parameters: {
            query: {
                semesterId: string;
            };
            header?: {
                "If-None-Match"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Materialized lessons in one authorized semester */
            200: {
                headers: {
                    /** @description private, no-cache */
                    "Cache-Control"?: unknown;
                    /** @description Authorized stable representation validator */
                    ETag?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentSemesterSchedule"];
                };
            };
            /** @description Authorized representation unchanged */
            304: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentSemesterSchedule"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Semester is outside the student's group scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Mandatory dependency unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
    getSession: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Session projection */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentSession"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Active role is not STUDENT */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Mandatory dependency unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
    getToday: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Live Today projection */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentToday"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Wrong role or group scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Mandatory dependency unavailable; eligibility fails closed */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
}
