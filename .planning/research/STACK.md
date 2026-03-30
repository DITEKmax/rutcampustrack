# Technology Stack — Academic Service Additions

**Project:** RutCampusTrack v2.0 — Academic Service
**Researched:** 2026-03-30
**Scope:** NEW capabilities only. Java 21, Spring Boot 3.4, Gradle Kotlin DSL, Flyway, Testcontainers, PostgreSQL, Redis, RabbitMQ, contract-first pattern — all validated in v1.0 and NOT re-researched here.

---

## New Capabilities Required

| Capability | Verdict |
|-----------|---------|
| gRPC server (academic.proto, 7 RPCs) | `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE` |
| Proto code generation (Gradle) | `com.google.protobuf` plugin `0.9.4` + `io.grpc:protoc-gen-grpc-java:1.68.0` |
| Spring method security (`@PreAuthorize` on header-injected role) | `spring-boot-starter-security` — already used in auth-service, add to academic-app |
| Redis `@Cacheable` with per-cache TTL | `spring-boot-starter-data-redis` already in `academic-app/build.gradle.kts`, needs `RedisCacheConfiguration` bean |
| RabbitMQ event publishing | `spring-boot-starter-amqp` already in `academic-app/build.gradle.kts`, needs `RabbitTemplate` + exchange declaration |
| HATEOAS pagination | `spring-boot-starter-hateoas` already in `academic-app/build.gradle.kts`, use `PagedResourcesAssembler` or custom `PagedResponse` record |

---

## gRPC Server

### Library Decision: `net.devh:grpc-spring-boot-starter`

**Why this library, not `org.springframework.grpc`:**

`spring-grpc` 1.0 GA was released December 2025. It requires Spring Boot 4. The project is currently on Spring Boot 3.4.1 — using `spring-grpc` 1.0 would force a Spring Boot 4 upgrade, which is out of scope. Use `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE` (compiled against Spring Boot 3.2.4, tested compatible with 3.x broadly, grpc-ecosystem official maintenance, 3.7k stars, last release April 2024). The library is stable and broadly used; no issues reported with Spring Boot 3.4.

**Confidence:** MEDIUM — net.devh 3.1.0.RELEASE was not explicitly tested against Spring Boot 3.4, but it declares compatibility with a "large variety of versions" beyond its 3.2.4 baseline. The project already has `// implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")` commented in `academic-app/build.gradle.kts`, which confirms this was the pre-decided choice.

### gRPC Version Stack

| Artifact | Version | Role |
|---------|---------|------|
| `net.devh:grpc-spring-boot-starter` | `3.1.0.RELEASE` | Embeds gRPC server into Spring Boot, `@GrpcService` annotation, port management |
| `io.grpc:grpc-stub` | `1.68.0` | Runtime stubs (used by generated code) |
| `io.grpc:grpc-protobuf` | `1.68.0` | Protobuf message support |
| `io.grpc:grpc-netty-shaded` | (transitive via starter) | Netty transport — do NOT add separately to avoid version conflicts |
| `com.google.protobuf:protobuf-java` | `3.25.5` | Protobuf runtime (protobuf 3.x, NOT 4.x — grpc-java 1.68 ships with proto3 runtime) |

**Why NOT grpc-java 1.80.0:** The `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE` was built against grpc-java `1.63.0`. Overriding the transitive grpc-java version to 1.80 without upgrading the starter risks API breakage. Keep grpc-java versions aligned with what the starter ships. The starter manages grpc-java transitively; only add explicit `grpc-stub` and `grpc-protobuf` if you need to reference generated classes directly.

### Proto Code Generation (Gradle Kotlin DSL)

The proto files live in `proto/` at the repository root. `academic-app` must run code generation for `academic.proto`.

```kotlin
// academic-app/build.gradle.kts additions

plugins {
    id("com.google.protobuf") version "0.9.4"
}

// In dependencies:
implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")
implementation("io.grpc:grpc-stub:1.68.0")
implementation("io.grpc:grpc-protobuf:1.68.0")
// javax.annotation required for generated stubs on Java 9+
compileOnly("javax.annotation:javax.annotation-api:1.3.2")

// After dependencies block:
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("${rootProject.projectDir}/proto")
        }
    }
}
```

**gRPC server port:** Add to `application.yml`:
```yaml
grpc:
  server:
    port: 9095   # separate from REST port 9091; internal only, not exposed via Gateway
```

**`@GrpcService` usage:**
```java
@GrpcService
public class AcademicGrpcServiceImpl extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {
    // override GetGroup, GetGroupMembers, etc.
}
```

---

## Spring Security — Method-Level Authorization from Gateway Headers

### Problem

Academic Service sits behind the Gateway. The Gateway already validates JWT and injects `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` headers. Academic Service must NOT re-validate JWT (it has no public key infrastructure), but must enforce `@PreAuthorize("hasRole('ADMIN')")` checks.

