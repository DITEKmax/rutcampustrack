# Testing Patterns

**Analysis Date:** 2026-04-08

## Java Testing Framework

**Runner:**
- JUnit 5 (Jupiter)
- Config: `build.gradle.kts` in each service with `useJUnitPlatform()`

**Assertion Library:**
- AssertJ (fluent assertions)

**Containers & Infrastructure:**
- Testcontainers 1.20.4 (BOM managed)
- Supported containers: MongoDB, PostgreSQL, RabbitMQ, Redis

**Run Commands:**
```bash
./gradlew test              # Run all tests
./gradlew test --watch      # Watch mode (not standard; use IDE)
./gradlew test --tests "*IntegrationTest"  # Filter by pattern
./gradlew jacocoTestReport  # Coverage report (if configured)
```

## Java Test File Organization

**Location:**
- Unit tests: `src/test/java/ru/rutcampustrack/{service}/.../{Class}Test.java`
- Integration tests: `src/test/java/ru/rutcampustrack/{service}/integration/{Feature}IntegrationTest.java` or `{Feature}Test.java`

**Naming:**
- Unit tests: `{ClassName}Test.java` (tests a single class in isolation)
- Integration tests: `{FeatureName}IntegrationTest.java` or `Abstract{FeatureName}IntegrationTest.java`
- Examples:
  - `MarkingServiceTest.java` (unit test for MarkingService)
  - `MarkingIntegrationTest.java` (integration test with HTTP stack)
  - `AbstractAttendanceIntegrationTest.java` (base class for all Attendance integration tests)

**Structure example:**
```
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/
├── checkin/
│   └── CheckinServiceTest.java
├── marking/
│   └── MarkingServiceTest.java
├── report/
│   └── ReportServiceTest.java
│   └── ReportDomainIsolationTest.java
├── grpc/
│   ├── AcademicGrpcClientTest.java
│   └── ScheduleGrpcClientTest.java
├── integration/
│   ├── AbstractAttendanceIntegrationTest.java
│   ├── MarkingIntegrationTest.java
│   ├── CheckinIntegrationTest.java
│   ├── ReportIntegrationTest.java
│   └── RabbitConsumerTest.java
├── event/
│   └── LessonEventServiceTest.java
├── ratelimit/
│   └── CheckinRateLimiterTest.java
└── ...
```

## Java Test Structure

**Unit Test (Mockito):**
```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarkingServiceTest {
  @Mock
  private ScheduleGrpcClient scheduleGrpcClient;
  
  @Mock
  private MongoTemplate mongoTemplate;
  
  @InjectMocks
  private MarkingService markingService;
  
  @BeforeEach
  void setUp() {
    // Setup happy path defaults via lenient().when()
    lenient().when(scheduleGrpcClient.getLessonById(LESSON_ID))
      .thenReturn(buildLesson(GROUP_ID));
  }
  
  @Test
  void markAttendance_headmanMarksStudentInGroup_upsertsAndPublishesEvent() {
    AttendanceDocument result = markingService.markAttendance(
      LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.PRESENT));
    
    verify(mongoTemplate).upsert(any(), any(), eq(AttendanceDocument.class));
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
  }
  
  @Test
  void markAttendance_notHeadman_throwsAccessDeniedException() {
    when(requestContext.isHeadman()).thenReturn(false);
    
    assertThatThrownBy(() -> 
      markingService.markAttendance(LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.PRESENT))
    ).isInstanceOf(AccessDeniedException.class);
  }
}
```

**Integration Test (Testcontainers):**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractAttendanceIntegrationTest {
  @Autowired
  protected MockMvc mockMvc;
  
  @Autowired
  protected MongoTemplate mongoTemplate;
  
  @MockitoBean
  protected ScheduleGrpcClient scheduleGrpcClient;
  
  @MockitoBean
  protected AcademicGrpcClient academicGrpcClient;
  
  static final MongoDBContainer MONGODB;
  static final RabbitMQContainer RABBITMQ;
  static final GenericContainer<?> REDIS;
  
  static {
    MONGODB = new MongoDBContainer("mongo:7.0");
    MONGODB.start();
    RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management");
    RABBITMQ.start();
    REDIS = new GenericContainer<>("redis:7.2").withExposedPorts(6379);
    REDIS.start();
  }
  
  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
    registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
  }
}
```

**Specific test class extending abstract base:**
```java
class MarkingIntegrationTest extends AbstractAttendanceIntegrationTest {
  @Autowired
  private ObjectMapper objectMapper;
  
