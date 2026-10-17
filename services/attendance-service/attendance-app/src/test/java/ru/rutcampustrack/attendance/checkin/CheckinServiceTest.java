package ru.rutcampustrack.attendance.checkin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.exception.LegacyCheckinRetiredException;

class CheckinServiceTest {

    @Test
    void legacyCheckinFailsClosedBeforeAnyWritePath() {
        CheckinService service = new CheckinService();

        assertThatThrownBy(() -> service.checkin(new CheckinRequest(55.7558, 37.6173)))
                .isInstanceOf(LegacyCheckinRetiredException.class)
                .hasMessage("Используйте канонический student check-in API");
    }
}
