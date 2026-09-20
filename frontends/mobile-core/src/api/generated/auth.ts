/* eslint-disable */
// Generated from docs/openapi/auth.json. Do not edit.
// Contract: Auth session/role; SHA-256: 61bfb083d71a4eb7f40c7bf7c0b464798ce7f884f00e52e7fe579e816a5ce9d2

export interface paths {
    "/auth/account-history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read the current user's account security history */
        get: operations["accountHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/change-password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Change the current user's password */
        post: operations["changePassword"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Login with credentials
         * @description Authenticate with login and password, returns JWT token pair + refresh cookie
         */
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Revoke the current session */
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/logout-all": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Revoke all sessions for the current user */
        post: operations["logoutAll"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/otp/request": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Request OTP code
         * @description Generate OTP code for Telegram-based authentication. M09 G2 (08 P0-2): код отправляется пользователю через notification-bot (RabbitMQ event otp.requested), НЕ возвращается в HTTP body.
         */
        post: operations["requestOtp"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/otp/verify": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Verify OTP code
         * @description Verify OTP code and receive JWT token pair + refresh cookie
         */
        post: operations["verifyOtp"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/otp/verify-by-code": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Verify OTP code without telegram ID
         * @description Verify OTP code by reverse lookup (code → telegramId). Used by web-panel where the user only enters the 6-digit code received in Telegram bot. M16 G3: brute-force защита по IP — 20 mismatch'ей за 5 мин → 429.
         */
        post: operations["verifyOtpByCode"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/public-key": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get RSA public key
         * @description Returns RSA public key in PEM format for JWT verification
         */
        get: operations["getPublicKey"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Refresh access token
         * @description Read refresh token from HttpOnly cookie 'rct_refresh', rotate it, return new access in body + new cookie.
         */
        post: operations["refresh"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/session": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read the current session */
        get: operations["currentSession"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/session/active-role": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Select the active role for the current session */
        put: operations["selectActiveRole"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List the current user's live sessions */
        get: operations["sessions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/tma": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Authenticate via Telegram Mini App
         * @description Validate Telegram initData (HMAC-SHA256) and return JWT token pair. Cookie also set for web-panel fallback; TMA clients read refreshToken from body.
         */
        post: operations["tmaAuth"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/ws-ticket": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Issue WebSocket ticket
         * @description Generate short-lived (30s, single-use) ticket for WebSocket handshake. Client uses it as `?ticket=<value>` query param. Replaces legacy `?token=<access_jwt>` pattern which leaked JWT into nginx/Gateway logs.
         */
        post: operations["issueTicket"];
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
        AccountHistoryEvent: {
            authMethod?: string;
            clientLabel?: string;
            id?: string;
            locationLabel?: string;
            /** Format: date-time */
            occurredAt: string;
            type?: string;
        };
        AccountHistoryPage: {
            items: components["schemas"]["AccountHistoryEvent"][];
            nextCursor?: string;
        };
        AuthSessionsPage: {
            items: components["schemas"]["AuthSessionSummary"][];
            nextCursor?: string;
        };
        AuthSessionSummary: {
            authMethod: string;
            clientLabel?: string;
            /** Format: date-time */
            createdAt: string;
            current: boolean;
            /** Format: date-time */
            lastSeenAt: string;
            locationLabel?: string;
            sessionId: string;
        };
        ChangePasswordRequest: {
            currentPassword?: string;
            newPassword?: string;
        };
        CurrentSessionResponse: {
            activeRole?: string;
            displayName: string;
            groupLabel?: string;
            passwordPolicy: components["schemas"]["PasswordPolicyResponse"];
            readOnly: boolean;
            roles: components["schemas"]["RoleGrantResponse"][];
            rolesVersion: string;
            sessionId: string;
            sessionVersion: string;
            userId: string;
        };
        /** @description Стандартный формат ошибки API (RFC 9457 Problem Details) */
        ErrorResponse: {
            /**
             * @description Детальное описание
             * @example Группа с id=99 не найдена
             */
            detail?: string;
            /**
             * @description Дополнительные данные для клиента (счётчики каскадного удаления, retry-after, и т.п.)
             * @example {
             *       "scheduleItemsCount": 3
             *     }
             */
            extras?: {
                [key: string]: Record<string, never>;
            };
            /**
             * @description Имя поля DTO, вызвавшего конфликт (только для 409, BUG-006-2)
             * @example login
             */
            field?: string;
            /** @description Ошибки валидации полей body DTO (только для 400) */
            fieldErrors?: components["schemas"]["FieldError"][];
            /**
             * @description URI запроса, вызвавшего ошибку
             * @example /api/academic/groups/99
             */
            instance?: string;
            /**
             * Format: int32
             * @description HTTP статус код
             * @example 404
             */
            status?: number;
            /**
             * Format: date-time
             * @description Время возникновения ошибки (UTC)
             * @example 2026-04-24T10:15:30Z
             */
            timestamp?: string;
            /**
             * @description Краткое описание ошибки
             * @example Ресурс не найден
             */
            title?: string;
            /**
             * @description Correlation ID из MDC (для трассировки в логах)
             * @example abc-trace-123
             */
            traceId?: string;
            /**
             * @description URI типа ошибки
             * @example https://api.rutcampustrack.ru/problems/resource-not-found
             */
            type?: string;
        };
        /** @description Ошибка валидации одного поля DTO (RFC 9457 Extension Member) */
        FieldError: {
            /**
             * @description Имя поля DTO
             * @example displayName
             */
            field?: string;
            /**
             * @description Локализованное сообщение об ошибке
             * @example Имя не может быть пустым
             */
            message?: string;
            /** @description Отклонённое значение */
            rejectedValue?: Record<string, never>;
        };
        LoginRequest: {
            login?: string;
            password?: string;
        };
        OtpRequest: {
            /** Format: int64 */
            telegramId: number;
        };
        OtpVerifyByCodeRequest: {
            code?: string;
        };
        OtpVerifyRequest: {
            code?: string;
            /** Format: int64 */
            telegramId: number;
        };
        PasswordPolicyResponse: {
            /** Format: int32 */
            maxUtf8Bytes?: number;
            /** Format: int32 */
            minCodePoints?: number;
            normalization: string;
            requiresDecimalDigit?: boolean;
            specialCategories: string[];
        };
        PublicKeyResponse: {
            algorithm?: string;
            publicKey?: string;
        };
        RoleGrantResponse: {
            contextLabel?: string;
            grantId?: string;
            groupId?: string;
            readOnly?: boolean;
            role?: string;
            selectable?: boolean;
            status?: string;
        };
        SelectActiveRoleRequest: {
            expectedSessionVersion?: string;
            role?: string;
        };
        SelectActiveRoleResponse: {
            accessToken?: string;
            /** Format: int64 */
            expiresIn?: number;
            session: components["schemas"]["CurrentSessionResponse"];
        };
        TmaAuthRequest: {
            initData?: string;
        };
        TokenResponse: {
            accessToken?: string;
            /** Format: int64 */
            expiresIn?: number;
            refreshToken?: string;
        };
        WsTicketResponse: {
            /** Format: date-time */
            expiresAt?: string;
            ticket?: string;
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
    accountHistory: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Own account history page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AccountHistoryPage"];
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
    changePassword: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangePasswordRequest"];
            };
        };
        responses: {
            /** @description Password changed and sessions revoked */
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
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description Successfully authenticated */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Invalid credentials */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: {
                rct_refresh?: string;
            };
        };
        requestBody?: never;
        responses: {
            /** @description Current session revoked */
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
    logoutAll: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description All sessions revoked */
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
    requestOtp: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OtpRequest"];
            };
        };
        responses: {
            /** @description OTP code generated and dispatched to Telegram bot */
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
            /** @description Unknown telegram_id or inactive account */
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
            /** @description Rate limited — too many requests */
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
    verifyOtp: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OtpVerifyRequest"];
            };
        };
        responses: {
            /** @description OTP verified, JWT pair returned */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Invalid or expired OTP code */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
    verifyOtpByCode: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OtpVerifyByCodeRequest"];
            };
        };
        responses: {
            /** @description OTP verified, JWT pair returned */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Invalid or expired OTP code */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
    getPublicKey: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Public key retrieved */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PublicKeyResponse"];
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
    refresh: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: {
                rct_refresh?: string;
            };
        };
        requestBody?: never;
        responses: {
            /** @description Tokens refreshed successfully */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Missing, invalid or expired refresh cookie */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
    currentSession: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Current live session state */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CurrentSessionResponse"];
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
    selectActiveRole: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SelectActiveRoleRequest"];
            };
        };
        responses: {
            /** @description Role selected with a fresh session snapshot */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SelectActiveRoleResponse"];
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
    sessions: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Own non-expired sessions */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AuthSessionsPage"];
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
    tmaAuth: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TmaAuthRequest"];
            };
        };
        responses: {
            /** @description Successfully authenticated via TMA */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Invalid or tampered initData, or user not linked */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
    issueTicket: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Ticket issued */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["WsTicketResponse"];
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
                    "*/*": components["schemas"]["WsTicketResponse"];
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
}
