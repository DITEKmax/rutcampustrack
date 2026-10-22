package ru.rutcampustrack.attendance.semester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;

@ExtendWith(MockitoExtension.class)
class SemesterCacheServiceTest {

    @Mock private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    private SemesterCacheService cache;

    @BeforeEach
    void setUp() throws Exception {
        AcademicGrpcClient client = new AcademicGrpcClient();
        Field stubField = AcademicGrpcClient.class.getDeclaredField("stub");
        stubField.setAccessible(true);
        stubField.set(client, stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        cache = new SemesterCacheService(client);
    }

    @Test
    void archiveLastActiveSemesterClearsOldIdAndNextReadFindsNewActivation() {
        doReturn(semester(11L)).when(stub).getActiveSemester(any());
        cache.refresh();
        assertThat(cache.getActiveSemesterId()).isEqualTo(11L);

        // The archive-event refresh succeeds while Academic has no active semester.
        doThrow(Status.NOT_FOUND.asRuntimeException()).when(stub).getActiveSemester(any());
        assertDoesNotThrow(cache::refresh);
        assertThat(cache.getActiveSemesterId()).isNull();

        // Activation from no active semester needs no archive event.
        doReturn(semester(12L)).when(stub).getActiveSemester(any());
        assertThat(cache.getActiveSemesterId()).isEqualTo(12L);
    }

    @Test
    void transportFailureDoesNotExposePreviousSemester() {
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(stub).getActiveSemester(any());
        assertDoesNotThrow(cache::init);
        assertThrows(AcademicServiceUnavailableException.class, cache::getActiveSemesterId);

        doReturn(semester(11L)).when(stub).getActiveSemester(any());
        assertThat(cache.getActiveSemesterId()).isEqualTo(11L);

        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(stub).getActiveSemester(any());
        assertThrows(AcademicServiceUnavailableException.class, cache::refresh);
        assertThrows(AcademicServiceUnavailableException.class, cache::getActiveSemesterId);
    }

    private static SemesterResponse semester(long id) {
        return SemesterResponse.newBuilder().setId(id).build();
    }
}
