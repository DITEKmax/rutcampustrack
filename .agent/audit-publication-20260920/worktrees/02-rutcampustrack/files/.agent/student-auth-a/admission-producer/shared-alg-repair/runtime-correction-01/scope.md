# Scope

## In scope

Одна строка в `InternalJwtValidator.java`: удаление
`.sig().clear().add(Jwts.SIG.RS256).and()` между `verifyWith(...)` и
`requireIssuer(...)`. Это устраняет конкретный JJWT callback defect, сохраняя
raw header algorithm gate до JJWT parse.

## Out of scope

Тесты, producer, test fixture, `InternalJwtClaims`, Auth/Profile/Contracts,
конфигурация, schema, clock skew, runtime, Gradle/Docker и все чужие dirty paths.

## Ownership

Writer scope ограничен target source и новой папкой
`.agent/student-auth-a/admission-producer/shared-alg-repair/runtime-correction-01/`.
