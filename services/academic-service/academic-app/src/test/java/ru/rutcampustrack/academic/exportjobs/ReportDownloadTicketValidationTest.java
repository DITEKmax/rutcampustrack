package ru.rutcampustrack.academic.exportjobs;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadFormat;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.time.LocalDate;
import java.util.UUID;

class ReportDownloadTicketValidationTest {

    @Test
    void rejectedOpaqueTicketIsNotEchoedByOwnerProblemResponse() throws Exception {
        var service = mock(ExportJobService.class);
        var marker = "SENTINEL" + "A".repeat(35);
        when(service.downloadTicket(marker))
                .thenThrow(
                        ExportJobRepository.error(org.springframework.http.HttpStatus.NOT_FOUND));
        var controller =
                new AcademicReportTicketController(
                        service, mock(ru.rutcampustrack.academic.security.RequestContext.class));
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new ExportJobExceptionAdvice())
                        .build();
        var response =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/academic/report-download/" + marker))
                        .andExpect(status().isNotFound())
                        .andReturn()
                        .getResponse();
        org.assertj.core.api.Assertions.assertThat(response.getContentAsString())
                .doesNotContain(marker)
                .contains("/academic/report-download/{ticket}");
        org.assertj.core.api.Assertions.assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store");
    }

    @Test
    void rosterSelectorIsClosedFormatOnlyAndBindsKindAndFormat() {
        var mapper =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .enable(
                                com.fasterxml.jackson.databind.DeserializationFeature
                                        .FAIL_ON_UNKNOWN_PROPERTIES);
        for (ReportDownloadFormat format : ReportDownloadFormat.values()) {
            var roster =
                    new IssueReportDownloadTicketRequest(
                            ReportDownloadKind.HEADMAN_GROUP_COMPOSITION,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(
                                    format));
            org.junit.jupiter.api.Assertions.assertTrue(roster.isParametersConsistent());
            org.junit.jupiter.api.Assertions.assertEquals(
                    format.mediaType(), roster.expectedMediaType());
            org.junit.jupiter.api.Assertions.assertEquals(
                    "headman-group-composition." + format.filenameExtension(),
                    roster.suggestedFilename());
            var another =
                    new IssueReportDownloadTicketRequest(
                            ReportDownloadKind.HEADMAN_GROUP_COMPOSITION,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(
                                    format == ReportDownloadFormat.PDF
                                            ? ReportDownloadFormat.HTML
                                            : ReportDownloadFormat.PDF));
            org.junit.jupiter.api.Assertions.assertNotEquals(
                    roster.bindingHash(), another.bindingHash());
        }
        org.junit.jupiter.api.Assertions.assertThrows(
                com.fasterxml.jackson.databind.JsonMappingException.class,
                () ->
                        mapper.readValue(
                                "{\"format\":\"pdf\",\"groupId\":99}",
                                IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters
                                        .class));
        var conflicting =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanStatsParameters(
                                null, null, null, null, ReportDownloadFormat.PDF),
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(
                                ReportDownloadFormat.PDF));
        org.junit.jupiter.api.Assertions.assertFalse(conflicting.isParametersConsistent());
    }

    @Test
    void trendTicketBindsItsSelectorAndUsesActualPngWithoutChangingLegacyPngZip() throws Exception {
        var selector =
                new IssueReportDownloadTicketRequest.HeadmanStatsTrendParameters(
                        IssueReportDownloadTicketRequest.HeadmanStatsTrendMode.WEEK,
                        LocalDate.parse("2026-09-21"),
                        null,
                        null,
                        ReportDownloadFormat.PNG);
        var png =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS_TREND,
                        null,
                        null,
                        null,
                        null,
                        null,
                        selector);
        var html =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS_TREND,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanStatsTrendParameters(
                                IssueReportDownloadTicketRequest.HeadmanStatsTrendMode.WEEK,
                                LocalDate.parse("2026-09-21"),
                                null,
                                null,
                                ReportDownloadFormat.HTML));
        var tablePng =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanStatsParameters(
                                null, null, null, null, ReportDownloadFormat.PNG));

        org.junit.jupiter.api.Assertions.assertTrue(png.isParametersConsistent());
        org.junit.jupiter.api.Assertions.assertEquals(
                "headman-stats-trend.png", png.suggestedFilename());
        org.junit.jupiter.api.Assertions.assertEquals("image/png", png.expectedMediaType());
        org.junit.jupiter.api.Assertions.assertNotEquals(png.bindingHash(), html.bindingHash());
        org.junit.jupiter.api.Assertions.assertEquals(
                "headman-stats.zip", tablePng.suggestedFilename());
        org.junit.jupiter.api.Assertions.assertEquals(
                "application/zip", tablePng.expectedMediaType());
        var day =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS_TREND,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanStatsTrendParameters(
                                selector.mode(),
                                selector.weekStart(),
                                null,
                                null,
                                ReportDownloadFormat.PNG,
                                7L,
                                IssueReportDownloadTicketRequest.HeadmanStatsBucketUnit.DAY));
        var week =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS_TREND,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanStatsTrendParameters(
                                selector.mode(),
                                selector.weekStart(),
                                null,
                                null,
                                ReportDownloadFormat.PNG,
                                7L,
                                IssueReportDownloadTicketRequest.HeadmanStatsBucketUnit.WEEK));
        org.junit.jupiter.api.Assertions.assertNotEquals(png.bindingHash(), day.bindingHash());
        org.junit.jupiter.api.Assertions.assertNotEquals(day.bindingHash(), week.bindingHash());
        var tableSemester =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanStatsParameters(
                                null, null, null, null, ReportDownloadFormat.PNG, 7L));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                tablePng.bindingHash(), tableSemester.bindingHash());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var oldStored =
                mapper.readValue(
                        """
                        {"kind":"HEADMAN_STATS_TREND","headmanStatsTrend":{"mode":"WEEK",
                          "weekStart":"2026-09-21","format":"png"}}
                        """,
                        IssueReportDownloadTicketRequest.class);
        org.junit.jupiter.api.Assertions.assertEquals(png.bindingHash(), oldStored.bindingHash());
        org.junit.jupiter.api.Assertions.assertEquals(
                day.bindingHash(),
                mapper.readValue(
                                mapper.writeValueAsBytes(day),
                                IssueReportDownloadTicketRequest.class)
                        .bindingHash());
    }

    @Test
    void selectorWithAnExtraParameterObjectIsRejectedAsBadRequestBeforeIssuance() throws Exception {
        ExportJobService service = mock(ExportJobService.class);
        var context = mock(ru.rutcampustrack.academic.security.RequestContext.class);
        when(context.getClaims())
                .thenReturn(
                        new ru.rutcampustrack.shared.security.InternalJwtClaims(
                                17L,
                                UUID.fromString("11111111-2222-4333-8444-555555555555"),
                                1,
                                1,
                                "TEACHER",
                                "ACTIVE",
                                31L,
                                false,
                                false));
        AcademicReportTicketController controller =
                new AcademicReportTicketController(service, context);
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new ExportJobExceptionAdvice(),
                                new ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler())
                        .build();
        SessionPrincipal principal =
                new SessionPrincipal(
                        17L,
                        UUID.fromString("11111111-2222-4333-8444-555555555555"),
                        1L,
                        1L,
                        AuthRole.TEACHER,
                        RoleStatus.ACTIVE,
                        31L,
                        false,
                        false);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null);

        mvc.perform(
                        post("/academic/report-download-tickets")
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
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
        for (String invalidSelector :
                java.util.List.of(
                        "{\"kind\":\"HEADMAN_STATS\",\"headmanStats\":{\"semesterId\":0,\"format\":\"pdf\"}}",
                        "{\"kind\":\"HEADMAN_STATS_TREND\",\"headmanStatsTrend\":{\"mode\":\"SEMESTER\",\"semesterId\":-1,\"bucketUnit\":\"DAY\",\"format\":\"png\"}}")) {
            mvc.perform(
                            post("/academic/report-download-tickets")
                                    .principal(authentication)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(invalidSelector))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void journalTicketBindsCompletedAndExactSelectionAndNormalizesMissingFalse() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        String json =
                "{\"kind\":\"TEACHER_JOURNAL\",\"teacherJournal\":{\"semesterId\":1,\"groupId\":31,"
                        + "\"subjectId\":4,\"lessonTypes\":[\"LECTURE\"],\"format\":\"pdf\"";
        var absent = mapper.readValue(json + "}}", IssueReportDownloadTicketRequest.class);
        var explicitFalse =
                mapper.readValue(
                        json + ",\"pastOnly\":false}}", IssueReportDownloadTicketRequest.class);
        var completed =
                mapper.readValue(
                        json + ",\"pastOnly\":true}}", IssueReportDownloadTicketRequest.class);
        var exact =
                mapper.readValue(
                        json + ",\"pastOnly\":true,\"lessonId\":\"101\"}}",
                        IssueReportDownloadTicketRequest.class);
        org.junit.jupiter.api.Assertions.assertEquals(
                absent.bindingHash(), explicitFalse.bindingHash());
        org.junit.jupiter.api.Assertions.assertNotEquals(
                absent.bindingHash(), completed.bindingHash());
        org.junit.jupiter.api.Assertions.assertNotEquals(
                completed.bindingHash(), exact.bindingHash());
        org.junit.jupiter.api.Assertions.assertEquals(
                exact.bindingHash(),
                mapper.readValue(
                                mapper.writeValueAsBytes(exact),
                                IssueReportDownloadTicketRequest.class)
                        .bindingHash());
        for (String id : java.util.List.of("0", "-1", "9223372036854775808", "abc", "")) {
            var invalid =
                    mapper.readValue(
                            json + ",\"lessonId\":\"" + id + "\"}}",
                            IssueReportDownloadTicketRequest.class);
            org.junit.jupiter.api.Assertions.assertFalse(invalid.isParametersConsistent());
        }
    }

    @Test
    void explicitServiceSelectorFailureMapsToBadRequest() throws Exception {
        ExportJobService service = mock(ExportJobService.class);
        when(service.legacyTicket(org.mockito.ArgumentMatchers.any(), any()))
                .thenThrow(
                        ExportJobRepository.error(org.springframework.http.HttpStatus.BAD_REQUEST));
        var context = mock(ru.rutcampustrack.academic.security.RequestContext.class);
        when(context.getClaims())
                .thenReturn(
                        new ru.rutcampustrack.shared.security.InternalJwtClaims(
                                17L,
                                UUID.fromString("11111111-2222-4333-8444-555555555555"),
                                1,
                                1,
                                "TEACHER",
                                "ACTIVE",
                                31L,
                                false,
                                false));
        AcademicReportTicketController controller =
                new AcademicReportTicketController(service, context);
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new ExportJobExceptionAdvice())
                        .build();
        SessionPrincipal principal =
                new SessionPrincipal(
                        17L,
                        UUID.fromString("11111111-2222-4333-8444-555555555555"),
                        1L,
                        1L,
                        AuthRole.TEACHER,
                        RoleStatus.ACTIVE,
                        31L,
                        false,
                        false);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null);

        mvc.perform(
                        post("/academic/report-download-tickets")
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"kind":"TEACHER_JOURNAL","teacherJournal":{
                                          "semesterId":1,"groupId":31,"subjectId":4,
                                          "lessonTypes":["LECTURE"],"format":"pdf"}}
                                        """))
                .andExpect(status().isBadRequest());
    }
}
