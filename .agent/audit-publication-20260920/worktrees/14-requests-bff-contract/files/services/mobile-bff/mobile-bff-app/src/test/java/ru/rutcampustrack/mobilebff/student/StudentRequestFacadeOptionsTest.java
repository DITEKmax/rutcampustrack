package ru.rutcampustrack.mobilebff.student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.attendance.grpc.StudentExcuseReason;
import ru.rutcampustrack.attendance.grpc.StudentRequestBudget;
import ru.rutcampustrack.attendance.grpc.StudentRequestFileLimits;
import ru.rutcampustrack.attendance.grpc.StudentRequestOptions;
import ru.rutcampustrack.attendance.grpc.StudentRequestReasonOption;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ReasonOption;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRequestFacadeOptionsTest {
    @Mock
    private MobileAttendanceClient attendance;
    @Mock
    private MobileRequestContext requestContext;

    @Test
    void mapsCommentRequiredDirectlyFromAttendanceProto() {
        when(requestContext.claims()).thenReturn(new InternalJwtClaims(100L, "STUDENT", 10L, false));
        when(attendance.requestOptions()).thenReturn(StudentRequestOptions.newBuilder()
                .addReasons(StudentRequestReasonOption.newBuilder()
                        .setCode(StudentExcuseReason.STUDENT_EXCUSE_REASON_OTHER)
                        .setLabel("Другое")
                        .setCommentRequired(true)
                        .build())
                .addReasons(StudentRequestReasonOption.newBuilder()
                        .setCode(StudentExcuseReason.STUDENT_EXCUSE_REASON_ILLNESS)
                        .setLabel("Болезнь")
                        .setCommentRequired(false)
                        .build())
                .setFiles(StudentRequestFileLimits.getDefaultInstance())
                .setBudget(StudentRequestBudget.getDefaultInstance())
                .build());

        var result = new StudentRequestFacade(attendance, requestContext).options();

        assertThat(result.reasons()).extracting(ReasonOption::code)
                .containsExactly(ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ExcuseReason.OTHER,
                        ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ExcuseReason.ILLNESS);
        assertThat(result.reasons()).extracting(ReasonOption::commentRequired)
                .containsExactly(true, false);
    }
}