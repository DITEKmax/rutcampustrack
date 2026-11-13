package ru.rutcampustrack.mobilebff.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.client.MongoClients;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.MongoDBContainer;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceReadPortImpl;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.grpc.TeacherAttendanceGrpcIdentityInterceptor;
import ru.rutcampustrack.attendance.grpc.TeacherAttendanceReadGrpcService;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.studentrequest.RequestAttachmentRepository;
import ru.rutcampustrack.mobilebff.error.MobileProblemHandler;
import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileGrpcAuth;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileIdentityFilter;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReadServiceGrpc;
import ru.rutcampustrack.teacher.grpc.TeacherJournalRequest;

/** Signed public JSON -> production BFF -> TCP gRPC -> production calculation -> real Mongo.
 * Academic/Schedule authority and dated snapshots are the only upstream domain stubs.
 */
class TeacherJournalMetricsHttpIT {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
    private static final String JOURNAL = "/api/v1/teacher/journal";
    private static final String STATS = "/api/v1/teacher/stats";

    @Test
    void mongoSeedTargetMustBeLoopback() {
        requireLoopbackMongo("mongodb://127.0.0.1:27291");
        requireLoopbackMongo("mongodb://localhost:27291");
        assertThatThrownBy(() -> requireLoopbackMongo("mongodb://192.0.2.10:27017"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requireLoopbackMongo("mongodb://localhost:27017,192.0.2.10:27017"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requireLoopbackMongo("mongodb://user:password@localhost:27017"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persistedMetricsReachSignedPublicJsonAcrossPagesAndStudentSelection() throws Exception {
        try (var container = new MongoDBContainer("mongo:7.0")) {
            // A supplied local daemon is useful for developer environments without Docker.
            // It is never a skip: every execution still uses a real, unique Mongo database.
            String uri = System.getenv("RCT_JOURNAL_MONGO_URI");
            if (uri == null || uri.isBlank()) {
                container.start();
                uri = container.getReplicaSetUrl();
            }
            requireLoopbackMongo(uri);
            try (var mongoClient = MongoClients.create(uri)) {
                var mongo = new MongoTemplate(mongoClient,
                        "teacher_journal_metrics_" + UUID.randomUUID().toString().replace("-", ""));
                runJournalBoundary(mongo);
            }
        }
    }

    private void runJournalBoundary(MongoTemplate mongo) throws Exception {
        var lessons = lessons();
        var authority = TeacherSubjectsResponse.newBuilder().addSubjects(
                TeacherSubjectInfo.newBuilder().setAssignmentId(12).setSemesterId(9)
                        .setGroupId(33).setGroupName("ТЕСТ-241").setSubjectId(22)
                        .setSubjectName("Тестовый предмет").setLessonType("lecture")).build();
        var academic = mock(AcademicGrpcClient.class);
        var schedule = mock(ScheduleGrpcClient.class);
        when(academic.getCurrentTeacherSubjects(71L)).thenReturn(authority);
        when(academic.getGroupMembers(eq(33L), any(LocalDate.class), eq(9L)))
                .thenAnswer(call -> roster(call.getArgument(1)));
        when(academic.getGroup(33L)).thenReturn(GroupResponse.newBuilder().setId(33)
                .setName("ТЕСТ-241").build());
        when(academic.getSubjectDetailsByIds(List.of(22L))).thenReturn(Map.of(22L,
                new AcademicGrpcClient.SubjectDetails("Тестовый предмет", "lecture")));
        when(schedule.getLessonById(any())).thenAnswer(call -> lessons.stream()
                .filter(lesson -> lesson.getId() == (Long) call.getArgument(0)).findFirst().orElseThrow());
        when(schedule.getLessonsByIds(any())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            return ids.stream().map(id -> info(lessons.stream()
                    .filter(lesson -> lesson.getId() == id).findFirst().orElseThrow())).toList();
        });
        when(schedule.getCurrentLessonsByGroup(eq(33L), eq(9L), any(), any(), any()))
                .thenAnswer(call -> {
                    Set<Long> ids = call.getArgument(4);
                    return inRange(lessons, LocalDate.parse(call.getArgument(2)),
                            LocalDate.parse(call.getArgument(3))).stream()
                            .filter(lesson -> ids.contains(lesson.getId())).toList();
                });

        insert(mongo, lessons, 101, 501, AttendanceStatus.PRESENT);
        insert(mongo, lessons, 102, 501, AttendanceStatus.EXCUSED);
        insert(mongo, lessons, 103, 501, AttendanceStatus.FREE_ATTENDANCE);
        insert(mongo, lessons, 101, 502, AttendanceStatus.PRESENT); // Not a member on this date.
        insert(mongo, lessons, 106, 502, AttendanceStatus.PRESENT);
        insert(mongo, lessons, 107, 502, AttendanceStatus.EXCUSED);
        insert(mongo, lessons, 206, 501, AttendanceStatus.PRESENT); // Cancelled lesson.
        insert(mongo, lessons, 209, 501, AttendanceStatus.PRESENT); // Future CLOSED snapshot.
        long stored = mongo.getCollection("attendances").countDocuments();
        assertThat(stored).isEqualTo(8);
        var report = new ReportService(new AttendanceReadPortImpl(mongo), academic, schedule,
                mock(SemesterCacheService.class), mock(RequestContext.class), CLOCK, null);
        var upstream = new TeacherAttendanceReadGrpcService(report, schedule, academic,
                mock(ExcuseRepository.class), mock(RequestAttachmentRepository.class), CLOCK);
        var signer = new InternalJwtTestFactory();
        var keys = mock(PublicKeyProvider.class);
        when(keys.getPublicKey()).thenReturn(signer.publicKey());
        var properties = new InternalJwtProperties(null, 60, 0, false, null, null, null);
        var validator = new InternalJwtValidator(keys, properties);
        var grpc = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0)).addService(upstream)
                .intercept(new TeacherAttendanceGrpcIdentityInterceptor(validator)).build().start();
        var channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpc.getPort()).usePlaintext().build();
        try {
            var context = new MobileRequestContext();
            var attendance = new MobileAttendanceClient(new MobileGrpcAuth(context, ""));
            ReflectionTestUtils.setField(attendance, "teacherStub",
                    TeacherAttendanceReadServiceGrpc.newBlockingStub(channel));
            var mobileAcademic = mock(MobileAcademicClient.class);
            var mobileSchedule = mock(MobileScheduleClient.class);
            when(mobileAcademic.currentTeacherSubjects(71L)).thenReturn(authority);
            when(mobileAcademic.teacherAssignmentsForSemester(9L)).thenReturn(
                    TeacherAssignmentsResponse.newBuilder().setSemesterDateFrom("2026-09-01")
                            .setSemesterDateTo("2026-10-31").build());
            when(mobileSchedule.lessons(eq(33L), eq(9L), any(), any())).thenAnswer(call ->
                    LessonsResponse.newBuilder().addAllLessons(
                            inRange(lessons, call.getArgument(2), call.getArgument(3))).build());
            var facade = new TeacherReadFacade(context, mobileAcademic, mobileSchedule, attendance, CLOCK);
            ObjectMapper json = Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
            var mvc = MockMvcBuilders.standaloneSetup(new TeacherApiController(facade))
                    .setControllerAdvice(new MobileProblemHandler(CLOCK))
                    .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                    .addFilters(new MobileIdentityFilter(validator, properties, context, json, CLOCK)).build();
            String teacher = signer.validToken(71L, UUID.randomUUID(), 1L, 1L,
                    "TEACHER", "ACTIVE", null, false, false);
            String header = properties.headerName();

            JsonNode group = read(mvc, json, request().header(header, teacher), "group-page-0");
            assertThat(group.path("lessons").size()).isEqualTo(100);
            assertThat(group.path("totalLessons").asInt()).isEqualTo(107);
            assertThat(group.path("hasMore").asBoolean()).isTrue();
            assertThat(group.at("/metrics/serverNow").asText()).isEqualTo(CLOCK.instant().toString());
            assertThat(group.at("/metrics/series").size()).isEqualTo(105);
            assertThat(group.at("/metrics/students/0/studentId").isTextual()).isTrue();
            assertThat(group.at("/lessons/0/id").isTextual()).isTrue();
            assertThat(group.at("/lessons/0/lessonType").asText()).isEqualTo("lecture");
            assertThat(group.at("/lessons/1/lessonType").asText()).isEqualTo("practice");
            assertThat(group.at("/lessons/0/assignmentId").isNull()).isTrue();
            assertThat(group.at("/lessons/1/assignmentId").isNull()).isTrue();
            assertThat(group.at("/lessons/2/assignmentId").asText()).isEqualTo("12");
            assertThat(group.at("/metrics/series/0/lessonId").isTextual()).isTrue();
            assertThat(group.at("/metrics/series/0/lessonId").asText()).isEqualTo("101");
            assertThat(group.at("/metrics/students/0/present/denominator").asInt()).isEqualTo(105);
            assertThat(group.at("/metrics/students/0/present/percent").asDouble()).isEqualTo(100.0 / 105);
            assertThat(group.at("/metrics/students/0/presentOrExcused/numerator").asInt()).isEqualTo(3);
            assertThat(group.at("/metrics/students/0/excused/numerator").asInt()).isEqualTo(2);
            assertThat(group.at("/metrics/students/0/absent/numerator").asInt()).isEqualTo(102);
            assertThat(group.at("/metrics/students/1/present/denominator").asInt()).isEqualTo(100);
            assertThat(group.at("/metrics/students/1/present/numerator").asInt()).isEqualTo(1);
            assertThat(group.at("/metrics/students/1/absent/percent").asDouble()).isEqualTo(98);
            assertThat(group.at("/metrics/series/0/present/denominator").asInt()).isEqualTo(1);
            assertThat(group.at("/students/1/cells/0/lessonId").asText()).isEqualTo("106");
            assertThat(group.at("/students/1/cells/2/status").asText()).isEqualTo("absent");
            assertThat(group.at("/students/1/cells/2/recordPresent").asBoolean()).isFalse();

            var last = read(mvc, json, request("page", "1", "includeMetrics", "false")
                    .header(header, teacher), "group-page-1");
            assertThat(last.path("lessons").size()).isEqualTo(7);
            assertThat(last.path("hasMore").asBoolean()).isFalse();
            assertThat(last.path("metrics").isNull()).isTrue();
            assertThat(last.at("/lessons/5/cancelled").asBoolean()).isTrue();
            assertThat(last.at("/lessons/5/assignmentId").isNull()).isTrue();
            var stats = read(mvc, json, statsRequest().header(header, teacher), "practice-stats");
            assertThat(stats.path("lessonsCount").asInt()).isEqualTo(1);
            assertThat(stats.at("/students/0/excused/numerator").asInt()).isEqualTo(1);
            assertThat(stats.at("/students/0/excused/denominator").asInt()).isEqualTo(1);
            var selected = read(mvc, json, request("metricsStudentId", "502")
                    .header(header, teacher), "student-502-page-0");
            assertThat(selected.at("/metrics/selectedStudentId").asText()).isEqualTo("502");
            assertThat(selected.at("/metrics/selectedStudentId").isTextual()).isTrue();
            assertThat(selected.at("/metrics/students")).isEqualTo(group.at("/metrics/students"));
            assertThat(selected.at("/metrics/series/0/present/denominator").asInt()).isZero();
            assertThat(selected.at("/metrics/series/5/present/percent").asDouble()).isEqualTo(100);

            var filtered = read(mvc, json, request("lessonType", "lab",
                    "dateFrom", "2026-09-02", "dateTo", "2026-09-02")
                    .header(header, teacher), "filtered");
            assertThat(filtered.path("totalLessons").asInt()).isEqualTo(3);
            assertThat(filtered.at("/metrics/series").size()).isEqualTo(3);
            assertThat(filtered.at("/metrics/students/1/present/percent").asDouble()).isEqualTo(100.0 / 3);
            var all = read(mvc, json, request("pastOnly", "false").header(header, teacher), "all-statuses");
            assertThat(all.path("totalLessons").asInt()).isEqualTo(109);
            assertThat(all.at("/metrics/series")).isEqualTo(group.at("/metrics/series"));
            var allLast = read(mvc, json, request("pastOnly", "false", "includeMetrics", "false",
                            "page", "1").header(header, teacher), "all-statuses-page-1");
            assertThat(allLast.at("/lessons/6/status").asText()).isEqualTo("active");
            assertThat(allLast.at("/lessons/7/status").asText()).isEqualTo("planned");
            assertThat(allLast.at("/lessons/7/assignmentId").asText()).isEqualTo("12");
            var empty = read(mvc, json, request("dateFrom", "2026-10-02",
                    "dateTo", "2026-10-31").header(header, teacher), "empty");
            assertThat(empty.at("/metrics/students").size()).isZero();
            assertThat(empty.at("/metrics/series").size()).isZero();

            mvc.perform(request()).andExpect(status().isUnauthorized());
            mvc.perform(request().header(header, signer.validToken(501L, UUID.randomUUID(), 1L, 1L,
                    "STUDENT", "ACTIVE", 33L, false, false))).andExpect(status().isForbidden());
            mvc.perform(request("groupId", "99").header(header, teacher))
                    .andExpect(status().isNotFound());
            mvc.perform(request("subjectId", "99").header(header, teacher))
                    .andExpect(status().isNotFound());
            mvc.perform(request("metricsStudentId", "999").header(header, teacher))
                    .andExpect(status().isForbidden());
            mvc.perform(request("includeMetrics", "false", "metricsStudentId", "502")
                    .header(header, teacher)).andExpect(status().isBadRequest());
            lessons.add(lesson(210, "2026-09-30", "closed").toBuilder()
                    .setLessonType("practice").clearAssignmentId().build());
            mvc.perform(request("dateFrom", "2026-09-30", "dateTo", "2026-09-30",
                            "includeMetrics", "false").header(header, teacher))
                    .andExpect(status().isForbidden());
            mvc.perform(request("dateFrom", "2026-09-30", "dateTo", "2026-09-30",
                            "page", "1").header(header, teacher))
                    .andExpect(status().isForbidden());
            mvc.perform(statsRequest().header(header, teacher)).andExpect(status().isForbidden());
            assertThatThrownBy(() -> TeacherAttendanceReadServiceGrpc.newBlockingStub(channel)
                    .getTeacherJournal(TeacherJournalRequest.newBuilder().setIncludeMetrics(true).build()))
                    .isInstanceOf(io.grpc.StatusRuntimeException.class)
                    .hasMessageContaining("UNAUTHENTICATED");
            assertThat(mongo.getCollection("attendances").countDocuments()).isEqualTo(stored);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            grpc.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static MockHttpServletRequestBuilder request(String... overrides) {
        Map<String, String> values = new java.util.LinkedHashMap<>(Map.of(
                "semesterId", "9", "groupId", "33", "subjectId", "22",
                "lessonType", "lecture,practice,lab",
                "pastOnly", "true", "includeMetrics", "true", "page", "0", "pageSize", "100"));
        for (int index = 0; index < overrides.length; index += 2)
            values.put(overrides[index], overrides[index + 1]);
        var request = get(JOURNAL);
        values.forEach((key, value) -> request.param(key, value.split(",")));
        return request;
    }

    private static MockHttpServletRequestBuilder statsRequest() {
        return get(STATS).param("semesterId", "9").param("groupId", "33")
                .param("subjectId", "22").param("scope", "students").param("lessonType", "practice");
    }

    private static void requireLoopbackMongo(String value) {
        URI uri = URI.create(value);
        if (!"mongodb".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null
                || !List.of("127.0.0.1", "localhost", "[::1]", "::1").contains(uri.getHost()))
            throw new IllegalArgumentException("Journal verification requires a loopback Mongo daemon");
    }

    private static JsonNode read(MockMvc mvc, ObjectMapper json, MockHttpServletRequestBuilder request,
            String name) throws Exception {
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String evidence = System.getenv("RCT_TEACHER_JOURNAL_EVIDENCE");
        if (evidence != null && !evidence.isBlank()) {
            Path target = Path.of(evidence).resolve(name + ".json");
            Files.createDirectories(target.getParent());
            Files.writeString(target, body);
        }
        return json.readTree(body);
    }

    private static GroupMembersResponse roster(LocalDate date) {
        var members = GroupMembersResponse.newBuilder().setSemesterId(9).setAsOfDate(date.toString())
                .addStudents(StudentInfo.newBuilder().setUserId(501).setDisplayName("Альфа Тестовый"));
        if (!date.isBefore(LocalDate.of(2026, 9, 2)))
            members.addStudents(StudentInfo.newBuilder().setUserId(502).setDisplayName("Бета Тестовый"));
        return members.build();
    }

    private static List<LessonResponse> lessons() {
        List<LessonResponse> values = new ArrayList<>();
        LongStream.rangeClosed(101, 205).forEach(id -> values.add(lesson(id,
                LocalDate.of(2026, 9, 1).plusDays((id - 101) / 5).toString(), "closed")));
        values.set(0, values.get(0).toBuilder().clearAssignmentId().clearAssignedTeacherId().build());
        values.set(1, values.get(1).toBuilder().setLessonType("practice")
                .clearAssignmentId().clearAssignedTeacherId().build());
        values.add(lesson(206, "2026-09-22", "cancelled").toBuilder().setLessonType("practice")
                .clearAssignmentId().clearAssignedTeacherId().build());
        values.add(lesson(207, "2026-09-23", "active"));
        values.add(lesson(208, "2026-09-24", "planned"));
        values.add(lesson(209, "2026-10-01", "closed"));
        values.add(lesson(211, "2026-10-03", "planned").toBuilder().setLessonType("practice")
                .clearAssignmentId().clearAssignedTeacherId().build());
        values.add(lesson(212, "2026-10-03", "cancelled").toBuilder().setLessonType("practice")
                .clearAssignmentId().clearAssignedTeacherId().build());
        values.add(lesson(213, "2026-10-03", "closed").toBuilder().setLessonType("practice")
                .clearAssignmentId().clearAssignedTeacherId().build());
        return values;
    }

    private static List<LessonResponse> inRange(
            List<LessonResponse> lessons, LocalDate from, LocalDate to) {
        return lessons.stream().filter(lesson -> {
            var date = LocalDate.parse(lesson.getDate());
            return !date.isBefore(from) && !date.isAfter(to);
        }).toList();
    }

    private static LessonResponse lesson(long id, String date, String status) {
        return LessonResponse.newBuilder().setId(id).setScheduleItemId(1000 + id)
                .setOccurrenceId(2000 + id).setAssignmentId(12).setAssignedTeacherId(71)
                .setGroupId(33).setSubjectId(22).setSemesterId(9).setDate(date)
                .setLessonNumber((int) ((id - 101) % 5) + 1).setStartTime("09:00:00").setEndTime("10:30:00")
                .setLessonType(id % 2 == 0 ? "lab" : "lecture").setStatus(status)
                .setRoom("Тест-101").setGeneration(1).setRevision(1).setCurrent(true).build();
    }

    private static LessonInfo info(LessonResponse lesson) {
        var info = LessonInfo.newBuilder().setLessonId(lesson.getId())
                .setGroupId(lesson.getGroupId()).setSubjectId(lesson.getSubjectId())
                .setSemesterId(lesson.getSemesterId()).setOccurrenceId(lesson.getOccurrenceId())
                .setGeneration(lesson.getGeneration()).setRevision(lesson.getRevision())
                .setLessonNumber(lesson.getLessonNumber())
                .setLessonType(lesson.getLessonType()).setDate(lesson.getDate())
                .setStartsAt(lesson.getDate() + "T" + lesson.getStartTime()).setStatus(lesson.getStatus());
        if (lesson.hasAssignmentId()) info.setAssignmentId(lesson.getAssignmentId());
        if (lesson.hasAssignedTeacherId()) info.setTeacherId(lesson.getAssignedTeacherId());
        return info.build();
    }

    private static void insert(MongoTemplate mongo, List<LessonResponse> lessons, long lessonId,
            long studentId, AttendanceStatus status) {
        var lesson = lessons.stream().filter(item -> item.getId() == lessonId).findFirst().orElseThrow();
        mongo.save(AttendanceDocument.builder().lessonId(lessonId).userId(studentId)
                .groupId(33L).subjectId(22L).semesterId(9L).lessonNumber(lesson.getLessonNumber())
                .lessonDate(LocalDate.parse(lesson.getDate())).status(status).source(AttendanceSource.STUDENT_GEO)
                .markedBy(studentId).createdAt(CLOCK.instant()).updatedAt(CLOCK.instant()).build());
    }
}
