// AUTO-GENERATED — do not edit by hand.
// Regenerate: npm run generate:types (requires backend running).
// Source: docs/openapi/auth.json (committed for CI drift-guard).

export interface paths {
    "/internal/issue-internal-jwt": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Issue Internal JWT
         * @description Exchange shared-secret + claims for a signed Internal JWT (audience=rutcampustrack-internal)
         */
        post: operations["issue"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/internal/consume-ws-ticket": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Consume WebSocket ticket
         * @description Atomic GET+DEL for single-use ticket. Returns {userId, role} if ticket was valid + not yet consumed, otherwise 404.
         */
        post: operations["consume"];
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
    "/auth/refresh-body": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Refresh tokens (body-based, deprecated)
         * @description Exchange refresh token in request body for new token pair. DEPRECATED in M03b — use cookie-based POST /auth/refresh instead. Planned removal: M04/M05.
         */
        post: operations["refreshBody"];
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
         * @description Verify OTP code by reverse lookup (code → telegramId). Used by web-panel where the user only enters the 6-digit code received in Telegram bot.
         */
        post: operations["verifyOtpByCode"];
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
    "/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Logout
         * @description Invalidate refresh token, ws-tickets, and clear refresh cookie
         */
        post: operations["logout"];
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
    "/auth/change-password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Change password
         * @description Change password for authenticated user
         */
        post: operations["changePassword"];
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
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        InternalIssueRequest: {
            /** Format: int64 */
            userId: number;
            role?: string;
            /** Format: int64 */
            groupId?: number;
            isHeadman?: boolean;
        };
        InternalIssueResponse: {
            token?: string;
            /** Format: date-time */
            expiresAt?: string;
        };
        ConsumeRequest: {
            ticket?: string;
        };
        ConsumeResponse: {
            /** Format: int64 */
            user_id?: number;
            role?: string;
            /** Format: int64 */
            group_id?: number;
            is_headman?: boolean;
            /** Format: date-time */
            expires_at?: string;
        };
        WsTicketResponse: {
            ticket?: string;
            /** Format: date-time */
            expiresAt?: string;
        };
        TmaAuthRequest: {
            initData?: string;
        };
        TokenResponse: {
            accessToken?: string;
            refreshToken?: string;
            /** Format: int64 */
            expiresIn?: number;
        };
        RefreshRequest: {
            refreshToken?: string;
        };
        OtpVerifyRequest: {
            /** Format: int64 */
            telegramId: number;
            code?: string;
        };
        OtpVerifyByCodeRequest: {
            code?: string;
        };
        OtpRequest: {
            /** Format: int64 */
            telegramId: number;
        };
        LoginRequest: {
            login?: string;
            password?: string;
        };
        ChangePasswordRequest: {
            currentPassword?: string;
            newPassword?: string;
        };
        PublicKeyResponse: {
            publicKey?: string;
            algorithm?: string;
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
    issue: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["InternalIssueRequest"];
            };
        };
        responses: {
            /** @description Token issued */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["InternalIssueResponse"];
                };
            };
            /** @description Invalid or missing X-Internal-Issuer-Secret */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["InternalIssueResponse"];
                };
            };
        };
    };
    consume: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConsumeRequest"];
            };
        };
        responses: {
            /** @description Ticket consumed, identity returned */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ConsumeResponse"];
                };
            };
            /** @description Ticket not found or already consumed/expired */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ConsumeResponse"];
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
            /** @description Missing or invalid access token */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["WsTicketResponse"];
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
            /** @description Invalid or tampered initData, or user not linked */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Missing, invalid or expired refresh cookie */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
                };
            };
        };
    };
    refreshBody: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RefreshRequest"];
            };
        };
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
            /** @description Invalid or expired refresh token */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Invalid or expired OTP code */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Invalid or expired OTP code */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Unknown telegram_id or inactive account */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Rate limited — too many requests */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
        requestBody?: {
            content: {
                "application/json": components["schemas"]["RefreshRequest"];
            };
        };
        responses: {
            /** @description Successfully logged out */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
            /** @description Invalid credentials */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TokenResponse"];
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
            /** @description Password changed successfully */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Current password is incorrect */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
        };
    };
}