### Solution: Stateless Pre-Auth Filter

Add `spring-boot-starter-security` (not yet in `academic-app/build.gradle.kts`). Configure a `OncePerRequestFilter` that reads the `X-User-Role` header and populates a `UsernamePasswordAuthenticationToken` into the `SecurityContext` before Spring Security evaluates `@PreAuthorize`.

```java
// HeaderAuthenticationFilter extends OncePerRequestFilter
String role = request.getHeader("X-User-Role");     // e.g. "ADMIN"
String userId = request.getHeader("X-User-Id");
if (role != null) {
    var auth = new UsernamePasswordAuthenticationToken(
        userId, null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
    );
    SecurityContextHolder.getContext().setAuthentication(auth);
}
filterChain.doFilter(request, response);
```

```java
// SecurityConfig
@EnableMethodSecurity          // enables @PreAuthorize
http.csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
    .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

Usage in controllers:
```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/users")
ResponseEntity<EntityModel<UserResponse>> createUser(...);

@PreAuthorize("hasAnyRole('ADMIN', 'HEADMAN')")
@GetMapping("/groups/{id}/members")
ResponseEntity<PagedModel<EntityModel<...>>> getMembers(...);
```

**IMPORTANT:** `@PreAuthorize` only fires on Spring-managed beans via AOP proxy. Do NOT annotate `private` methods. Do NOT call annotated methods from within the same class.

**What NOT to add:** Do NOT add JWT parsing dependencies to academic-app. Do NOT configure `oauth2ResourceServer()`. The auth boundary is the Gateway.

**Confidence:** HIGH — this pattern (header-based pre-auth filter for downstream services) is well-established in Spring Security microservice architecture. Spring Security 6 (shipped with Spring Boot 3.x) fully supports it.

---

## Redis `@Cacheable` — Per-Cache TTL Configuration

`spring-boot-starter-data-redis` is already declared. What's missing is the `RedisCacheManager` configuration bean with:
1. JSON serialization (not Java default serialization — the latter is fragile across deploys)
2. Per-cache TTL aligned with data volatility

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        var json = new GenericJackson2JsonRedisSerializer();
        var defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(json));

        return RedisCacheManager.builder(cf)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("group:info",
                defaultConfig.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("group:members",
                defaultConfig.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("semester:active",
                defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("campus:geofence",
                defaultConfig.entryTtl(Duration.ofHours(6)))
            .withCacheConfiguration("teacher:subjects",
                defaultConfig.entryTtl(Duration.ofMinutes(15)))
            .build();
    }
}
```

**Cache key strategy:** The cache names above match the keys documented in `phases-plan.md`: `group:{id}:info`, `group:{id}:members`, `teacher:{id}:subjects`, `semester:active`, `campus:geofence`. For dynamic keys use `@Cacheable(value = "group:info", key = "#groupId")`.

**Invalidation via `@CacheEvict`:**
```java
@CacheEvict(value = "group:members", key = "#groupId")
public void transferStudent(Long groupId, ...) { ... }

@CacheEvict(value = "semester:active", allEntries = true)
public void archiveSemester(...) { ... }
```

**What NOT to do:** Do NOT use default Java serialization — it breaks if you deploy a new JAR with changed class signatures. Do NOT use `@CacheEvict(allEntries = true)` on group:info unless every group is truly stale.

**No new dependency required.** `spring-boot-starter-data-redis` includes `spring-data-redis` which includes `RedisCacheManager` and `GenericJackson2JsonRedisSerializer`.

**Confidence:** HIGH — official Spring Data Redis docs confirm this API is stable and unchanged in Spring Boot 3.x.

---

## Spring AMQP — RabbitMQ Event Publishing

`spring-boot-starter-amqp` is already declared. What's needed is exchange/queue declaration beans and a typed publisher.

### Exchange Declaration

```java
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "rut-uit.events";

    @Bean
    public FanoutExchange eventExchange() {
        return new FanoutExchange(EXCHANGE, true, false);
        // durable=true, autoDelete=false
    }
}
```

Do NOT declare queues in academic-service — queues belong to the consuming services (notification-web, notification-bot). Academic Service is a producer only.

### Event Publishing Pattern

```java
@Service
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String eventType, Object payload) {
        var message = Map.of("type", eventType, "payload", payload,
                             "timestamp", Instant.now().toString());
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, "", message);
        // routingKey="" is correct for fanout exchanges — it is ignored
    }
}
```

**Events to publish:**
- `group.updated` — when group membership changes (transfer, headman change)
- `semester.archived` — when a semester is deactivated
- `homework.published` — when a new homework is created
- `homework.updated` — when an existing homework is modified

**What NOT to do:** Do NOT configure a `@RabbitListener` in academic-service for these events — Academic Service is event producer only in this milestone. Do NOT use `DirectExchange` — the architecture uses `fanout` so all notification consumers receive all events.

