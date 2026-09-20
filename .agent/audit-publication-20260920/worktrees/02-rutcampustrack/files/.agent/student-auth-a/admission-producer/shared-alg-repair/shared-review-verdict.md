FAIL — immutable shared-security subset небезопасно выпускать в B1a.

1. **HIGH — валидатор не ограничивает алгоритм подписи RS256.**  
   Файл: [InternalJwtValidator.java:52](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java:52).  
   Evidence: `verifyWith(RSA public key)` использует полный registry `Jwts.SIG`; ни `.sig()` allowlist, ни проверка `parsed.getHeader().getAlgorithm()` отсутствуют. JJWT 0.12.6 подключён в [build.gradle.kts:24](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/build.gradle.kts:24).  
   Impact: корректно подписанный тем же Auth RSA key токен с RS384/RS512/PS256 проходит cryptographic boundary, хотя frozen wire разрешает только RS256.  
   Repro: подписать тот же canonical payload через `Jwts.SIG.RS512` и вызвать `validator.validate(token)`; текущая конфигурация parser допускает алгоритм.  
   Correction: до parse ограничить registry до `Jwts.SIG.RS256`, например `.sig().clear().add(Jwts.SIG.RS256).and()`, и проверить raw JOSE header на точный `alg=RS256` без duplicate keys.

2. **HIGH — public record API не совпадает с frozen contract.**  
   Файл: [InternalJwtClaims.java:19](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtClaims.java:19).  
   Evidence: component объявлен как `boolean isHeadman`, поэтому accessor — `isHeadman()`. Frozen contract требует component `boolean headman` и accessor `headman()`.  
   Impact: downstream, собранный по frozen B1a contract, не компилируется; reflection/serialization также видит другое имя компонента. Сохранение старого accessor было бы запрещённым compatibility layer.  
   Repro: скомпилировать `claims.headman()` против текущего record — `cannot find symbol`; доступен только `claims.isHeadman()`.
   Correction: переименовать component в `headman`, обновить shared tests и целевые consumers; не добавлять alias/default overload.

3. **MEDIUM — контрактные time bounds ослаблены configurable skew.**  
   Файл: [InternalJwtValidator.java:57](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java:57), [InternalJwtValidator.java:153](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java:153).  
   Evidence: skew применяется и внутри JJWT, и в собственных сравнениях. Downstream-конфигурации задают 30 секунд, например [mobile-bff application.yml:27](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/mobile-bff/mobile-bff-app/src/main/resources/application.yml:27). Frozen contract требует `iat <= now` и `exp > now` без допуска.  
   Impact: принимаются ещё не выпущенные и уже истёкшие identity tokens; фактическое окно — до настроенного skew.  
   Repro: при `clockSkewSeconds=30` подписать canonical token с `iat=now+20s` либо `exp=now-1s`; текущие проверки допускают его.  
   Correction: применять zero skew для frozen internal JWT и сравнивать с одним зафиксированным `now`; добавить проверки обоих граничных случаев.

4. **MEDIUM — зелёные tests не доказывают критичные границы.**  
   Файлы: [InternalJwtValidatorTest.java:30](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java:30), [InternalJwtValidatorTest.java:213](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java:213), [DualModeUserContextFilterTest.java:54](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/DualModeUserContextFilterTest.java:54).  
   Evidence: validator tests используют skew `0`; все generated/raw tokens подписаны только RS256; duplicate payload/header keys не тестируются; filter test проверяет лишь `userId`, а второй — `userId/role`, не полный tuple.  
   Impact: suite остаётся зелёной при findings 1 и 3 и не защищает custom raw parser или полную передачу identity record.  
   Repro: добавить negative RS512 case — он выявит finding 1; убрать duplicate-key rejection — существующие девять tests этого не заметят; сравнить полный ожидаемый `InternalJwtClaims` с captured filter value — текущего assertion нет.  
   Correction: добавить RS512/PS256/header-duplicate/payload-duplicate negatives, nonzero-skew boundary cases, wrong types и out-of-range для всех числовых claims, обе стороны headman/readOnly invariants и equality полного record после filter.

Repair contract:

- **Defect:** неверное имя record component, отсутствие RS256 allowlist, разрешённый skew, недостаточная security verification.
- **Evidence:** строки и воспроизведения выше; существующий runtime не покрывает дефекты.
- **Correction:** выполнить три функциональные правки и расширить contract tests без compatibility overloads/defaults.
- **Scope:** `InternalJwtClaims`, `InternalJwtValidator`, оба shared tests и только необходимые consumer accessor updates; Auth admission/Gateway/issuer остаются отдельным scope.
- **Verification:** exact shared-security test selectors; negative RS512/PS256, duplicate JSON, future `iat`, expired `exp`, полный filter tuple; затем обязательный fresh independent recheck затронутого immutable diff.

Текущий frozen source повторно сверен непосредственно перед вердиктом:

- Claims: `91F3605EB1FC14075F81D285F11BB0F678ED650B228F17DD63D2710EF042E961`, 559 bytes.
- Validator: `E97A2F67C9FD4D2ABACB7D6592280A7C1CF4E1DAEB6CC23BA9D5C9A872802BA7`, 19220 bytes.
- Factory: `DF85909E461FDAB4EAF0559202711C663737E2909106E8786A7B365635304A4B`, 3257 bytes.
- Validator test: `33E58D2DFD46EB28095C9C354D107BD21EEB5A85F5861E699793CBF3BE7D139C`, 10191 bytes.
- Filter test: `5B168C29A741215755A530B069B274D4081767C95FB492BD0C6A668AF43B8295`, 7916 bytes.

Открытые XML подтверждают 9/9 и 8/8 green: `275DDE88BCA2D6771A6C7F0D0861B6999CA115FA755CED256FE3C02C7A0977EC` и `E228FE5D8822B8E483A2733129942B06560DF1601F31FD533600897B5C83182F`. Новые Gradle/Docker checks по запрету не запускались.
