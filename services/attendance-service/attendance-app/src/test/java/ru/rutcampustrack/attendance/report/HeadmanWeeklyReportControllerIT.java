package ru.rutcampustrack.attendance.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.api.ReportApi;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Spring MVC + Testcontainers coverage for M18 headman weekly export.
 *
 * <p>MongoDB is real through {@link AbstractAttendanceIntegrationTest}; upstream gRPC clients
 * are mocked at service boundaries so the test remains deterministic and focused on the
 * attendance API contract, Mongo-backed attendance lookup and export packaging.
 */
class HeadmanWeeklyReportControllerIT extends AbstractAttendanceIntegrationTest {

    private static final Long HEADMAN_ID = 777L;
    private static final Long GROUP_ID = 10L;
    private static final Long SEMESTER_ID = 1L;
    private static final Long SUBJECT_ID = 5L;
    private static final LocalDate FIRST_WEEK = LocalDate.of(2026, 4, 27);
    private static final LocalDate THIRD_WEEK = LocalDate.of(2026, 5, 11);
    private static final byte[] PDF_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46};
    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47};

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentRendererGrpcClient documentRendererGrpcClient;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), "attendances");
        Mockito.reset(academicGrpcClient, scheduleGrpcClient, documentRendererGrpcClient);

        stubAcademic();
        stubSchedule();
        seedAttendance();
    }

    @Test
    void exportCurrentDocx_returnsGeneratedDocxFromMongoBackedData() throws Exception {
        MvcResult result = mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", FIRST_WEEK.toString())
                        .param("format", "docx")
                        .headers(headmanHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(ReportApi.DOCX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(".docx")))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        assertThat(content.length).isGreaterThan(1_000);
        assertThat(content[0]).isEqualTo((byte) 'P');
        assertThat(content[1]).isEqualTo((byte) 'K');
        verifyNoInteractions(documentRendererGrpcClient);
    }

    @Test
    void exportCurrentPdf_convertsSingleWeekDocx() throws Exception {
        when(documentRendererGrpcClient.convertDocx(any(byte[].class), eq(TargetFormat.PDF)))
                .thenReturn(PDF_BYTES);

        mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", FIRST_WEEK.toString())
                        .param("format", "pdf")
                        .headers(headmanHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(content().bytes(PDF_BYTES));

        verify(documentRendererGrpcClient).convertDocx(any(byte[].class), eq(TargetFormat.PDF));
    }

    @Test
    void exportCurrentPng_convertsSingleWeekDocx() throws Exception {
        when(documentRendererGrpcClient.convertDocx(any(byte[].class), eq(TargetFormat.PNG)))
                .thenReturn(PNG_BYTES);

        mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", FIRST_WEEK.toString())
                        .param("format", "png")
                        .headers(headmanHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(".png")))
                .andExpect(content().bytes(PNG_BYTES));

        verify(documentRendererGrpcClient).convertDocx(any(byte[].class), eq(TargetFormat.PNG));
    }

    @Test
    void exportSelectedWeeksDocx_returnsOneMultiPageDocx() throws Exception {
        MvcResult result = mockMvc.perform(post("/attendance/reports/headman-weekly/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(multiWeekRequest("docx")))
                        .headers(headmanHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(ReportApi.DOCX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(".docx")))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        assertThat(content.length).isGreaterThan(1_000);
        assertThat(content[0]).isEqualTo((byte) 'P');
        assertThat(content[1]).isEqualTo((byte) 'K');
        verifyNoInteractions(documentRendererGrpcClient);
    }

    @Test
    void exportSelectedWeeksPdf_convertsOneMultiPageDocx() throws Exception {
        when(documentRendererGrpcClient.convertDocx(any(byte[].class), eq(TargetFormat.PDF)))
                .thenReturn(PDF_BYTES);

        mockMvc.perform(post("/attendance/reports/headman-weekly/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(multiWeekRequest("pdf")))
                        .headers(headmanHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(content().bytes(PDF_BYTES));

        verify(documentRendererGrpcClient).convertDocx(any(byte[].class), eq(TargetFormat.PDF));
    }

    @Test
    void exportSelectedWeeksPng_returnsZipWithOneEntryPerSelectedWeek() throws Exception {
        when(documentRendererGrpcClient.convertDocx(any(byte[].class), eq(TargetFormat.PNG)))
                .thenReturn(new byte[]{1}, new byte[]{2});

        MvcResult result = mockMvc.perform(post("/attendance/reports/headman-weekly/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(multiWeekRequest("png")))
                        .headers(headmanHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(ReportApi.ZIP_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("_png.zip")))
                .andReturn();

        assertThat(zipEntries(result.getResponse().getContentAsByteArray()))
                .containsExactly("week-01_27.04.2026_03.05.2026.png", "week-03_11.05.2026_17.05.2026.png");
        verify(documentRendererGrpcClient, times(2)).convertDocx(any(byte[].class), eq(TargetFormat.PNG));
    }

    private static org.springframework.http.HttpHeaders headmanHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", HEADMAN_ID.toString());
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Group-Id", GROUP_ID.toString());
        headers.add("X-Is-Headman", "true");
        return headers;
    }

    private static HeadmanWeeklyExportRequest multiWeekRequest(String format) {
        return new HeadmanWeeklyExportRequest(List.of(FIRST_WEEK, THIRD_WEEK), format);
    }

    private void stubAcademic() {
        when(academicGrpcClient.getActiveSemester()).thenReturn(SemesterResponse.newBuilder()
                .setId(SEMESTER_ID)
                .setName("Spring 2026")
                .setDateFrom("2026-04-27")
                .setDateTo("2026-05-31")
                .build());
        when(academicGrpcClient.getGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID)
                .setName("UVPV511")
                .setIsActive(true)
                .build());
        when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(GroupMembersResponse.newBuilder()
                .addStudents(student(100L, "Alpha Student"))
                .addStudents(student(101L, "Beta Student"))
                .build());
        when(academicGrpcClient.getSubjectsByIds(List.of(SUBJECT_ID)))
                .thenReturn(Map.of(SUBJECT_ID, "Math"));
    }

    private void stubSchedule() {
        when(scheduleGrpcClient.getLessonsByGroup(GROUP_ID, SEMESTER_ID, "2026-04-27", "2026-05-03"))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(1000L, "2026-04-27", 1))
                        .build());
        when(scheduleGrpcClient.getLessonsByGroup(GROUP_ID, SEMESTER_ID, "2026-05-11", "2026-05-17"))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(3000L, "2026-05-11", 1))
                        .build());
    }

    private void seedAttendance() {
        mongoTemplate.save(attendance(1000L, 100L, FIRST_WEEK, AttendanceStatus.PRESENT));
        mongoTemplate.save(attendance(1000L, 101L, FIRST_WEEK, AttendanceStatus.ABSENT));
        mongoTemplate.save(attendance(3000L, 100L, THIRD_WEEK, AttendanceStatus.EXCUSED));
    }

    private static AttendanceFixture attendance(Long lessonId,
                                                Long userId,
                                                LocalDate date,
                                                AttendanceStatus status) {
        Instant now = Instant.parse("2026-05-02T09:00:00Z");
        return new AttendanceFixture(
                lessonId,
                userId,
                GROUP_ID,
                SUBJECT_ID,
                SEMESTER_ID,
                1,
                date,
                status,
                AttendanceSource.HEADMAN,
                HEADMAN_ID,
                now,
                now);
    }

    private static StudentInfo student(Long id, String displayName) {
        return StudentInfo.newBuilder()
                .setUserId(id)
                .setDisplayName(displayName)
                .build();
    }

    private static LessonResponse lesson(Long id, String date, int lessonNumber) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID)
                .setDate(date)
                .setLessonNumber(lessonNumber)
                .setStatus("closed")
                .build();
    }

    private static List<String> zipEntries(byte[] zipBytes) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ArrayList<String> entries = new ArrayList<>();
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                entries.add(entry.getName());
                entry = zip.getNextEntry();
            }
            return entries;
        }
    }

    @org.springframework.data.mongodb.core.mapping.Document(collection = "attendances")
    private record AttendanceFixture(
            @org.springframework.data.mongodb.core.mapping.Field("lesson_id")
            Long lessonId,
            @org.springframework.data.mongodb.core.mapping.Field("user_id")
            Long userId,
            @org.springframework.data.mongodb.core.mapping.Field("group_id")
            Long groupId,
            @org.springframework.data.mongodb.core.mapping.Field("subject_id")
            Long subjectId,
            @org.springframework.data.mongodb.core.mapping.Field("semester_id")
            Long semesterId,
            @org.springframework.data.mongodb.core.mapping.Field("lesson_number")
            Integer lessonNumber,
            @org.springframework.data.mongodb.core.mapping.Field("lesson_date")
            LocalDate lessonDate,
            @org.springframework.data.mongodb.core.mapping.Field("status")
            AttendanceStatus status,
            @org.springframework.data.mongodb.core.mapping.Field("source")
            AttendanceSource source,
            @org.springframework.data.mongodb.core.mapping.Field("marked_by")
            Long markedBy,
            @org.springframework.data.mongodb.core.mapping.Field("created_at")
            Instant createdAt,
            @org.springframework.data.mongodb.core.mapping.Field("updated_at")
            Instant updatedAt
    ) {}
}