  @MockitoSpyBean
  protected AttendanceEventPublisher attendanceEventPublisher;
  
  @BeforeEach
  void setUp() {
    mongoTemplate.remove(new Query(), AttendanceDocument.class);
    Mockito.reset(scheduleGrpcClient, academicGrpcClient);
  }
  
  @Test
  void mark_headmanMarksStudentInGroup_returns200WithHateoas() throws Exception {
    mockHappyPath();
    
    mockMvc.perform(put("/attendance/lessons/{lessonId}/students/{userId}", LESSON_ID, USER_ID)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new MarkRequest(AttendanceStatus.PRESENT)))
        .header("X-User-Id", HEADMAN_USER_ID.toString())
        .header("X-User-Role", "STUDENT"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("PRESENT"))
      .andExpect(jsonPath("$._links.self").exists());
    
    verify(attendanceEventPublisher).publishMarked(any());
  }
}
```

**Patterns:**
- `@BeforeEach` runs before each test method; use for setup and cleanup
- `lenient().when()` for default mocks (doesn't fail if unused)
- `when(...).thenReturn()` for specific test behavior
- `verify()` for assertion on mock interactions
- `assertThat()` (AssertJ) for value assertions
- `@MockitoBean` — Spring-managed mocks (used in `@SpringBootTest`)
- `@MockitoSpyBean` — real object with partial mocking
- `mongoTemplate.remove(new Query(), EntityClass.class)` for cleanup

## Java Mocking Patterns

**Framework:** Mockito

**Patterns:**
```java
// Default behavior (lenient — won't fail if unused)
lenient().when(client.method()).thenReturn(value);

// Strict behavior (fails if mock not called)
when(client.method()).thenReturn(value);

// With matchers
when(client.getById(eq(123L))).thenReturn(obj);
when(client.save(any(Entity.class))).thenReturn(saved);

// Verify calls
verify(client).method();
verify(client, times(2)).method();
verify(client, never()).method();

// Spy on real object
@MockitoSpyBean
private EventPublisher publisher;

@Test
void test() {
  doNothing().when(publisher).publish(any());
  
  // ... test code ...
  
  verify(publisher).publish(argThat(event -> event.getType().equals("marked")));
}
```

**What to Mock:**
- External gRPC clients (ScheduleGrpcClient, AcademicGrpcClient)
- External REST clients
- Infrastructure (GeofenceService, SemesterCacheService)
- Third-party APIs

**What NOT to Mock:**
- Repository/DAO objects (use real data store via Testcontainers)
- Service objects under test
- Domain models (AttendanceDocument, Lesson, etc.)

## Java ArchUnit Tests

**Purpose:** Enforce architecture rules at compile-time

**Example: Domain Isolation Test**
```java
@AnalyzeClasses(packages = "ru.rutcampustrack.attendance")
class ReportDomainIsolationTest {
  @ArchTest
  static final ArchRule reportDoesNotImportCheckin =
    noClasses()
      .that().resideInAPackage("ru.rutcampustrack.attendance.report..")
      .should().dependOnClassesThat()
      .resideInAPackage("ru.rutcampustrack.attendance.checkin..");
}
```

**File location:** `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportDomainIsolationTest.java`

**Dependencies:** `com.tngtech.archunit:archunit-junit5:1.3.0`

## Java REST Testing (MockMvc)

**Pattern:**
```java
mockMvc.perform(post("/api/attendance/checkin")
    .contentType(MediaType.APPLICATION_JSON)
    .content(objectMapper.writeValueAsString(request))
    .header("X-User-Id", "1"))
  .andExpect(status().isCreated())
  .andExpect(jsonPath("$.lessonId").exists())
  .andExpect(jsonPath("$._links.self").exists());
```

**Scope:**
- Only in integration tests with `@SpringBootTest`
- Bypasses network layer; tests full Spring stack (controllers, services, repositories)
- Uses real database (Testcontainers)

## Java gRPC Testing

**Pattern:**
```java
@ExtendWith(MockitoExtension.class)
class ScheduleGrpcClientTest {
  @Mock
  private ScheduleServiceGrpc.ScheduleServiceBlockingStub stub;
  
