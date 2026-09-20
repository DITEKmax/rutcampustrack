package ru.rutcampustrack.mobilebff.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ScheduleResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Bucket;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentApiControllerCacheControlTest {

    private final StudentQueryService queries = mock(StudentQueryService.class);
    private final StudentCheckinFacade checkins = mock(StudentCheckinFacade.class);
    private final StudentRequestFacade requests = mock(StudentRequestFacade.class);
    private StudentApiController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentApiController(queries, checkins, requests, new ObjectMapper());
    }

    @Test
    void schedule200UsesPrivateNoCacheAndEtag() {
        when(queries.schedule(30L)).thenReturn(schedule());

        ResponseEntity<ScheduleResponse> response = controller.getSchedule("30", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache, private");
        assertThat(response.getHeaders().getETag()).isNotBlank();
    }

    @Test
    void conditionalSchedule304KeepsPrivateNoCacheAndEtag() {
        when(queries.schedule(30L)).thenReturn(schedule());
        String etag = controller.getSchedule("30", null).getHeaders().getETag();

        ResponseEntity<ScheduleResponse> response = controller.getSchedule("30", etag);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache, private");
        assertThat(response.getHeaders().getETag()).isEqualTo(etag);
    }

    @Test
    void requestsListRetainsNoStore() {
        when(requests.list(Bucket.OPEN, 0, 20)).thenReturn(null);

        ResponseEntity<ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Page> response =
                controller.listRequests(Bucket.OPEN, 0, 20);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    private static ScheduleResponse schedule() {
        return new ScheduleResponse(null, null, null, null, List.of(), Map.of());
    }
}
