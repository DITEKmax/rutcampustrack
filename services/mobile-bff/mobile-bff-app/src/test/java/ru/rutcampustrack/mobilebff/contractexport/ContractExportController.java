package ru.rutcampustrack.mobilebff.contractexport;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.mobilebff.contract.api.StudentApi;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinAck;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ScheduleResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.SessionResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.TodayResponse;

/**
 * Test-only Springdoc seam. It exposes signatures for export and is never packaged
 * into the application; real handlers are deliberately outside the foundation.
 */
@RestController
@Validated
class ContractExportController implements StudentApi {

    @Override
    public ResponseEntity<SessionResponse> getSession() {
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<TodayResponse> getToday() {
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<ScheduleResponse> getSchedule(String semesterId, String ifNoneMatch) {
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<CheckinAck> checkin(
            String lessonId,
            String idempotencyKey,
            CheckinRequest request
    ) {
        return ResponseEntity.notFound().build();
    }
}
