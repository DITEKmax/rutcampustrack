# Bounded local PWA role-control acceptance 2026-10-04

Frontend source/build6cf3d7aa, permanent stand source8d800497; CUA IAB https://127.0.0.1:18514/app/. Existing synthetic seed student/password, no private fixture credentials. Trusted TLS without warning/bypass. Root UI owner, API owner uses distinct generated accounts; no Docker mutations during check.

PASS observed DOM:
1. /app/password-reset mounts recovery screen, missing code explicitly explained; return to login works. No recovery message or password change requested.
2. Student login → Today, empty schedule displayed without HTTP error.
3. New Student role control → existing role selection with STUDENT/HEADMAN grants; screen Back returns Student Today.
4. Select existing HEADMAN role → Headman Today, loaded group lessons empty state without prior HTTP404.
5. New Headman role control → existing selector; screen Back returns Headman Today.

Evidence screenshot: headman-home-runtime.png. Tab retained as deliverable. No visual Figma acceptance, full lesson CRUD, device/offline, native Telegram Back or real TMA acceptance claimed. Backend fresh-token ws-ticket matrix remains API owner's separate criterion.

Tool limitations retained: native AX represented aria-pressed role buttons as checkboxes; DOM snapshot identified actual buttons, correct semantic click used. First guessed exact Student button wait had no matches; actual accessible name is «Сменить роль, активная роль: студент». Neither tool refusal indicates product failure or changed product code. No repeat of accepted source/typecheck/build required.

## After the agreed Stop/Start cycle

6. main42cbc547: browser reload first displayed the checking state, then restored the same HEADMAN session and Today group empty state without login/HTTP404. No second role round-trip or business matrix. Screenshot: headman-home-after-restart.png, retained deliverable. The API owner separately accepted fresh ADMIN readback of the same group/four users/headman and fresh-role tickets in its original matrix.
