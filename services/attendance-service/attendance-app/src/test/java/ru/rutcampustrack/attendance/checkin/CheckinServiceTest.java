package ru.rutcampustrack.attendance.checkin;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.exception.LegacyCheckinRetiredException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckinServiceTest {

    @Test
    void legacyCheckinFailsClosedBeforeAnyWritePath() {
        CheckinService service = new CheckinService();

        assertThatThrownBy(() -> service.checkin(new CheckinRequest(55.7558, 37.6173)))
                .isInstanceOf(LegacyCheckinRetiredException.class)
                .hasMessage("Используйте канонический student check-in API");
    }
}
