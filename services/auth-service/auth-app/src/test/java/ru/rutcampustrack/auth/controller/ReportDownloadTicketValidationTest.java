package ru.rutcampustrack.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.rutcampustrack.auth.exception.GlobalExceptionHandler;
import ru.rutcampustrack.auth.exception.InvalidReportDownloadTicketRequestException;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.ReportDownloadTicketService;
import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadFormat;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.util.UUID;
import java.time.LocalDate;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportDownloadTicketValidationTest {

    @Test
    void rosterSelectorIsClosedFormatOnlyAndBindsKindAndFormat() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        for (ReportDownloadFormat format : ReportDownloadFormat.values()) {
            var roster = new IssueReportDownloadTicketRequest(ReportDownloadKind.HEADMAN_GROUP_COMPOSITION,
                    null, null, null, null, null, null,
                    new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(format));
            org.junit.jupiter.api.Assertions.assertTrue(roster.isParametersConsistent());
            org.junit.jupiter.api.Assertions.assertEquals(format.mediaType(), roster.expectedMediaType());
            org.junit.jupiter.api.Assertions.assertEquals("headman-group-composition." + format.filenameExtension(),
                    roster.suggestedFilename());
            var another = new IssueReportDownloadTicketRequest(ReportDownloadKind.HEADMAN_GROUP_COMPOSITION,
                    null, null, null, null, null, null,
                    new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(
                            format == ReportDownloadFormat.PDF ? ReportDownloadFormat.HTML : ReportDownloadFormat.PDF));
            org.junit.jupiter.api.Assertions.assertNotEquals(roster.bindingHash(), another.bindingHash());
        }
        org.junit.jupiter.api.Assertions.assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue("{\"format\":\"pdf\",\"groupId\":99}",
                        IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters.class));
        var conflicting = new IssueReportDownloadTicketRequest(ReportDownloadKind.HEADMAN_STATS,
                null, null, null, null,
                new IssueReportDownloadTicketRequest.HeadmanStatsParameters(null, null, null, null, ReportDownloadFormat.PDF),
                null, new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(ReportDownloadFormat.PDF));
        org.junit.jupiter.api.Assertions.assertFalse(conflicting.isParametersConsistent());
    }

    @Test
    void trendTicketBindsItsSelectorAndUsesActualPngWithoutChangingLegacyPngZip() {
        var selector = new IssueReportDownloadTicketRequest.HeadmanStatsTrendParameters(
                IssueReportDownloadTicketRequest.HeadmanStatsTrendMode.WEEK,
                LocalDate.parse("2026-09-21"), null, null, ReportDownloadFormat.PNG);
        var png = new IssueReportDownloadTicketRequest(ReportDownloadKind.HEADMAN_STATS_TREND,
                null, null, null, null, null, selector);
        var html = new IssueReportDownloadTicketRequest(ReportDownloadKind.HEADMAN_STATS_TREND,
                null, null, null, null, null,
                new IssueReportDownloadTicketRequest.HeadmanStatsTrendParameters(
                        IssueReportDownloadTicketRequest.HeadmanStatsTrendMode.WEEK,
                        LocalDate.parse("2026-09-21"), null, null, ReportDownloadFormat.HTML));
        var tablePng = new IssueReportDownloadTicketRequest(ReportDownloadKind.HEADMAN_STATS,
                null, null, null, null,
                new IssueReportDownloadTicketRequest.HeadmanStatsParameters(
                        null, null, null, null, ReportDownloadFormat.PNG));

        org.junit.jupiter.api.Assertions.assertTrue(png.isParametersConsistent());
        org.junit.jupiter.api.Assertions.assertEquals("headman-stats-trend.png", png.suggestedFilename());
        org.junit.jupiter.api.Assertions.assertEquals("image/png", png.expectedMediaType());
        org.junit.jupiter.api.Assertions.assertNotEquals(png.bindingHash(), html.bindingHash());
        org.junit.jupiter.api.Assertions.assertEquals("headman-stats.zip", tablePng.suggestedFilename());
        org.junit.jupiter.api.Assertions.assertEquals("application/zip", tablePng.expectedMediaType());
    }

    @Test
    void selectorWithAnExtraParameterObjectIsRejectedAsBadRequestBeforeIssuance() throws Exception {
        ReportDownloadTicketService service = mock(ReportDownloadTicketService.class);
        ReportDownloadTicketController controller = new ReportDownloadTicketController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(),
                        new ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler())
                .build();
        SessionPrincipal principal = new SessionPrincipal(17L,
                UUID.fromString("11111111-2222-4333-8444-555555555555"),
                1L, 1L, AuthRole.TEACHER, RoleStatus.ACTIVE, 31L, false, false);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null);

        mvc.perform(post("/auth/report-download-tickets")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind":"TEACHER_JOURNAL",
                                  "teacherJournal":{
                                    "semesterId":1,"groupId":31,"subjectId":4,
                                    "lessonTypes":["LECTURE"],"format":"pdf"
                                  },
                                  "headmanStats":{"format":"pdf"}
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void explicitServiceSelectorFailureMapsToBadRequest() throws Exception {
        ReportDownloadTicketService service = mock(ReportDownloadTicketService.class);
        when(service.issue(org.mockito.ArgumentMatchers.any(), any()))
                .thenThrow(new InvalidReportDownloadTicketRequestException());
        ReportDownloadTicketController controller = new ReportDownloadTicketController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SessionPrincipal principal = new SessionPrincipal(17L,
                UUID.fromString("11111111-2222-4333-8444-555555555555"),
                1L, 1L, AuthRole.TEACHER, RoleStatus.ACTIVE, 31L, false, false);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null);

        mvc.perform(post("/auth/report-download-tickets")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"TEACHER_JOURNAL","teacherJournal":{
                                  "semesterId":1,"groupId":31,"subjectId":4,
                                  "lessonTypes":["LECTURE"],"format":"pdf"}}
                                """))
                .andExpect(status().isBadRequest());
    }
}
