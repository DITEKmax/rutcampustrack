package ru.rutcampustrack.notification.exception;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import ru.rutcampustrack.notification.history.*;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesV2Service;
import ru.rutcampustrack.notification.security.RequestContext;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

class NotificationUnavailableBoundaryTest {
    @Test void unavailableAuthorityReturnsSafeRetryableResponseWithoutPretendingEmptyPreferences() throws Exception {
        for (RuntimeException failure : new RuntimeException[] {
                new DataAccessResourceFailureException("private-context-test-marker"),
                new CannotCreateTransactionException("private-context-test-marker"),
                new MongoException("private-context-test-marker")}) {
            RequestContext context = mock(RequestContext.class);
            when(context.requireSelectedRole()).thenReturn("STUDENT");
            when(context.getUserId()).thenReturn(11L);
            NotificationPreferencesV2Service preferences = mock(NotificationPreferencesV2Service.class);
            when(preferences.get(11L)).thenThrow(failure);
            var mvc = MockMvcBuilders.standaloneSetup(new NotificationInboxController(context,
                    new NotificationCatalog(), mock(NotificationInboxService.class), preferences))
                    .setControllerAdvice(new NotificationExceptionHandler()).build();
            mvc.perform(get("/notifications/preferences/v2"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(content().string(not(containsString("private-context-test-marker"))))
                    .andExpect(jsonPath("$.types").doesNotExist());
        }
    }
}
