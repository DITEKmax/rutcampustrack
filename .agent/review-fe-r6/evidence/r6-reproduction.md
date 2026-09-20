# Pre-fix reproduction: retry and geolocation

The original shell call sites acquired a fresh command and called `newIdempotencyKey()` on every click. A server may commit the first POST but its response can be lost; the next click then sends a fresh key and is rejected as a duplicate/current ACK conflict. PWA's `readPosition` discarded `GeolocationPositionError`, so code 1 and 3 became generic `POSITION_UNAVAILABLE`.

Before the recovery implementation, `npx vitest run mobile-core/src/domain/checkin-recovery.test.ts` exited 1: all three executable cases failed with `TypeError: CheckinCommandRecovery is not a constructor`. The cases model the observed loss-after-commit retry, 4xx release, and 5xx retention. This is the bounded implementation gate requested by root; no WARN/ERROR caused an unrelated code change.
