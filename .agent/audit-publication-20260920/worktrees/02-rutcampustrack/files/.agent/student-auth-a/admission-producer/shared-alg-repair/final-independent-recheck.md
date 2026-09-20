PASS — shared-security frozen subset готов к B1a. Блокирующих findings нет.

Проверено по оригиналам:

- [InternalJwtValidator.java:54](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java:54): raw header/payload разбираются до JJWT; header допускает только plain `alg=RS256`, обязательный непустой plain `kid` и optional plain `typ=JWT`. Затем подпись проверяется настроенным RSA public key.
- [InternalJwtValidator.java:189](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java:189): compact/section/decoded размеры ограничены; UTF-8 декодируется с `REPORT`; JSON требует единственный top-level object, отклоняет дубликаты на всех уровнях и ограничивает nesting до 32.
- [InternalJwtValidator.java:71](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java:71): canonical positive decimals, lowercase UUID, enum/type/time bounds и identity semantics проверяются до создания полного `InternalJwtClaims`.
- [JwtService.java:281](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:281): producer добавляет `kid`, не добавляет `typ`, передаёт полный frozen identity tuple и явно подписывает `Jwts.SIG.RS256`.
- Tests содержат реальные RS384/RS512/PS256 negatives, duplicate header/payload с валидной RS256 подписью, producer-compatible positive без `typ`, positive с `typ=JWT`, canonical/type/range/time/semantic negatives и полное record equality в filter.
- Ошибки не включают token material; filter журналирует только контролируемое сообщение. Configured clock skew и `isHeadman()` сохранены согласно owner adjudication.

Повторно подтверждённый frozen manifest:

- Validator: `E8633DE82330BCCB8A9805E37351F5E9F439A27FD1C95524B760592B614E3FF5`, 23230 bytes.
- Validator test: `4D3C3DD677B902F875F655584F530BA43FBDAE0DE687426EF122EAA42E190B26`, 14470 bytes.
- Filter test: `2CCB149F9FD57627BE6C18CF4B30A6DD49A234F146595A03663428C5B8EB87CB`, 7934 bytes.
- Fixture: `DA186908CBC27F752195EBBBF8A874004E183F507EF928C78B32D24910CBCF52`, 3355 bytes.
- Stable scoped diff: ровно четыре файла, `905 insertions / 132 deletions`; `git diff --check` прошёл.

Фактический SHA Validator оканчивается на `...E3FF5`; в переданном reference и `runtime-final/result.md` отсутствует последняя hex-цифра `5`. Полный SHA подтверждён current source и correction evidence, поэтому это не нарушает идентификацию frozen artifact.

Проверенный runtime: focused Gradle command завершился exit `0`, `BUILD SUCCESSFUL in 43s`, 22/22 tests passed. XML:

- Validator 14/14: `2BED126604E68670BEDD93153E8E7C9D75D850EC66039C76EF9AA68BDF997665`, 2408 bytes.
- Filter 8/8: `F15611D001CA90C93A1765A500EF873220CBFAD8A9FCD2B416D8FD2232775F31`, 2561 bytes.

Auth admission behavior, Gateway/BFF/WS consumers, Stage 2 и отдельный `InternalSessionAdmissionIT` timestamp fixture остаются последующими integration scopes и этот PASS не расширяют. Код, tests, evidence и данные не изменялись.
