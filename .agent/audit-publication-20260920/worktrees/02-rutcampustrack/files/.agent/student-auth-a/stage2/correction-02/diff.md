# Correction-02 diff summary

The stable correction delta against the prior Stage2 final manifest is listed
machine-readably in `manifest.json` with previous/current byte counts and
SHA-256 values.

- `AuthSessionController` catches optional WS Redis cleanup failures after the
  durable operation, and cookie-only logout uses the returned revoke snapshot
  to invalidate the correct user's tickets.
- `AuthService` translates repository dependency errors encountered during
  cookie-session metadata and password-change user lookup to
  `AUTHORITY_UNAVAILABLE`.
- `OtpService` consumes forward/reverse OTP proof indexes and counters in one
  Lua operation for both public forms, requiring a matching reverse owner;
  Redis and UserRepository dependency failures use the typed authority code.
- `TmaService` maps its UserRepository dependency failure at the session issue
  boundary to the same typed code.
- Owned tests cover Redis best-effort responses, cookie-only ticket cleanup,
  direct missing-reverse proof, mixed OTP concurrency and narrow repository
  failures.

No Gateway/BFF/shared-security/SQL/V24/OpenAPI source or unrelated dirty path
was included in this correction delta.