  @InjectMocks
  private ScheduleGrpcClient client;
  
  @Test
  void getLessonById_success_returnsLesson() {
    LessonResponse expected = LessonResponse.newBuilder()
      .setId(1L)
      .setStatus("started")
      .build();
    
    when(stub.getLesson(any())).thenReturn(expected);
    
    LessonResponse result = client.getLessonById(1L);
    assertThat(result.getStatus()).isEqualTo("started");
  }
}
```

## Frontend Testing (React - Vitest)

**Test Framework:**
- Vitest (drop-in Jest replacement)
- Config: `vitest.config.ts`

**Test Libraries:**
- `@testing-library/react` — component rendering and queries
- `@testing-library/user-event` — user interaction simulation
- `jsdom` — browser-like DOM environment

**Run Commands:**
```bash
npm run test              # Run once
npm run test:watch       # Watch mode
```

**Test File Organization:**

**Location:**
- Co-located in feature directories
- Pattern: `src/features/{feature}/__tests__/{Component}.test.tsx`

**Examples:**
- `frontends/pwa/src/features/auth/__tests__/AuthProvider.test.tsx`
- `frontends/pwa/src/features/checkin/__tests__/CheckInButton.test.tsx`
- `frontends/mini-app/src/features/auth/__tests__/AuthProvider.test.tsx`

## React Test Structure

**Pattern:**
```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import type { ReactNode } from 'react'

// Mock external modules
vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  setAccessTokenGetter: vi.fn(),
}))

// Helper to create test JWT
function createFakeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  const signature = 'fakesignature'
  return `${header}.${body}.${signature}`
}

// Wrapper for hooks
function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders children and returns isAuthenticated=false initially', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
  })

  it('after login() succeeds, returns isAuthenticated=true and user object', async () => {
    const fakeToken = createFakeJwt({ sub: '1', role: 'STUDENT', groupId: 5 })
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: fakeToken, expiresIn: 900 },
    })

    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login({ login: 'student00001', password: 'pass' })
    })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual({ id: 1, role: 'STUDENT', groupId: 5 })
  })
})
```

**Patterns:**
- `vi.mock()` for module mocking
- `renderHook()` for testing custom hooks
- `act()` for state updates
- `wrapper` for providing context (e.g., AuthProvider)
- `beforeEach(() => vi.clearAllMocks())` — reset mocks between tests

## Angular Testing (Vitest + @angular/core/testing)

**Test Framework:**
- Vitest (same as React)
- Config: `vitest.config.ts`
- TestBed API from `@angular/core/testing`

**Run Commands:**
```bash
npm run test              # Run once
npm run test:watch       # Watch mode
```

**Test File Organization:**

**Location:**
- Co-located with component/service
- Pattern: `src/app/{path}/{name}.spec.ts` (same directory as source)

**Examples:**
- `frontends/web-panel/src/app/core/auth/auth.service.spec.ts` (next to `auth.service.ts`)
- `frontends/web-panel/src/app/features/teacher/journal/journal-page.component.spec.ts`

## Angular Test Structure

**Pattern:**
```typescript
import { TestBed } from '@angular/core/testing'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { AuthService } from './auth.service'
import { AuthApi } from './auth.api'

// Helper JWT creation
const makeJwt = (payload: object): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  return `${header}.${body}.signature`
}

const ACCESS_TOKEN = makeJwt({ sub: '1', role: 'TEACHER', exp: 9999999999 })
const REFRESH_TOKEN = 'refresh-token-abc'

describe('AuthService', () => {
  let service: AuthService

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService],
    })
    service = TestBed.inject(AuthService)
  })

  it('isAuthenticated() returns false initially', () => {
    expect(service.isAuthenticated()).toBe(false)
  })

  it('setTokens(access, refresh) makes isAuthenticated() return true', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN)
    expect(service.isAuthenticated()).toBe(true)
  })

  it('currentUser() returns null when no token set', () => {
    expect(service.currentUser()).toBeNull()
  })

  it('currentUser() returns { id, role } parsed from JWT payload after setTokens', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN)
    const user = service.currentUser()
    expect(user).not.toBeNull()
    expect(user!.id).toBe(1)
    expect(user!.role).toBe('TEACHER')
  })

  it('clearTokens() makes isAuthenticated() return false', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN)
    service.clearTokens()
    expect(service.isAuthenticated()).toBe(false)
  })
})
```

**Patterns:**
- `TestBed.configureTestingModule()` — configure test providers
- `TestBed.inject(Service)` — get service instance
- `beforeEach(() => { ... })` — setup before each test
- No component rendering in these examples (unit tests of services)

**Component Testing:**
```typescript
import { TestBed } from '@angular/core/testing'
import { render, screen } from '@testing-library/angular'
import { JournalPageComponent } from './journal-page.component'

