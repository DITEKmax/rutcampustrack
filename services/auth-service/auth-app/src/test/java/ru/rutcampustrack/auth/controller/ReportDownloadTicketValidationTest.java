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
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.util.UUID;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportDownloadTicketValidationTest {

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
