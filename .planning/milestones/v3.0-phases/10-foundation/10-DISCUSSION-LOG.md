# Phase 10: Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-01
**Phase:** 10-foundation
**Areas discussed:** Security porting, Testcontainers setup, Entity mapping details, Build dependencies

---

## Security Porting

### Q1: How to handle UserRole import difference?

| Option | Description | Selected |
|--------|-------------|----------|
| Copy + adapt (Recommended) | Copy 4 security classes from academic-service, change UserRole import to schedule-contract's enum | ✓ |
| Extract shared lib | Create shared security library module parameterizing UserRole | |
| You decide | Claude picks based on codebase conventions | |

**User's choice:** Copy + adapt (Recommended)
**Notes:** Same logic, different import. No need for shared lib with only 2 services.

### Q2: RequestContext fields

| Option | Description | Selected |
|--------|-------------|----------|
| Same fields | userId, role, groupId, isHeadman — identical to academic-service | ✓ |
| Add semesterId | Add semesterId for schedule ops, Gateway passes X-Semester-Id | |
| You decide | Claude picks based on Phase 11 API needs | |

**User's choice:** Same fields
**Notes:** None.

### Q3: Security smoke test scope

| Option | Description | Selected |
|--------|-------------|----------|
| Basic 403 only (Recommended) | Test missing headers = 403. Role-specific tests in Phase 11. | ✓ |
| Include role checks | Also test STUDENT gets 403 on headman-only dummy endpoint | |
| You decide | Claude picks minimal viable test | |

**User's choice:** Basic 403 only (Recommended)
**Notes:** None.

---

## Testcontainers Setup

### Q4: What should base test class exclude?

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror academic pattern (Recommended) | Mock RabbitTemplate, exclude RabbitAutoConfig, exclude gRPC server auto-config | ✓ |
| Minimal exclusion | Only PostgreSQL Testcontainer, add exclusions later | |
| You decide | Claude picks based on build.gradle.kts | |

**User's choice:** Mirror academic pattern (Recommended)
**Notes:** None.

### Q5: Testcontainer configuration

| Option | Description | Selected |
|--------|-------------|----------|
| Same pattern, schedule_db (Recommended) | Static container, postgres:16, schedule_db, rct_user/rct_dev_pass | ✓ |
| Custom init script | Same container + init SQL for custom types before Flyway | |
| You decide | Claude picks based on Flyway migration | |

**User's choice:** Same pattern, schedule_db (Recommended)
**Notes:** Flyway auto-runs V1__baseline.sql which creates the enum types.

---

## Entity Mapping Details

### Q6: TIME column Java type

| Option | Description | Selected |
|--------|-------------|----------|
| LocalTime (Recommended) | java.time.LocalTime — native Hibernate mapping for TIME | ✓ |
| OffsetTime | java.time.OffsetTime — schema uses TIME not TIMETZ | |
| You decide | Claude picks based on schema | |

**User's choice:** LocalTime (Recommended)
**Notes:** None.

### Q7: Logical FK fields mapping

| Option | Description | Selected |
|--------|-------------|----------|
| Plain Long fields (Recommended) | Map as Long with @Column. No JPA relationships — validated via gRPC. | ✓ |
| Wrapper objects | GroupRef, SubjectRef value objects wrapping Long ID | |
| You decide | Claude picks simplest approach | |

**User's choice:** Plain Long fields (Recommended)
**Notes:** Cross-database FKs can't be JPA relationships.

### Q8: Enum converter standardization

| Option | Description | Selected |
|--------|-------------|----------|
| Keep existing inline (Recommended) | EnumConverters.java already works. Only 2 enums — base class is overkill. | ✓ |
| Add base class | Create LowercaseEnumConverter<E> like academic-service | |
| You decide | Claude picks based on pragmatism | |

**User's choice:** Keep existing inline (Recommended)
**Notes:** Already committed and working.

### Q9: Lesson → ScheduleItem relationship

| Option | Description | Selected |
|--------|-------------|----------|
| @ManyToOne (Recommended) | LAZY fetch. Enables getScheduleItem() for Phase 11 schedule viewing. | ✓ |
| Plain Long | Manual joins in queries. Simpler entity, more service-layer work. | |
| You decide | Claude picks based on Phase 11 needs | |

**User's choice:** @ManyToOne (Recommended)
**Notes:** Both entities in same DB — real JPA relationship is appropriate.

---

## Build Dependencies

### Q10: Dependency set for Phase 10

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal for Phase 10 (Recommended) | Add AOP + Testcontainers only. Defer gRPC to Phase 14. | ✓ |
| Include gRPC now | Add gRPC server starter + protobuf plugin now | |
| Full future-proof | AOP + gRPC + protobuf + Testcontainers + MapStruct | |

**User's choice:** Minimal for Phase 10 (Recommended)
**Notes:** None.

### Q11: gRPC port config without gRPC starter

| Option | Description | Selected |
|--------|-------------|----------|
| Defer gRPC deps, keep port config | No gRPC starters in build.gradle.kts. Keep grpc.server.port: 19092 in application.yml as placeholder. | ✓ |
| Add gRPC deps now after all | Include gRPC server starter so port config actually works | |
| You decide | Claude picks based on success criteria | |

**User's choice:** Defer gRPC deps, keep port config
**Notes:** Property is harmless without gRPC starter on classpath.

---

## Claude's Discretion

- Package structure within schedule-app
- @RequireRole annotation design and RoleCheckAspect implementation details
- ClockConfig and SchedulingConfig implementation
- Exact Flyway migration handling

## Deferred Ideas

None — discussion stayed within phase scope.
