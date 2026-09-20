# PASS — консультационное решение

PASS относится к выбору архитектуры repair. Текущее security-состояние остаётся **FAIL**: исходный Trivy rootfs scan содержит 51 уникальный HIGH/CRITICAL finding. Dependency repair ещё не реализован и не проверен.

Revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; dependency diff отсутствует, checkout содержит чужие изменения. Проверки не запускались — review был read-only.

## Writer contract

1. **Goal.** Устранить 51 finding минимальным согласованным обновлением в линии Spring Boot 3 без suppressions и без заявлений об exploitability.

2. **Context/evidence.** [backend-scan-summary.json](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/security/backend-scan-summary.json) фиксирует 256 Java package records, 82 instances, 51 unique findings: 8 CRITICAL и 43 HIGH. `fs`-probe с нулём Java packages не считается оценкой; исходный rootfs scan остаётся FAIL.

3. **Relevant scope.** Один dependency writer владеет:

   - [root build](C:/Users/maksd/IntelliJIDEA/rutcampustrack/build.gradle.kts:5) и [version catalog](C:/Users/maksd/IntelliJIDEA/rutcampustrack/gradle/libs.versions.toml:5);
   - восемью `services/shared/*/build.gradle.kts`;
   - шестью API-contract builds: academic, attendance, auth, mobile-bff, notification, schedule;
   - gRPC builds academic, attendance, schedule, mobile-bff и document-renderer;
   - [Gateway build](C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/api-gateway/build.gradle.kts:11), [main YAML](C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/api-gateway/src/main/resources/application.yml:18) и [prod YAML](C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/api-gateway/src/main/resources/application-prod.yml:1).

4. **Required behavior.**

   - Spring Boot `3.5.16`; Cloud `2025.0.3`, Gateway `4.3.5`.
   - Boot-managed: Framework `6.2.19`, Data Commons `3.5.13`, HATEOAS `2.5.3`, Security `6.5.11`, Jackson annotations `2.21`, остальные Jackson `2.21.4`, Micrometer `1.15.12`, json-smart `2.5.2`.
   - Central Boot overrides: `netty.version=4.1.137.Final`, `tomcat.version=10.1.59`, `rabbit-amqp-client.version=5.33.1`, `postgresql.version=42.7.12`.
   - gRPC BOM `1.82.4`, protobuf BOM/runtime/compiler `3.25.8`; исключить `grpc-netty-shaded` из каждого `net.devh` starter и добавить `grpc-netty`.
   - Обновить `protoc-gen-grpc-java` до `1.82.4`; убрать все смешанные gRPC `1.63.0`.
   - Заменить Gateway starter на `spring-cloud-starter-gateway-server-webflux`; перенести обе YAML-ветки в `spring.cloud.gateway.server.webflux.*`.
   - В `shared-observability` снять явный Micrometer tracing `1.4.1` и выровнять его на Boot-managed `1.5.12`.

5. **Constraints.** Сохранить Boot 3, Java 21, все публичные DTO, route semantics, схемы данных и протоколы. Starter `net.devh:3.1.0.RELEASE` остаётся, но его совместимость требует runtime-проверки: upstream собирал его с Boot 3.2.4, gRPC 1.63 и protobuf 3.25.3.

6. **Existing patterns.** Boot overrides размещаются централизованно в root `subprojects`; прямые contract pins обновляются до значений Boot BOM. gRPC и protobuf выравниваются BOM, а не отдельной заменой transport JAR.

7. **Acceptance criteria.**

   - Все affected graphs разрешаются строго в перечисленные версии.
   - `grpc-netty-shaded` отсутствует; все `io.grpc` равны `1.82.4`, protobuf — `3.25.8`.
   - Gateway сохраняет ровно 21 route с прежними IDs, URI, predicates, filters и порядком.
   - Все BootJars содержат ожидаемые версии.
   - Новый полный Trivy rootfs scan возвращает ноль HIGH/CRITICAL для ремонтируемого набора без ignore/suppression.

8. **Verification.** Нужны `dependencyInsight` для всех 19 координат и полного gRPC/Netty/protobuf graph; clean `check` и `bootJar` всех apps/shared/contracts; Gateway CORS, 429, Redis rate-limit, WebSocket, OpenAPI, auth/header stripping; реальные gRPC client/server, TLS, deadline/error/retry; PostgreSQL/Flyway/JPA, Rabbit/outbox и Redis/cache. Rescan должен охватить все backend BootJars и обнаружить не меньше прежних 256 Java records. После исправления требуется свежая независимая Sol high recheck.

9. **Do not.** Не переходить на Boot 4 в этом repair, не использовать gRPC 1.83.x/Netty 4.2, не подавлять findings и не считать успешную компиляцию security PASS.

Boot 3.5.16 — последняя OSS-версия линии 3.5, поэтому EOL остаётся отдельным lifecycle-риском; будущая Boot 4 migration требует отдельного решения владельца. Официальный migration guide сам рекомендует сначала перейти на актуальную 3.5.x; Boot 4 дополнительно вводит Framework 7, Jakarta EE 11/Servlet 6.1, Jackson 3 и модульные изменения. [Boot 3.5.16 release](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/), [Boot coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html), [Cloud compatibility](https://spring.io/projects/spring-cloud/), [Cloud 2025.0 notes](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes), [Boot 4 migration](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).

gRPC `1.82.4` сохраняет Netty 4.1 и содержит дополнительное исправление ограничения streams; `1.83.0` перешёл на Netty 4.2. [gRPC 1.82.4](https://github.com/grpc/grpc-java/releases/tag/v1.82.4), [dependency catalog](https://raw.githubusercontent.com/grpc/grpc-java/v1.82.4/gradle/libs.versions.toml), [gRPC 1.83.0](https://github.com/grpc/grpc-java/releases/tag/v1.83.0), [grpc-spring 3.1.0 source](https://raw.githubusercontent.com/grpc-ecosystem/grpc-spring/v3.1.0.RELEASE/build.gradle).

Patch releases подтверждены первичными источниками: [Netty 4.1.137](https://netty.io/news/2026/08/06/4-1-137-Final.html), [Tomcat 10.1.59](https://tomcat.apache.org/security-10.html), [RabbitMQ client 5.33.1](https://github.com/rabbitmq/rabbitmq-java-client/releases/tag/v5.33.1), [pgJDBC 42.7.12](https://github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.12).

Отдельный finding, вынесенный из dependency contract:

- **HIGH — [RedisRateLimiterConfig.java:107](C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfig.java:107).** Resolver доверяет первому необработанному `X-Forwarded-For`, а [nginx default.conf:99](C:/Users/maksd/IntelliJIDEA/rutcampustrack/nginx/conf.d/default.conf:99) использует `$proxy_add_x_forwarded_for`, сохраняя клиентское значение. Тест прямо закрепляет выбор первого адреса. Клиент может менять левый XFF и получать новый Redis bucket, обходя OTP/login IP limits. Воспроизведение: отправить больше разрешённого числа одинаковых запросов через nginx, меняя первый XFF; ожидаемый `429` не наступает. Исправление — отдельный bounded repair с перезаписью ingress-header и trusted-proxy-aware address resolution, затем независимая recheck.
