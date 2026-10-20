package ru.rutcampustrack.attendance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.bson.Document;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class AttendanceIdentityRecordFixture {
    private AttendanceIdentityRecordFixture() {}

    static void assertOwnRecords(
            TestRestTemplate http,
            MongoTemplate mongo,
            ScheduleGrpcClient schedule,
            SemesterCacheService semesters,
            HttpHeaders headers)
            throws Exception {
        mongo.getCollection("attendances").deleteMany(new Document());
        for (long user : List.of(1L, 999L)) {
            mongo.save(
                    AttendanceDocument.builder()
                            .lessonId(710L + user)
                            .userId(user)
                            .groupId(user == 1 ? 5L : 88L)
                            .subjectId(7L)
                            .semesterId(21L)
                            .lessonNumber(1)
                            .lessonDate(LocalDate.of(2026, 4, 1))
                            .status(user == 1 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT)
                            .source(AttendanceSource.HEADMAN)
                            .createdAt(Instant.parse("2026-04-01T08:00:00Z"))
                            .updatedAt(Instant.parse("2026-04-01T08:00:00Z"))
                            .build());
        }
        when(semesters.getActiveSemesterId()).thenReturn(21L);
        when(schedule.getLessonsByIds(anyList()))
                .thenAnswer(
                        call -> {
                            var result =
                                    new ArrayList<ru.rutcampustrack.schedule.grpc.LessonInfo>();
                            for (Long id : (List<Long>) call.getArgument(0))
                                result.add(
                                        ru.rutcampustrack.schedule.grpc.LessonInfo.newBuilder()
                                                .setLessonId(id)
                                                .setGroupId(id == 711 ? 5L : 88L)
                                                .setSubjectId(7L)
                                                .setSemesterId(21L)
                                                .setDate("2026-04-01")
                                                .setLessonNumber(1)
                                                .setStatus("closed")
                                                .build());
                            return result;
                        });
        var before = mongo.getCollection("attendances").find().into(new ArrayList<Document>());
        var response =
                http.exchange(
                        "/attendance/reports/student/records",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var records =
                new ObjectMapper()
                        .readTree(response.getBody())
                        .path("_embedded")
                        .path("attendanceRecordEntryList");
        assertThat(records.size()).isEqualTo(1);
        var own = records.get(0);
        assertThat(own.path("lessonId").asLong()).isEqualTo(711L);
        assertThat(own.path("subjectId").asLong()).isEqualTo(7L);
        assertThat(own.path("status").asText()).isEqualTo("present");
        assertThat(own.path("symbol").asText()).isEqualTo("+");
        assertThat(own.path("source").asText()).isEqualTo("headman");
        assertThat(response.getBody()).doesNotContain("1709");
        assertThat(mongo.getCollection("attendances").find().into(new ArrayList<Document>()))
                .containsExactlyInAnyOrderElementsOf(before);
    }
}
