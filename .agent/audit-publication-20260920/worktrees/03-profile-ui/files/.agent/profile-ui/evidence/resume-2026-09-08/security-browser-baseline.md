# Mounted SecurityScreen baseline — before correction

- observed: 2026-09-08 in the task-owned Codex In-app Browser tab
- URL: http://127.0.0.1:18110/
- harness/runtime: own Vite PID 47364, strict 127.0.0.1:18110, evidence-only fixture callbacks
- setup: selected Безопасность; entered three valid test-only secrets into the mounted SecurityScreen; then changed the parent error prop to ACCOUNT_INVALIDATED through the harness control
- visible error: Аккаунт больше недоступен
- defect reproduction: after the invalidating error was visible, clicking Сменить пароль invoked the harness callback; observable output changed from Отправок: 0 to Отправок: 1 and Последний результат: получен
- impact: the component accepted a password mutation while account invalidation persisted; this is the browser reproduction for the requested correction
- limitation: the resolved callback then cleared inputs through the existing success path, so this baseline records the unsafe submission signal rather than persisting secret values in evidence
