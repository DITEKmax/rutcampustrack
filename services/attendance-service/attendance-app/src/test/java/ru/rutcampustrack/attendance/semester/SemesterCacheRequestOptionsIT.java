package ru.rutcampustrack.attendance.semester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SubjectInfo;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsRequest;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsResponse;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.config.MongoConvertersConfig;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.studentrequest.StudentLateCheckinBudgetRepository;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.LessonSnapshot;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLateCheckinBudgetDocument;

/**
 * Actual cache/client and request domain with Mongo; the Academic server is a controlled RPC seam.
 */
@DataMongoTest
@Testcontainers
@Import(MongoConvertersConfig.class)
class SemesterCacheRequestOptionsIT {

    private static final String DATABASE =
            "delivery_semester_" + UUID.randomUUID().toString().replace("-", "");
    private static final Identity STUDENT = new Identity(100L, UserRole.STUDENT, 10L, false);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-07T08:00:00Z"), ZoneId.of("Europe/Moscow"));

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0").withReuse(false);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @Autowired private ExcuseRepository excuses;
    @Autowired private LateCheckinRepository lateCheckins;
    @Autowired private AttendanceRepository attendance;
    @Autowired private StudentLateCheckinBudgetRepository budgets;
    @Autowired private MongoTemplate mongo;

    private final AtomicReference<SemesterResponse> authoritative = new AtomicReference<>();
    private volatile boolean unavailable;
    private Server server;
    private ManagedChannel channel;
    private SemesterCacheService cache;
    private StudentRequestService requests;

    @BeforeEach
    void currentStudentScope() throws Exception {
        authoritative.set(semester(11L, "2026-02-01", "2026-08-31"));
        unavailable = false;
        server =
                ServerBuilder.forPort(0)
                        .addService(
                                new AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase() {
                                    @Override
                                    public void getActiveSemester(
                                            Empty request,
                                            StreamObserver<SemesterResponse> observer) {
                                        if (unavailable) {
                                            observer.onError(
                                                    Status.UNAVAILABLE.asRuntimeException());
                                        } else if (authoritative.get() == null) {
                                            observer.onError(Status.NOT_FOUND.asRuntimeException());
                                        } else {
                                            observer.onNext(authoritative.get());
                                            observer.onCompleted();
                                        }
                                    }

                                    @Override
                                    public void getSubjectsByIds(
                                            SubjectsByIdsRequest request,
                                            StreamObserver<SubjectsByIdsResponse> observer) {
                                        observer.onNext(
                                                SubjectsByIdsResponse.newBuilder()
                                                        .addSubjects(
                                                                SubjectInfo.newBuilder()
                                                                        .setSubjectId(5L)
                                                                        .setSubjectName(
                                                                                "Математика")
                                                                        .setSubjectType("lecture"))
                                                        .build());
                                        observer.onCompleted();
                                    }
                                })
                        .build()
                        .start();
        channel =
                ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext()
                        .build();
        AcademicGrpcClient academic = new AcademicGrpcClient();
        ReflectionTestUtils.setField(
                academic, "stub", AcademicGrpcServiceGrpc.newBlockingStub(channel));
        cache = new SemesterCacheService(academic);
        cache.init();
        requests =
                new StudentRequestService(
                        excuses,
                        lateCheckins,
                        attendance,
                        null,
                        budgets,
                        null,
                        null,
                        academic,
                        cache,
                        null,
                        null,
                        null,
                        mongo,
                        null,
                        CLOCK,
                        null);
        budgets.deleteAll();
        budgets.save(
                StudentLateCheckinBudgetDocument.builder()
                        .id("old")
                        .studentId(100L)
                        .semesterId(11L)
                        .limit(5)
                        .used(4)
                        .updatedAt(CLOCK.instant())
                        .build());
        budgets.save(
                StudentLateCheckinBudgetDocument.builder()
                        .id("current")
                        .studentId(100L)
                        .semesterId(12L)
                        .limit(5)
                        .used(1)
                        .updatedAt(CLOCK.instant())
                        .build());
    }

    @AfterEach
    void closeOwnedRpc() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        if (server != null) {
            server.shutdownNow();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void missedActivationEventDoesNotRejectCurrentLessonOrSelectPreviousBudget() {
        // Adjacent semesters do not overlap. No invalidation is delivered to this instance.
        authoritative.set(semester(12L, "2026-09-01", "2027-01-31"));
        var result = requests.options(STUDENT, List.of(lesson(12L)));

        assertThat(result.budget().semesterId()).isEqualTo(12L);
        assertThat(result.budget().remaining()).isEqualTo(4);
        assertThat(result.lessons()).hasSize(1);
        assertThat(result.lessons().getFirst().excuseEligible()).isTrue();
        assertThat(budgets.count()).isEqualTo(2);
    }

    @Test
    void missedArchiveEventDoesNotExposePreviousSemesterRequestOptions() {
        authoritative.set(null);
        assertThatThrownBy(() -> requests.options(STUDENT, List.of(lesson(11L))))
                .isInstanceOf(BadRequestException.class);
        assertThat(budgets.count()).isEqualTo(2);
    }

    @Test
    void authorityOutageDoesNotSilentlyReusePreviousSemesterForRequests() {
        unavailable = true;
        assertThatThrownBy(() -> requests.options(STUDENT, List.of(lesson(11L))))
                .isInstanceOf(AcademicServiceUnavailableException.class);
        assertThat(budgets.count()).isEqualTo(2);
    }

    private static SemesterResponse semester(long id, String from, String to) {
        return SemesterResponse.newBuilder().setId(id).setDateFrom(from).setDateTo(to).build();
    }

    private static LessonSnapshot lesson(long semesterId) {
        return new LessonSnapshot(
                41L,
                10L,
                5L,
                "Математика",
                "lecture",
                semesterId,
                1,
                semesterId == 11L ? LocalDate.of(2026, 8, 31) : LocalDate.of(2026, 9, 8),
                LocalTime.of(8, 30),
                LocalTime.of(10, 0),
                "planned",
                false);
    }
}
