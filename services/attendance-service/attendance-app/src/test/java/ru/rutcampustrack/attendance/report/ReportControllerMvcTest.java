package ru.rutcampustrack.attendance.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.attendance.contract.api.ReportApi;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.GlobalExceptionHandler;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.security.AttendanceUserContextFilter;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AttendanceUserContextFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private HeadmanWeeklyReportService headmanWeeklyReportService;

    @Test
    void exportCurrentDocx_returnsBinaryFileHeaders() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        when(headmanWeeklyReportService.exportSingleWeek(weekStart, "docx"))
                .thenReturn(new HeadmanWeeklyExportResult(
                        "UVPV511_27.04.2026_03.05.2026.docx",
                        ReportApi.DOCX_MEDIA_TYPE,
                        new byte[]{1, 2, 3}));

        mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", "2026-04-27")
                        .param("format", "docx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(ReportApi.DOCX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("UVPV511_27.04.2026_03.05.2026.docx")))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        verify(headmanWeeklyReportService).exportSingleWeek(weekStart, "docx");
    }

    @Test
    void exportCurrentPdf_returnsPdfContentType() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        when(headmanWeeklyReportService.exportSingleWeek(weekStart, "pdf"))
                .thenReturn(new HeadmanWeeklyExportResult(
                        "UVPV511_27.04.2026_03.05.2026.pdf",
                        ReportApi.PDF_MEDIA_TYPE,
                        new byte[]{4, 5}));

        mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", "2026-04-27")
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".pdf")))
                .andExpect(content().bytes(new byte[]{4, 5}));
    }

    @Test
    void exportCurrentPng_returnsPngContentType() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        when(headmanWeeklyReportService.exportSingleWeek(weekStart, "png"))
                .thenReturn(new HeadmanWeeklyExportResult(
                        "UVPV511_27.04.2026_03.05.2026.png",
                        ReportApi.PNG_MEDIA_TYPE,
                        new byte[]{8, 9}));

        mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", "2026-04-27")
                        .param("format", "png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".png")))
                .andExpect(content().bytes(new byte[]{8, 9}));
    }

    @Test
    void exportSelectedWeeks_pngReturnsZipAndNoGroupIdContract() throws Exception {
        when(headmanWeeklyReportService.exportSelectedWeeks(any()))
                .thenReturn(new HeadmanWeeklyExportResult(
                        "UVPV511_27.04.2026_17.05.2026_\u0421_\u041f\u0420\u041e\u041f\u0423\u0421\u041a\u0410\u041c\u0418_png.zip",
                        ReportApi.ZIP_MEDIA_TYPE,
                        new byte[]{10, 11}));
        HeadmanWeeklyExportRequest request = new HeadmanWeeklyExportRequest(
                List.of(LocalDate.of(2026, 4, 27), LocalDate.of(2026, 5, 11)),
                "png");

        mockMvc.perform(post("/attendance/reports/headman-weekly/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(ReportApi.ZIP_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("_png.zip")))
                .andExpect(content().bytes(new byte[]{10, 11}));

        verify(headmanWeeklyReportService).exportSelectedWeeks(eq(request));
    }

    @Test
    void exportCurrent_regularStudentGets403Problem() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        when(headmanWeeklyReportService.exportSingleWeek(weekStart, "docx"))
                .thenThrow(new AccessDeniedException("Only a group headman can export weekly reports"));

        mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", "2026-04-27")
                        .param("format", "docx"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Only a group headman can export weekly reports"));
    }

    @Test
    void exportCurrent_templateValidationGets422Problem() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        when(headmanWeeklyReportService.exportSingleWeek(weekStart, "docx"))
                .thenThrow(new ReportValidationException("Headman weekly template supports up to 35 students"));

        mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", "2026-04-27")
                        .param("format", "docx"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void exportCurrent_rendererUnavailableGets503Problem() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        when(headmanWeeklyReportService.exportSingleWeek(weekStart, "pdf"))
                .thenThrow(new ReportExportUnavailableException("document-renderer-service unavailable"));

        mockMvc.perform(get("/attendance/reports/headman-weekly/current")
                        .param("weekStart", "2026-04-27")
                        .param("format", "pdf"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503));
    }
}
