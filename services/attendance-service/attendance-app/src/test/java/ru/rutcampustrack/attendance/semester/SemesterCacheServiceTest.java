package ru.rutcampustrack.attendance.semester;

import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemesterCacheServiceTest {

    @Mock
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

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
        when(stub.getActiveSemester(any()))
                .thenReturn(semester(11L))
                .thenThrow(Status.NOT_FOUND.asRuntimeException())
                .thenThrow(Status.NOT_FOUND.asRuntimeException())
                .thenReturn(semester(12L));

        cache.refresh();
        assertThat(cache.getActiveSemesterId()).isEqualTo(11L);

        // The archive-event refresh can complete successfully while Academic has no active semester.
        assertDoesNotThrow(cache::refresh);
        assertThat(cache.getActiveSemesterId()).isNull();

        // Activation from no active semester needs no archive event: a lazy read discovers it.
        assertThat(cache.getActiveSemesterId()).isEqualTo(12L);
    }

    @Test
    void transportFailurePropagatesOnRefreshAndKeepsExistingCachePolicy() {
        when(stub.getActiveSemester(any()))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException())
                .thenReturn(semester(11L))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());

        assertThat(cache.getActiveSemesterId()).isNull();
        assertThat(cache.getActiveSemesterId()).isEqualTo(11L);

        assertThrows(AcademicServiceUnavailableException.class, cache::refresh);
        assertThat(cache.getActiveSemesterId()).isEqualTo(11L);
    }

    private static SemesterResponse semester(long id) {
        return SemesterResponse.newBuilder().setId(id).build();
    }
}
