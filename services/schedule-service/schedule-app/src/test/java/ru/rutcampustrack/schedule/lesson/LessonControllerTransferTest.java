package ru.rutcampustrack.schedule.lesson;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonResponse;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonControllerTransferTest {

    @Test
    void errorConflictResponseRetainsTransferBody() {
        LessonService lessonService = mock(LessonService.class);
        LessonTransferService transferService = mock(LessonTransferService.class);
        LessonAssembler lessonAssembler = mock(LessonAssembler.class);
        LessonController controller = new LessonController(lessonService, transferService, lessonAssembler);
        TransferLessonRequest request = new TransferLessonRequest(
                LocalDate.of(2090, 1, 3), 2, null, null, null, "1", UUID.randomUUID());
        TransferLessonResponse error = new TransferLessonResponse(
                UUID.randomUUID().toString(), "ERROR", "500", "600", "601", "2", false,
                "TARGET_DATA_CONFLICT", request.targetDate());
        when(transferService.transfer(600L, request)).thenReturn(error);

        var response = controller.transferLesson(600L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(error);
        assertThat(response.getHeaders().getLocation()).hasToString(
                "/schedule/lesson-transfers/" + error.operationId());
    }
}
