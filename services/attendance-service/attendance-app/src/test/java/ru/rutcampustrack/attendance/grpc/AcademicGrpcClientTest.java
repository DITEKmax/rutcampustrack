package ru.rutcampustrack.attendance.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckRequest;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicGrpcClientTest {

    @Mock
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub mockStub;

    private AcademicGrpcClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new AcademicGrpcClient();
        Field stubField = AcademicGrpcClient.class.getDeclaredField("stub");
        stubField.setAccessible(true);
        stubField.set(client, mockStub);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(mockStub);
    }

    @Test
    void getGroupMembers_buildsCorrectRequest() {
        when(mockStub.getGroupMembers(any())).thenReturn(GroupMembersResponse.getDefaultInstance());

        client.getGroupMembers(7L);

        verify(mockStub).getGroupMembers(GroupMembersRequest.newBuilder()
                .setGroupId(7L)
                .build());
    }

    @Test
    void getGroupMembers_notFound_throwsResourceNotFoundException() {
        when(mockStub.getGroupMembers(any()))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> client.getGroupMembers(7L));
    }

    @Test
    void getGroupMembers_unavailable_throwsAcademicServiceUnavailableException() {
        when(mockStub.getGroupMembers(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(AcademicServiceUnavailableException.class, () -> client.getGroupMembers(7L));
    }

    @Test
    void getCampusGeofence_buildsCorrectRequest() {
        when(mockStub.getCampusGeofence(any())).thenReturn(GeofenceResponse.getDefaultInstance());

        client.getCampusGeofence();

        verify(mockStub).getCampusGeofence(Empty.getDefaultInstance());
    }

    @Test
    void getCampusGeofence_unavailable_throwsAcademicServiceUnavailableException() {
        when(mockStub.getCampusGeofence(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(AcademicServiceUnavailableException.class, () -> client.getCampusGeofence());
    }

    @Test
    void getActiveSemester_notFound_throwsResourceNotFoundException() {
        when(mockStub.getActiveSemester(any()))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> client.getActiveSemester());
    }

    @Test
    void getActiveSemester_buildsCorrectRequest() {
        when(mockStub.getActiveSemester(any())).thenReturn(SemesterResponse.getDefaultInstance());

        client.getActiveSemester();

        verify(mockStub).getActiveSemester(Empty.getDefaultInstance());
    }

    @Test
    void isHeadman_buildsCorrectRequest() {
        when(mockStub.isHeadman(any())).thenReturn(HeadmanCheckResponse.getDefaultInstance());

        client.isHeadman(100L, 7L);

        verify(mockStub).isHeadman(HeadmanCheckRequest.newBuilder()
                .setUserId(100L)
                .setGroupId(7L)
                .build());
    }

    @Test
    void isHeadman_unavailable_throwsAcademicServiceUnavailableException() {
        when(mockStub.isHeadman(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(AcademicServiceUnavailableException.class, () -> client.isHeadman(100L, 7L));
    }
}
