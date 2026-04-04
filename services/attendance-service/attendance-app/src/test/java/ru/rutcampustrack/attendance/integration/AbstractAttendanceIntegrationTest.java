package ru.rutcampustrack.attendance.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;

/**
 * Abstract base class for Attendance Service integration tests.
 *
 * Provides:
 * - Static MongoDB 7.0 and RabbitMQ 3.13 Testcontainers (shared across all tests in JVM)
 * - @MockitoBean for gRPC clients and SemesterCacheService (prevents @PostConstruct gRPC calls)
 * - @ActiveProfiles("test") activates application-test.yml with RabbitAutoConfiguration excluded
 * - @AutoConfigureMockMvc for HTTP endpoint testing
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractAttendanceIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected AttendanceRepository attendanceRepository;

    @MockitoBean
    protected ScheduleGrpcClient scheduleGrpcClient;

    @MockitoBean
    protected AcademicGrpcClient academicGrpcClient;

    @MockitoBean
    protected SemesterCacheService semesterCacheService;

    static final MongoDBContainer MONGODB;
    static final RabbitMQContainer RABBITMQ;

    static {
        MONGODB = new MongoDBContainer("mongo:7.0");
        MONGODB.start();
        RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management");
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        // Override autoconfigure exclude to re-enable RabbitMQ for integration tests
        registry.add("spring.autoconfigure.exclude", () -> "");
    }
}
