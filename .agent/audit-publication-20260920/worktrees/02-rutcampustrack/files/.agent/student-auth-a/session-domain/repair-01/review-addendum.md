Дополнение к review:

- В finding 5 снимаю утверждение об отсутствии проверки normalization: `PasswordPolicyTest.java:34-37` действительно доказывает отсутствие NFC-normalization через combining-mark vector.
- Остальные gaps сохраняются: нет отдельных проверок категории `S`, lone low surrogate, current logout, expired/revoked sessions и session-command authority failures.
- Finding 4 исправляется как явная обязанность trusted B1 integration: документировать и обеспечить, что `replacementHash` получен именно из проверенного `newPassword`. BCrypt не добавляется в pure domain.

Вердикт остаётся **FAIL**; findings по suspended snapshot, `RevokeResult` и forgeable policy overload сохраняются без изменений.
