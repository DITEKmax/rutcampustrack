package ru.rutcampustrack.mobilebff.error;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import static org.assertj.core.api.Assertions.assertThat;

class EducationalProblemClockIsolationTest {
    @Test
    void retryAfterUsesDomainTimeAndProblemTimestampKeepsRealTime() {
        Instant realNow = Instant.parse("2026-10-11T23:00:00Z");
        Instant teachingNow = Instant.parse("2026-10-12T06:00:00Z");
        var handler = new MobileProblemHandler(Clock.fixed(realNow, ZoneOffset.UTC),
                new EducationalTime(Clock.fixed(teachingNow, ZoneOffset.UTC)));
        var error = new MobileBffException(HttpStatus.TOO_MANY_REQUESTS, ProblemCode.CHECKIN_COOLDOWN,
                "Synthetic cooldown", teachingNow.plusSeconds(45));
        var response = handler.handle(error, new MockHttpServletRequest("POST", "/api/v1/student/checkin"));
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("45");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isEqualTo(realNow);
        assertThat(response.getBody().retryAt()).isEqualTo(teachingNow.plusSeconds(45));
    }
}
