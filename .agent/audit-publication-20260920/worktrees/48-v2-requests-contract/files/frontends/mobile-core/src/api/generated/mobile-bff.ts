/* eslint-disable */
// Generated from docs/openapi/mobile-bff.json. Do not edit.
// Contract: JS-STUDENT-01-r1; SHA-256: d4f97e0476cd681a9460247d9f904c7901241269b73221a097ec6be45132cda0

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
    "/api/v1/student/homework/{id}/completion": {
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
    "/api/v1/student/lessons/{lessonId}/checkin": {
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
    "/api/v1/student/requests": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List the authenticated student's requests */
        get: operations["listRequests"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/requests/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get one of the authenticated student's requests */
        get: operations["getRequest"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/requests/{id}/attachments/{attachmentId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Download a request attachment */
        get: operations["downloadRequestAttachment"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/requests/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancel a pending student request */
        post: operations["cancelRequest"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/requests/excuse": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Submit a student excuse request */
        post: operations["submitExcuse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/requests/late-checkin": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Submit a manual late-checkin request */
        post: operations["submitLateCheckin"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/requests/options": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get request submission options */
        get: operations["requestOptions"];
        put?: never;
        post?: never;
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
            resolutionReason: "GEO_CONFIRMED" | "HEADMAN_APPROVED" | "HEADMAN_REJECTED" | "STUDENT_CANCELLED" | "PRESENT_PRIORITY" | null;
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
            endsAt: string;
            id: string;
            /** Format: int32 */
            lessonNumber: number;
            room: components["schemas"]["Room"];
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
            code: "INVALID_REQUEST" | "INVALID_IDEMPOTENCY_KEY" | "INVALID_SESSION" | "WRONG_ROLE" | "OUT_OF_SCOPE" | "ROLE_READ_ONLY" | "HOMEWORK_NOT_FOUND" | "LESSON_NOT_FOUND" | "REQUEST_NOT_FOUND" | "ATTACHMENT_NOT_FOUND" | "REQUEST_CONFLICT" | "ATTACHMENT_EXPIRED" | "PAYLOAD_TOO_LARGE" | "CHECKIN_COOLDOWN" | "MANUAL_ABSENCE_REQUIRES_APPEAL" | "IDEMPOTENCY_PAYLOAD_MISMATCH" | "CHECKIN_NOT_ELIGIBLE" | "DEPENDENCY_UNAVAILABLE" | "INTERNAL_ERROR";
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
        StudentExcuseRequest: {
            comment?: string;
            lessonIds: string[];
            /** @enum {string} */
            reason: "ILLNESS" | "MEDICAL_EXAMINATION" | "COMPETITION_PARTICIPATION" | "FAMILY_CIRCUMSTANCES" | "OTHER";
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
            /** Format: date-time */
            completedAt: string | null;
            id: string;
        };
        StudentHomeworkCompletionCommand: {
            completed: boolean;
        };
        StudentHomeworkItem: {
            completed: boolean;
            /** Format: date-time */
            completedAt: string | null;
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
        StudentLateCheckinRequest: {
            lessonId: string;
        };
        StudentRequestAttachment: {
            contentType: string;
            /** Format: date-time */
            expiredAt: string | null;
            /** Format: date-time */
            expiresAt: string;
            id: string;
            name: string;
            sha256: string;
            /** Format: int64 */
            sizeBytes: number;
            /** @enum {string} */
            state: "ACTIVE" | "EXPIRED";
            /** Format: date-time */
            uploadedAt: string;
        };
        StudentRequestBudget: {
            /** Format: int32 */
            limit: number;
            /** Format: int32 */
            remaining: number;
            semesterId: string;
            /** Format: int32 */
            used: number;
        };
        StudentRequestDecision: {
            comment: string | null;
            /** Format: date-time */
            decidedAt: string | null;
        } | null;
        StudentRequestDetail: {
            attachments: components["schemas"]["StudentRequestAttachment"][];
            comment: string | null;
            decision: components["schemas"]["StudentRequestDecision"];
            /** @enum {string|null} */
            reason: "ILLNESS" | "MEDICAL_EXAMINATION" | "COMPETITION_PARTICIPATION" | "FAMILY_CIRCUMSTANCES" | "OTHER" | null;
            summary: components["schemas"]["StudentRequestSummary"];
        };
        StudentRequestFileLimits: {
            contentTypes: string[];
            extensions: string[];
            /** Format: int64 */
            maxBytesPerFile: number;
            /** Format: int64 */
            maxBytesTotal: number;
            /** Format: int32 */
            maxFiles: number;
        };
        StudentRequestLesson: {
            blocked: boolean;
            /** Format: date */
            date?: string | null;
            endsAt?: string | null;
            id: string;
            /** Format: int32 */
            lessonNumber: number;
            semesterId?: string | null;
            startsAt?: string | null;
            status: string;
            subjectId?: string | null;
            subjectName?: string | null;
            subjectType?: string | null;
        };
        StudentRequestLessonOption: {
            excuseEligible: boolean;
            lateCheckinEligible: boolean;
            lesson: components["schemas"]["StudentRequestLesson"];
            pendingRequests: components["schemas"]["StudentRequestPendingRef"][];
        };
        StudentRequestOptions: {
            budget: components["schemas"]["StudentRequestBudget"];
            files: components["schemas"]["StudentRequestFileLimits"];
            lessons: components["schemas"]["StudentRequestLessonOption"][];
            reasons: components["schemas"]["StudentRequestReasonOption"][];
        };
        StudentRequestPage: {
            content: components["schemas"]["StudentRequestSummary"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            totalElements: number;
            /** Format: int32 */
            totalPages: number;
        };
        StudentRequestPendingRef: {
            id: string;
            /** @enum {string} */
            kind: "EXCUSE" | "LATE_CHECKIN";
            /** @enum {string} */
            origin: "MANUAL" | "AUTO_GEO_FAILURE";
        };
        StudentRequestReasonOption: {
            /** @enum {string} */
            code: "ILLNESS" | "MEDICAL_EXAMINATION" | "COMPETITION_PARTICIPATION" | "FAMILY_CIRCUMSTANCES" | "OTHER";
            commentRequired: boolean;
            label: string;
        };
        StudentRequestSummary: {
            /** Format: date-time */
            createdAt: string;
            id: string;
            /** @enum {string} */
            kind: "EXCUSE" | "LATE_CHECKIN";
            lessons: components["schemas"]["StudentRequestLesson"][];
            /** @enum {string} */
            origin: "MANUAL" | "AUTO_GEO_FAILURE";
            /** @enum {string} */
            status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
            /** Format: date-time */
            updatedAt: string;
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
            readOnly: boolean;
            rolesVersion: string;
            semester: components["schemas"]["SemesterSummary"] | null;
            /** Format: date-time */
            serverNow: string;
            /** Format: uuid */
            sessionId: string;
            sessionVersion: string;
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
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Wrong role or group scope */
            403: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Homework scope was not found */
            404: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Academic dependency unavailable */
            503: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
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
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Wrong role, group or semester scope */
            403: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Homework not found */
            404: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Academic dependency unavailable */
            503: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
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
    listRequests: {
        parameters: {
            query?: {
                bucket?: "OPEN" | "ARCHIVE";
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Request page */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentRequestPage"];
                };
            };
            /** @description Invalid page parameters */
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
            /** @description Dependency unavailable */
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
    getRequest: {
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
            /** @description Request detail */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentRequestDetail"];
                };
            };
            /** @description Invalid request id */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Request not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
    downloadRequestAttachment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                attachmentId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Attachment bytes */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/octet-stream": unknown;
                };
            };
            /** @description Attachment is outside the student's scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Attachment not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Attachment expired */
            410: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
    cancelRequest: {
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
            /** @description Cancelled or replayed request */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentRequestDetail"];
                };
            };
            /** @description Request not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Request is already decided */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
        };
    };
    submitExcuse: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "multipart/form-data": {
                    files?: string[];
                    request: components["schemas"]["StudentExcuseRequest"];
                };
            };
        };
        responses: {
            /** @description Created or replayed request */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentRequestDetail"];
                };
            };
            /** @description Invalid request, file or key */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Request conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Payload too large */
            413: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Dependency unavailable */
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
    submitLateCheckin: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StudentLateCheckinRequest"];
            };
        };
        responses: {
            /** @description Created or replayed request */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentRequestDetail"];
                };
            };
            /** @description Invalid request or key */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Request conflict */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["MobileProblemDetails"];
                };
            };
            /** @description Dependency unavailable */
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
    requestOptions: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Request options */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentRequestOptions"];
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
