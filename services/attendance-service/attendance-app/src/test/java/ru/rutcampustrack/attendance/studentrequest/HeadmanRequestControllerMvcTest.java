package ru.rutcampustrack.attendance.studentrequest;

import org.junit.jupiter.api.BeforeEach;
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
import ru.rutcampustrack.attendance.exception.GlobalExceptionHandler;
import ru.rutcampustrack.attendance.security.AttendanceUserContextFilter;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentDownload;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = HeadmanRequestController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AttendanceUserContextFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler.class
})
class HeadmanRequestControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HeadmanRequestService service;

    @MockitoBean
    private RequestContext requestContext;

    @BeforeEach
    void setUpRequestContext() {
        when(requestContext.getUserId()).thenReturn(42L);
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.getGroupId()).thenReturn(7L);
        when(requestContext.isHeadman()).thenReturn(true);
    }

    @Test
    void downloadAttachmentNegotiatesBinaryResponseAndRejectsJsonAccept() throws Exception {
        byte[] bytes = new byte[]{0x25, 0x50, 0x44, 0x46};
        when(service.downloadAttachment(any(), eq("request-1"), eq("attachment-1")))
                .thenReturn(new AttachmentDownload(bytes, MediaType.APPLICATION_PDF_VALUE, "report.pdf"));

        mockMvc.perform(get("/attendance/requests/request-1/attachments/attachment-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.type")
                        .value(ErrorResponse.PROBLEM_BASE + "media-type-not-acceptable"));

        mockMvc.perform(get("/attendance/requests/request-1/attachments/attachment-1")
                        .accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("report.pdf")))
                .andExpect(content().bytes(bytes));
    }
}