**No new dependency required.**

**Confidence:** HIGH — `RabbitTemplate.convertAndSend(exchange, routingKey, payload)` is the standard Spring AMQP publish API. Fanout exchange ignores routing key (empty string is idiomatic).

---

## HATEOAS Pagination

### Decision: Use `PagedResourcesAssembler` (built-in), NOT a custom `PagedResponse` record

`phases-plan.md` mentions "свой record `PagedResponse<T>`" but the contract module already has `spring-hateoas:2.4.1` as an `api` dependency and `academic-app` has `spring-boot-starter-hateoas`. Spring HATEOAS provides `PagedModel<EntityModel<T>>` with proper `_links` (self, first, prev, next, last) generated by `PagedResourcesAssembler`. This is the idiomatic Level 3 HATEOAS approach and is already on the classpath.

A custom `PagedResponse` record would replicate what `PagedModel` already does, without HATEOAS link generation. **Use `PagedModel`.**

```java
// Controller signature (in api-contract interface):
ResponseEntity<PagedModel<EntityModel<UserResponse>>> getUsers(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    PagedResourcesAssembler<UserResponse> assembler
);

// App implementation:
Page<User> userPage = userRepository.findAll(PageRequest.of(page, size));
Page<UserResponse> dtoPage = userPage.map(userMapper::toResponse);
return ResponseEntity.ok(assembler.toModel(dtoPage, userAssembler));
```

**What NOT to add:** Do NOT add `spring-data-web` separately — it is transitive via `spring-boot-starter-data-jpa`. `PagedResourcesAssembler` is auto-configured by Spring HATEOAS when `spring-boot-starter-hateoas` is on the classpath.

**Confidence:** HIGH — `PagedResourcesAssembler` is official Spring HATEOAS API, stable since Spring HATEOAS 1.x, documented at docs.spring.io.

---

## Complete `academic-app/build.gradle.kts` Delta

Starting from the existing file, add:

```kotlin
plugins {
    // ADD:
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    // EXISTING (keep all):
    implementation(project(":services:academic-service:academic-api-contract"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // ADD — gRPC server:
    implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")
    implementation("io.grpc:grpc-stub:1.68.0")
    implementation("io.grpc:grpc-protobuf:1.68.0")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    // ADD — method security:
    implementation("org.springframework.boot:spring-boot-starter-security")

    // KEEP:
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ADD — proto code generation:
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins { create("grpc") }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("${rootProject.projectDir}/proto")
        }
    }
}
```

---

## What NOT to Add

| Item | Reason |
|------|--------|
| `org.springframework.grpc:spring-grpc-spring-boot-starter` | Requires Spring Boot 4 — incompatible with current Spring Boot 3.4 baseline |
| `io.grpc:grpc-netty-shaded` (explicit) | Transitive via `net.devh` starter — adding explicitly risks version conflict |
| JWT parsing (`jjwt-*`) | Auth is Gateway's responsibility; academic-service trusts injected headers |
| `oauth2ResourceServer()` | Same reason — no JWT in academic-service |
| Custom `PagedResponse` record | Spring HATEOAS `PagedModel` already provides this with link generation |
| Separate `spring-data-web` | Transitive via `spring-boot-starter-data-jpa` |
| Testcontainers for Redis | `@SpringBootTest` slice with `spring-boot-starter-data-redis` can use EmbeddedRedis or a real container; follow auth-service pattern with a `redis` Testcontainers module if integration tests need it |

---

## `application.yml` Additions Required

```yaml
# ADD to academic-app application.yml:
grpc:
  server:
    port: 9095

spring:
  cache:
    type: redis
  security:
    # no JWT config — header-based auth only
```

---

## Sources

- net.devh grpc-spring-boot-starter releases: https://github.com/grpc-ecosystem/grpc-spring/releases (HIGH confidence — official repo)
- grpc-java releases: https://github.com/grpc/grpc-java/releases (HIGH confidence — official repo)
- protobuf-gradle-plugin: https://github.com/google/protobuf-gradle-plugin (HIGH confidence — official repo)
- Spring Data Redis Cache docs: https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html (HIGH confidence)
- Spring HATEOAS PagedModel: https://docs.spring.io/spring-hateoas/docs/current/api/org/springframework/hateoas/PagedModel.html (HIGH confidence)
- Spring AMQP RabbitMQ fanout: https://rabbitmq.com/tutorials/tutorial-three-spring-amqp.html (HIGH confidence)
- Spring Security Method Security: https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html (HIGH confidence)
- spring-grpc 1.0 GA requires Boot 4: confirmed at https://spring.io/blog/2025/12/04/spring-grpc-1/ (MEDIUM — page returned only CSS, but blog title confirms 1.0 GA Dec 2025; combined with search result stating Spring Boot 4 dependency)