describe('JournalPageComponent', () => {
  beforeEach(async () => {
    // No setup needed for standalone component
  })

  it('renders journal page with title', async () => {
    await render(JournalPageComponent)
    expect(screen.getByText(/journal/i)).toBeInTheDocument()
  })
})
```

## Frontend Test Setup Files

**React (PWA, Mini App):**
- File: `src/test/setup.ts`
- Purpose: Global mocks, polyfills, DOM setup
- Configured in `vitest.config.ts` via `setupFiles: ['./src/test/setup.ts']`

**Angular (web-panel):**
- File: `src/test-setup.ts`
- Content:
  ```typescript
  import 'zone.js'
  import '@angular/compiler'
  import '@testing-library/jest-dom'
  import { getTestBed } from '@angular/core/testing'
  import {
    BrowserTestingModule,
    platformBrowserTesting,
  } from '@angular/platform-browser/testing'
  
  getTestBed().initTestEnvironment(BrowserTestingModule, platformBrowserTesting(), {
    teardown: { destroyAfterEach: true },
  })
  ```
- Configured in `vitest.config.ts` via `setupFiles: ['src/test-setup.ts']`

## Test Coverage

**Java:**
- No explicit coverage requirements detected
- Optional: Add `gradle-jacoco-plugin` for coverage reports

**React/Angular:**
- No explicit coverage requirements detected
- Vitest can generate coverage via `vitest --coverage` (if `@vitest/coverage-*` installed)

## Common Test Patterns

**Async Testing (Java):**
```java
@Test
void eventConsumer_receivesMessage_publishesEvent() throws Exception {
  // Setup
  rabbitTemplate.convertAndSend("exchange", "routing.key", messagePayload);
  
  // Wait for async processing
  Awaitility.await()
    .atMost(Duration.ofSeconds(5))
    .untilAsserted(() -> {
      verify(eventPublisher, atLeastOnce()).publish(any());
    });
}
```

**Async Testing (React):**
```typescript
it('after login() succeeds, returns isAuthenticated=true', async () => {
  mockedPost.mockResolvedValueOnce({
    data: { accessToken: fakeToken, expiresIn: 900 },
  })

  const { result } = renderHook(() => useAuth(), { wrapper })

  await act(async () => {
    await result.current.login({ login: 'student00001', password: 'pass' })
  })

  expect(result.current.isAuthenticated).toBe(true)
})
```

**Error Testing (Java):**
```java
@Test
void markAttendance_notHeadman_throwsAccessDeniedException() {
  when(requestContext.isHeadman()).thenReturn(false);
  
  assertThatThrownBy(() -> 
    markingService.markAttendance(LESSON_ID, USER_ID, new MarkRequest(status))
  ).isInstanceOf(AccessDeniedException.class)
   .hasMessageContaining("Only headman");
}
```

**Error Testing (React):**
```typescript
it('login() with invalid credentials rejects', async () => {
  mockedPost.mockRejectedValueOnce(new Error('Invalid credentials'))

  const { result } = renderHook(() => useAuth(), { wrapper })

  await act(async () => {
    await expect(result.current.login({ login: 'bad', password: 'bad' }))
      .rejects.toThrow('Invalid credentials')
  })

  expect(result.current.isAuthenticated).toBe(false)
})
```

## Test Summary Statistics

**Java Services:**
- Auth Service: ~26 tests
- Academic Service: ~50 tests
- Schedule Service: ~55 tests
- Attendance Service: ~95 tests
- Notification Web Service: ~20 tests

**Frontend:**
- PWA (React): ~63 tests
- Mini App (React): ~35 tests
- Web Panel (Angular): ~129 tests

**Total Java:** ~246 tests
**Total Frontend:** ~227 tests

---

*Testing analysis: 2026-04-08*
