/* eslint-disable */
// Generated from docs/openapi/mobile-bff.json. Do not edit.
// Contract: JS-STUDENT-01-r1; SHA-256: 67c9cc911fd173bb838b3f23b64370a1feffd94cc2f02a0cd856f740b9af062e

export interface paths {
    "/api/v1/map/buildings/{buildingId}/floors/{floorId}/opens": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Record one explicit floor-open intent */
        post: operations["recordFloorOpen_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/map/buildings/{buildingId}/floors/{floorId}/plan": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read the current plan metadata for one authorized floor */
        get: operations["getFloorPlan_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/map/buildings/{buildingId}/floors/{floorId}/plans/{version}/assets/{format}/{assetId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Stream one validated private map asset */
        get: operations["readAsset_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/map/manifest": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read the authorized campus map manifest */
        get: operations["getManifest_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/attendance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student attendance projection */
        get: operations["getAttendance"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
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
    "/api/v1/student/map/buildings/{buildingId}/floors/{floorId}/opens": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["recordFloorOpen"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/map/buildings/{buildingId}/floors/{floorId}/plan": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getFloorPlan"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/map/buildings/{buildingId}/floors/{floorId}/plans/{version}/assets/{format}/{assetId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["readAsset"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/map/manifest": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getManifest"];
        put?: never;
        post?: never;
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
    "/api/v1/student/statistics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student attendance statistics overview */
        get: operations["getStatistics"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/statistics/ranking": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student attendance ranking for the current group */
        get: operations["getStatisticsRanking"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/student/statistics/subjects/{subjectId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student statistics for one subject */
        get: operations["getStatisticsSubject"];
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
    "/api/v1/teacher/assignments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Teacher assignments in a dated semester range */
        get: operations["assignments"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/catalog": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Current teacher groups and scheduled subject catalog */
        get: operations["catalog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/day": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Teacher cross-group day schedule */
        get: operations["day"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/excuses/{requestId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Server-scoped excuse detail */
        get: operations["excuse"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/excuses/{requestId}/attachments/{attachmentId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Server-scoped excuse attachment */
        get: operations["excuseAttachment"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/journal": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Concrete lesson journal */
        get: operations["journal"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/journal/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Download a server-generated complete teacher journal */
        get: operations["exportJournal"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/journal/export/formats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Server-supported teacher journal export formats */
        get: operations["journalExportFormats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/lessons/{lessonId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Concrete lesson read-only roster */
        get: operations["lesson"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Search current authorised teacher groups and students */
        get: operations["search"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/semester": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Teacher active semester bootstrap */
        get: operations["semester"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Teacher server-side attendance statistics */
        get: operations["stats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/stats/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Download a server-generated teacher statistics summary */
        get: operations["exportStats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teacher/stats/export/formats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Server-supported teacher statistics export formats */
        get: operations["statsExportFormats"];
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
        DecisionSelection: {
            excludedLessons?: components["schemas"]["StudentRequestLesson"][];
            expectedRevision?: string;
            includedLessons?: components["schemas"]["StudentRequestLesson"][];
            requestKey?: string;
        } | null;
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
        StudentAttendance: {
            _links: {
                [key: string]: components["schemas"]["Link"];
            };
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            days: components["schemas"]["StudentAttendanceDay"][];
            graph: components["schemas"]["StudentAttendanceGraph"];
            metrics: components["schemas"]["StudentAttendanceMetricSet"];
            ownRank: components["schemas"]["StudentAttendanceOwnRank"];
            semester: components["schemas"]["SemesterSummary"];
            /** Format: date-time */
            serverNow: string;
            subjects: components["schemas"]["StudentAttendanceSubject"][];
            terminalReadOnly: boolean;
            weeks: components["schemas"]["StudentAttendanceWeek"][];
        };
        StudentAttendanceDay: {
            /** Format: date */
            date: string;
            dayNumber: string;
            lessons: components["schemas"]["StudentAttendanceLesson"][];
            /** @enum {string} */
            state: "PAST" | "CURRENT" | "FUTURE";
            weekday: string;
        };
        StudentAttendanceExplanation: {
            cancellationReason?: string | null;
            comment?: string | null;
            reasonCode?: string | null;
            reasonText?: string | null;
        } | null;
        StudentAttendanceGraph: {
            days: components["schemas"]["StudentAttendanceSeriesPoint"][];
            weeks: components["schemas"]["StudentAttendanceSeriesPoint"][];
        };
        StudentAttendanceGroupPresent: {
            /** Format: int32 */
            denominator: number;
            /** Format: int32 */
            numerator: number;
            /** Format: double */
            percent: number | null;
        };
        StudentAttendanceHistorySegment: {
            explanation?: components["schemas"]["StudentAttendanceExplanation"];
            id: string;
            /** @enum {string} */
            status: "PRESENT" | "ABSENT" | "EXCUSED" | "FUTURE" | "NO_DATA";
        };
        StudentAttendanceLesson: {
            /** Format: date */
            date: string;
            explanation?: components["schemas"]["StudentAttendanceExplanation"];
            id: string;
            number: string;
            requestOptions: components["schemas"]["StudentAttendanceRequestOption"][];
            schedule: components["schemas"]["StudentAttendanceLessonSchedule"];
            /** @enum {string} */
            status: "PRESENT" | "ABSENT" | "EXCUSED" | "ACTIVE" | "FUTURE" | "NO_DATA" | "CANCELLED";
            subject: components["schemas"]["Subject"];
            /** @enum {string} */
            type: "LECTURE" | "PRACTICE" | "LAB";
        };
        StudentAttendanceLessonSchedule: {
            endsAt: string;
            room: string | null;
            startsAt: string;
        };
        StudentAttendanceMetricSet: {
            absent: components["schemas"]["StudentAttendanceMetricValue"];
            excused: components["schemas"]["StudentAttendanceMetricValue"];
            /** Format: int32 */
            held: number;
            /** Format: int32 */
            planned: number;
            present: components["schemas"]["StudentAttendanceMetricValue"];
            presentOrExcused: components["schemas"]["StudentAttendanceMetricValue"];
        };
        StudentAttendanceMetricValue: {
            /** Format: int32 */
            count: number;
            /** Format: double */
            percent: number | null;
        };
        StudentAttendanceOwnRank: {
            available: boolean;
            /** Format: int32 */
            participantCount: number;
            /** Format: int32 */
            position: number | null;
        };
        StudentAttendanceRequestOption: {
            enabled: boolean;
            id: string;
            kind: string;
            label: string;
            reason?: string | null;
        };
        StudentAttendanceSeriesPoint: {
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            groupPresent?: components["schemas"]["StudentAttendanceGroupPresent"];
            id: string;
            label: string;
            metrics: components["schemas"]["StudentAttendanceMetricSet"];
            /** @enum {string} */
            state: "DATA" | "NO_DATA" | "FUTURE";
        };
        StudentAttendanceSubject: {
            availableTypes?: ("LECTURE" | "PRACTICE" | "LAB")[];
            id: string;
            metrics?: components["schemas"]["StudentAttendanceMetricSet"];
            name: string;
            selectedAggregate?: components["schemas"]["StudentAttendanceMetricSet"];
            selectedTypes?: ("LECTURE" | "PRACTICE" | "LAB")[];
            series?: components["schemas"]["StudentAttendanceSeriesPoint"][];
            typeCards: components["schemas"]["StudentAttendanceTypeCard"][];
        };
        StudentAttendanceTypeCard: {
            history: components["schemas"]["StudentAttendanceHistorySegment"][];
            metrics: components["schemas"]["StudentAttendanceMetricSet"];
            /** @enum {string} */
            type: "LECTURE" | "PRACTICE" | "LAB";
        };
        StudentAttendanceWeek: {
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            parityLabel: string;
            /** Format: int32 */
            parityNumber: number;
            /** Format: int32 */
            weekOfSemester: number;
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
            reason: "ILLNESS" | "MEDICAL_EXAMINATION" | "COMPETITION_PARTICIPATION" | "FAMILY_CIRCUMSTANCES" | "OTHER" | "UNIVERSITY_ORDER" | "SUMMONS";
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
            archived: boolean;
            /** @enum {string} */
            bindingMode: "LESSON" | "DATE";
            completed: boolean;
            /** Format: date-time */
            completedAt: string | null;
            description: string;
            id: string;
            /** Format: date */
            lessonDate: string;
            /**
             * Format: int32
             * @description Номер пары для LESSON; null для DATE
             */
            lessonNumber: number | null;
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
        StudentMapBuilding: {
            floors: components["schemas"]["StudentMapFloor"][];
            id: string;
            label: string;
        };
        StudentMapFloor: {
            id: string;
            label: string;
            plan: components["schemas"]["StudentMapPlan"];
        };
        StudentMapFormatSlot: {
            /** Format: int64 */
            bytes: number;
            contentType: string;
            /** @enum {string} */
            format: "png" | "svg";
            /** Format: int32 */
            height?: number | null;
            id: string | null;
            sha256: string | null;
            /** @enum {string} */
            state: "absent" | "processing" | "ready" | "failed";
            viewBox?: (number | null)[] | null;
            /** Format: int32 */
            width?: number | null;
        };
        StudentMapManifest: {
            buildings: components["schemas"]["StudentMapBuilding"][];
            revision: string;
            /** Format: int32 */
            schemaVersion: number;
            /** Format: int32 */
            validationPolicyVersion: number;
        };
        StudentMapPlan: {
            buildingId: string;
            floorId: string;
            label: string;
            png: components["schemas"]["StudentMapFormatSlot"];
            svg: components["schemas"]["StudentMapFormatSlot"];
            version: string;
        } | null;
        StudentMapPlanResponse: {
            plan: components["schemas"]["StudentMapPlan"];
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
            decisionSelection?: components["schemas"]["DecisionSelection"];
            /** @enum {string|null} */
            reason: "ILLNESS" | "MEDICAL_EXAMINATION" | "COMPETITION_PARTICIPATION" | "FAMILY_CIRCUMSTANCES" | "OTHER" | "UNIVERSITY_ORDER" | "SUMMONS" | null;
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
            occurrenceId?: string | null;
            occurrenceRevision?: string | null;
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
            code: "ILLNESS" | "MEDICAL_EXAMINATION" | "COMPETITION_PARTICIPATION" | "FAMILY_CIRCUMSTANCES" | "OTHER" | "UNIVERSITY_ORDER" | "SUMMONS";
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
        StudentStatistics: {
            _links: {
                [key: string]: components["schemas"]["Link"];
            };
            metrics: components["schemas"]["StudentAttendanceMetricSet"];
            ownRank: components["schemas"]["StudentAttendanceOwnRank"];
            semesterSeries: components["schemas"]["StudentAttendanceSeriesPoint"][];
            subjects: components["schemas"]["StudentStatisticsSubject"][];
        };
        StudentStatisticsRanking: {
            available: boolean;
            /** Format: int32 */
            ownPosition: number | null;
            /** Format: int32 */
            page: number;
            rows: components["schemas"]["StudentStatisticsRankingRow"][];
            /** Format: int32 */
            size: number;
            /** Format: int32 */
            total: number;
        };
        StudentStatisticsRankingRow: {
            id: string;
            isSelf: boolean;
            name: string;
            /** Format: double */
            percentage: number;
            /** Format: int32 */
            position: number;
        };
        StudentStatisticsSubject: {
            id: string;
            metrics: components["schemas"]["StudentAttendanceMetricSet"];
            name: string;
        };
        StudentStatisticsSubjectDetail: {
            availableTypes: ("LECTURE" | "PRACTICE" | "LAB")[];
            name: string;
            selectedAggregate: components["schemas"]["StudentAttendanceMetricSet"];
            selectedTypes: ("LECTURE" | "PRACTICE" | "LAB")[];
            series: components["schemas"]["StudentAttendanceSeriesPoint"][];
            subjectId: string;
            typeCards: components["schemas"]["StudentAttendanceTypeCard"][];
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
        TeacherAssignment: {
            groupId?: string;
            groupName?: string;
            id?: string;
            lessonType?: string;
            semesterId?: string;
            subjectId?: string;
            subjectName?: string;
            teacherId?: string;
            /** Format: date */
            validFrom?: string;
            /** Format: date */
            validUntilExclusive?: string;
        };
        TeacherCatalog: {
            groups?: components["schemas"]["TeacherCatalogGroup"][];
            /** Format: date-time */
            serverNow?: string;
            subjectOptions?: components["schemas"]["TeacherStatsSubjectOption"][];
            taughtSubjectOptions?: components["schemas"]["TeacherStatsSubjectOption"][];
        };
        TeacherCatalogGroup: {
            id?: string;
            name?: string;
        };
        TeacherDay: {
            /** Format: date */
            date?: string;
            lessons?: components["schemas"]["TeacherDayLesson"][];
            semesterId?: string;
            /** Format: date-time */
            serverNow?: string;
        };
        TeacherDayLesson: {
            assignmentId?: string;
            cancellationReason?: string;
            cancelled?: boolean;
            /** Format: date */
            date?: string;
            endsAt?: string;
            groupId?: string;
            groupName?: string;
            id?: string;
            /** Format: int32 */
            lessonNumber?: number;
            lessonType?: string;
            moved?: boolean;
            oneOff?: boolean;
            room?: string;
            semesterId?: string;
            startsAt?: string;
            status?: string;
            subjectId?: string;
            subjectName?: string;
        };
        TeacherExcuse: {
            attachments?: components["schemas"]["TeacherExcuseAttachment"][];
            comment?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            decisionAt?: string;
            decisionBy?: string;
            decisionByName?: string;
            decisionComment?: string;
            excuseType?: string;
            groupId?: string;
            groupName?: string;
            id?: string;
            kind?: string;
            lessons?: components["schemas"]["TeacherDayLesson"][];
            reason?: string;
            /** Format: date-time */
            serverNow?: string;
            status?: string;
            studentId?: string;
            studentName?: string;
        };
        TeacherExcuseAttachment: {
            contentType?: string;
            /** Format: date-time */
            expiresAt?: string;
            fileName?: string;
            id?: string;
            /** Format: int64 */
            size?: number;
            /** Format: date-time */
            uploadedAt?: string;
        };
        TeacherExportFormat: {
            code?: string;
            contentType?: string;
            extension?: string;
            label?: string;
        };
        TeacherExportFormats: {
            formats?: components["schemas"]["TeacherExportFormat"][];
        };
        TeacherJournal: {
            hasMore?: boolean;
            lessons?: components["schemas"]["TeacherDayLesson"][];
            metrics?: components["schemas"]["TeacherJournalMetrics"];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            pageSize?: number;
            /** Format: date-time */
            serverNow?: string;
            students?: components["schemas"]["TeacherJournalStudent"][];
            /** Format: int32 */
            totalLessons?: number;
        };
        TeacherJournalCell: {
            autoAbsent?: boolean;
            excuseReason?: string;
            excuseType?: string;
            lessonId?: string;
            pendingTicket?: boolean;
            recordPresent?: boolean;
            source?: string;
            status?: string;
            symbol?: string;
            ticketId?: string;
        };
        TeacherJournalLessonMetrics: {
            absent?: components["schemas"]["TeacherStatsMetric"];
            excused?: components["schemas"]["TeacherStatsMetric"];
            lessonId?: string;
            present?: components["schemas"]["TeacherStatsMetric"];
            presentOrExcused?: components["schemas"]["TeacherStatsMetric"];
        };
        TeacherJournalMetrics: {
            selectedStudentId?: string | null;
            series?: components["schemas"]["TeacherJournalLessonMetrics"][];
            /** Format: date-time */
            serverNow?: string;
            students?: components["schemas"]["TeacherStatsStudent"][];
        } | null;
        TeacherJournalStudent: {
            cells?: components["schemas"]["TeacherJournalCell"][];
            displayName?: string;
            studentId?: string;
        };
        TeacherLesson: {
            lesson?: components["schemas"]["TeacherDayLesson"];
            roster?: components["schemas"]["TeacherRosterEntry"][];
            /** Format: date-time */
            serverNow?: string;
        };
        TeacherRosterEntry: {
            attachmentContentType?: string;
            attachmentId?: string;
            attachmentName?: string;
            /** Format: int64 */
            attachmentSize?: number;
            autoAbsent?: boolean;
            displayName?: string;
            excuseComment?: string;
            excuseReason?: string;
            excuseType?: string;
            pendingTicket?: boolean;
            recordPresent?: boolean;
            source?: string;
            status?: string;
            studentId?: string;
            symbol?: string;
            ticketId?: string;
        };
        TeacherSearch: {
            groups?: components["schemas"]["TeacherCatalogGroup"][];
            students?: components["schemas"]["TeacherSearchStudent"][];
            truncated?: boolean;
        };
        TeacherSearchStudent: {
            displayName?: string;
            groupId?: string;
            groupName?: string;
            id?: string;
        };
        TeacherSemester: {
            /** Format: date */
            dateFrom?: string;
            /** Format: date */
            dateTo?: string;
            id?: string;
            name?: string;
        };
        TeacherStats: {
            groups?: components["schemas"]["TeacherStatsGroup"][];
            /** Format: int32 */
            lessonsCount?: number;
            /** Format: date */
            periodFrom?: string;
            /** Format: date */
            periodTo?: string;
            scope?: string;
            semesterId?: string;
            /** Format: date-time */
            serverNow?: string;
            students?: components["schemas"]["TeacherStatsStudent"][];
            subjectOptions?: components["schemas"]["TeacherStatsSubjectOption"][];
        };
        TeacherStatsGroup: {
            absent?: components["schemas"]["TeacherStatsMetric"];
            excused?: components["schemas"]["TeacherStatsMetric"];
            groupId?: string;
            groupName?: string;
            /** Format: int32 */
            lessonsCount?: number;
            present?: components["schemas"]["TeacherStatsMetric"];
            presentOrExcused?: components["schemas"]["TeacherStatsMetric"];
        };
        TeacherStatsMetric: {
            /** Format: int32 */
            denominator?: number;
            /** Format: int32 */
            numerator?: number;
            /** Format: double */
            percent?: number;
        };
        TeacherStatsStudent: {
            absent?: components["schemas"]["TeacherStatsMetric"];
            displayName?: string;
            excused?: components["schemas"]["TeacherStatsMetric"];
            present?: components["schemas"]["TeacherStatsMetric"];
            presentOrExcused?: components["schemas"]["TeacherStatsMetric"];
            studentId?: string;
        };
        TeacherStatsSubjectOption: {
            groupId?: string;
            lessonTypes?: string[];
            subjectId?: string;
            subjectName?: string;
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
    recordFloorOpen_1: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                buildingId: string;
                floorId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Open intent accepted or already counted */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Invalid idempotency key */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Floor is outside the current role scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Building or floor not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Intent is already bound to another floor */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Map usage is unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    getFloorPlan_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                buildingId: string;
                floorId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Plan metadata, including independent format states */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapPlanResponse"];
                };
            };
            /** @description No plan published for the floor */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapPlanResponse"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapPlanResponse"];
                };
            };
            /** @description Floor is outside the current role scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapPlanResponse"];
                };
            };
            /** @description Building or floor not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapPlanResponse"];
                };
            };
            /** @description Map catalog unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapPlanResponse"];
                };
            };
        };
    };
    readAsset_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assetId: string;
                buildingId: string;
                floorId: string;
                format: "png" | "svg";
                version: string;
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
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Asset is outside the current role scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Asset not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description Asset is incomplete or failed validation */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    getManifest_1: {
        parameters: {
            query?: never;
            header?: {
                "If-None-Match"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Ordered campus map manifest */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapManifest"];
                };
            };
            /** @description Manifest unchanged */
            304: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapManifest"];
                };
            };
            /** @description Invalid or expired session */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapManifest"];
                };
            };
            /** @description Role is outside the campus-map scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapManifest"];
                };
            };
            /** @description Map catalog unavailable */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentMapManifest"];
                };
            };
        };
    };
    getAttendance: {
        parameters: {
            query: {
                semesterId: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Server-calculated attendance projection */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentAttendance"];
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
            /** @description Semester or student scope is unavailable */
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
    recordFloorOpen: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                buildingId: string;
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
                content?: never;
            };
        };
    };
    getFloorPlan: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                buildingId: string;
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
                    "*/*": components["schemas"]["StudentMapPlanResponse"];
                };
            };
        };
    };
    readAsset: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assetId: string;
                buildingId: string;
                floorId: string;
                format: "png" | "svg";
                version: string;
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
                    "*/*": string;
                };
            };
        };
    };
    getManifest: {
        parameters: {
            query?: never;
            header?: {
                "If-None-Match"?: string;
            };
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
                    "*/*": components["schemas"]["StudentMapManifest"];
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
    getStatistics: {
        parameters: {
            query: {
                semesterId: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Server-calculated semester statistics */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentStatistics"];
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
            /** @description Semester or student scope is unavailable */
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
    getStatisticsRanking: {
        parameters: {
            query: {
                page?: number;
                semesterId: string;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Authorized cohort ranking page */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentStatisticsRanking"];
                };
            };
            /** @description Invalid semester or page bounds */
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
            /** @description Semester or student scope is unavailable */
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
    getStatisticsSubject: {
        parameters: {
            query: {
                range?: string;
                semesterId: string;
                types?: string[];
            };
            header?: never;
            path: {
                subjectId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Server-calculated subject statistics */
            200: {
                headers: {
                    /** @description Always no-store */
                    "Cache-Control"?: unknown;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentStatisticsSubjectDetail"];
                };
            };
            /** @description Invalid range or lesson type filter */
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
            /** @description Subject or semester is outside signed scope */
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
    assignments: {
        parameters: {
            query: {
                dateFrom: string;
                dateTo: string;
                semesterId: string;
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
                    "*/*": components["schemas"]["TeacherAssignment"][];
                };
            };
        };
    };
    catalog: {
        parameters: {
            query: {
                semesterId: string;
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
                    "*/*": components["schemas"]["TeacherCatalog"];
                };
            };
        };
    };
    day: {
        parameters: {
            query: {
                date: string;
                semesterId: string;
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
                    "*/*": components["schemas"]["TeacherDay"];
                };
            };
        };
    };
    excuse: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                requestId: string;
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
                    "*/*": components["schemas"]["TeacherExcuse"];
                };
            };
        };
    };
    excuseAttachment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                attachmentId: string;
                requestId: string;
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
                    "application/octet-stream": string;
                };
            };
        };
    };
    journal: {
        parameters: {
            query: {
                dateFrom?: string;
                dateTo?: string;
                groupId: string;
                includeMetrics?: boolean;
                lessonType: string[];
                metricsStudentId?: string;
                page?: string;
                pageSize?: string;
                pastOnly?: boolean;
                semesterId: string;
                subjectId: string;
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
                    "*/*": components["schemas"]["TeacherJournal"];
                };
            };
        };
    };
    exportJournal: {
        parameters: {
            query: {
                dateFrom?: string;
                dateTo?: string;
                format: string;
                groupId: string;
                lessonId?: string;
                lessonType: string[];
                pastOnly?: boolean;
                semesterId: string;
                subjectId: string;
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
                    "application/octet-stream": string;
                };
            };
        };
    };
    journalExportFormats: {
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
                    "*/*": components["schemas"]["TeacherExportFormats"];
                };
            };
        };
    };
    lesson: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                lessonId: string;
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
                    "*/*": components["schemas"]["TeacherLesson"];
                };
            };
        };
    };
    search: {
        parameters: {
            query: {
                q: string;
                semesterId: string;
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
                    "*/*": components["schemas"]["TeacherSearch"];
                };
            };
        };
    };
    semester: {
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
                    "*/*": components["schemas"]["TeacherSemester"];
                };
            };
        };
    };
    stats: {
        parameters: {
            query: {
                filter?: string[];
                groupId?: string;
                lessonType?: string[];
                scope: string;
                semesterId: string;
                sort?: string[];
                subjectId?: string;
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
                    "*/*": components["schemas"]["TeacherStats"];
                };
            };
        };
    };
    exportStats: {
        parameters: {
            query: {
                filter?: string[];
                format: string;
                groupId?: string;
                lessonType?: string[];
                scope: string;
                semesterId: string;
                sort?: string[];
                subjectId?: string;
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
                    "application/octet-stream": string;
                };
            };
        };
    };
    statsExportFormats: {
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
                    "*/*": components["schemas"]["TeacherExportFormats"];
                };
            };
        };
    };
}
