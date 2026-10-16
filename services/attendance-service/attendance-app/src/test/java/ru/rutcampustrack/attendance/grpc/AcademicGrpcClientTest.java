package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckRequest;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SubjectInfo;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsRequest;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsResponse;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;

@ExtendWith(MockitoExtension.class)
class AcademicGrpcClientTest {

    @Mock private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub mockStub;

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
    void getGroup_buildsCorrectRequest() {
        when(mockStub.getGroup(any())).thenReturn(GroupResponse.getDefaultInstance());

        client.getGroup(7L);

        verify(mockStub).getGroup(GroupRequest.newBuilder().setGroupId(7L).build());
    }

    @Test
    void getGroup_notFound_throwsResourceNotFoundException() {
        when(mockStub.getGroup(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> client.getGroup(7L));
    }

    @Test
    void getGroup_unavailable_throwsAcademicServiceUnavailableException() {
        when(mockStub.getGroup(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(AcademicServiceUnavailableException.class, () -> client.getGroup(7L));
    }

    @Test
    void getGroupMembers_buildsCorrectRequest() {
        when(mockStub.getGroupMembers(any())).thenReturn(GroupMembersResponse.getDefaultInstance());

        client.getGroupMembers(7L);

        verify(mockStub).getGroupMembers(GroupMembersRequest.newBuilder().setGroupId(7L).build());
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
    void getHistoricalGroupMembers_buildsPairedDatedRequest() {
        when(mockStub.getGroupMembers(any())).thenReturn(GroupMembersResponse.getDefaultInstance());

        client.getGroupMembers(7L, LocalDate.of(2026, 4, 20), 3L);

        verify(mockStub)
                .getGroupMembers(
                        GroupMembersRequest.newBuilder()
                                .setGroupId(7L)
                                .setAsOfDate("2026-04-20")
                                .setSemesterId(3L)
                                .build());
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

        verify(mockStub)
                .isHeadman(HeadmanCheckRequest.newBuilder().setUserId(100L).setGroupId(7L).build());
    }

    @Test
    void isHeadman_unavailable_throwsAcademicServiceUnavailableException() {
        when(mockStub.isHeadman(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(AcademicServiceUnavailableException.class, () -> client.isHeadman(100L, 7L));
    }

    @Test
    void getSubjectDetailsByIds_returnsNamesAndTypes() {
        when(mockStub.getSubjectsByIds(any()))
                .thenReturn(
                        SubjectsByIdsResponse.newBuilder()
                                .addSubjects(
                                        SubjectInfo.newBuilder()
                                                .setSubjectId(5L)
                                                .setSubjectName("Math")
                                                .setSubjectType("lab")
                                                .build())
                                .build());

        var result = client.getSubjectDetailsByIds(List.of(5L));

        assertThat(result).containsEntry(5L, new AcademicGrpcClient.SubjectDetails("Math", "lab"));
        verify(mockStub)
                .getSubjectsByIds(SubjectsByIdsRequest.newBuilder().addSubjectIds(5L).build());
    }

    @Test
    void subjectOwnerAndActualTypesRemainDistinctFromLegacyPrimaryType() {
        when(mockStub.getSubjectsByIds(any()))
                .thenReturn(
                        SubjectsByIdsResponse.newBuilder()
                                .addSubjects(
                                        SubjectInfo.newBuilder()
                                                .setSubjectId(5)
                                                .setSubjectName("Math")
                                                .setSubjectType("lecture")
                                                .setGroupId(10)
                                                .addAllLessonTypes(List.of("lecture", "practice"))
                                                .build())
                                .build());
        assertThat(client.getSubjectDetailsByIds(List.of(5L)))
                .containsEntry(
                        5L,
                        new AcademicGrpcClient.SubjectDetails(
                                "Math", "lecture", 10, List.of("lecture", "practice")));
    }

    @Test
    void getSubjectsByIds_keepsNameOnlyCompatibility() {
        when(mockStub.getSubjectsByIds(any()))
                .thenReturn(
                        SubjectsByIdsResponse.newBuilder()
                                .addSubjects(
                                        SubjectInfo.newBuilder()
                                                .setSubjectId(5L)
                                                .setSubjectName("Math")
                                                .setSubjectType("lecture")
                                                .build())
                                .build());

        assertThat(client.getSubjectsByIds(List.of(5L))).containsEntry(5L, "Math");
    }

    @Test
    void auditNamesRemainOptionalWithinPageDeadlineAndKeepExistingRequestNameSemantics() {
        when(mockStub.getUserById(any()))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.UserResponse.newBuilder().setId(8).build());
        assertThat(client.getUserDisplayName(8L)).isEqualTo("Студент #8");
        when(mockStub.withDeadline(any())).thenReturn(mockStub);
        when(mockStub.getUserById(any()))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.UserResponse.newBuilder()
                                .setId(8)
                                .setDisplayName("Иванов Иван Ильич")
                                .build())
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.UserResponse.newBuilder().setId(8).build())
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.UserResponse.newBuilder()
                                .setId(9)
                                .setDisplayName("Другой Автор")
                                .build())
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        var deadline = io.grpc.Deadline.after(1, java.util.concurrent.TimeUnit.MINUTES);
        assertThat(client.getAuditUserDisplayName(8L, deadline)).isEqualTo("Иванов Иван Ильич");
        for (int attempt = 0; attempt < 4; attempt++)
            assertThat(client.getAuditUserDisplayName(8L, deadline)).isNull();
        org.mockito.Mockito.clearInvocations(mockStub);
        assertThat(
                        client.getAuditUserDisplayName(
                                8L,
                                io.grpc.Deadline.after(
                                        -1, java.util.concurrent.TimeUnit.NANOSECONDS)))
                .isNull();
        org.mockito.Mockito.verify(mockStub, org.mockito.Mockito.never()).getUserById(any());
    }

    @Test
    void semesterStateCredentialsBindOnlyExactReadAuthorityAndConfidentialTransport()
            throws Exception {
        byte[] bytes = new byte[DirectedServiceCredential.TOKEN_BYTES];
        java.util.Arrays.fill(bytes, (byte) 0x67);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        setField("attendanceToAcademicToken", token);
        setField("academicAuthority", "academic-service");
        when(mockStub.withCallCredentials(any())).thenReturn(mockStub);
        when(mockStub.getSemesterState(any()))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(3)
                                .build());
        assertThat(client.getSemesterState(3).getId()).isEqualTo(3);
        verify(mockStub)
                .getSemesterState(
                        ru.rutcampustrack.academic.grpc.SemesterStateRequest.newBuilder()
                                .setSemesterId(3)
                                .build());
        var captured = ArgumentCaptor.forClass(CallCredentials.class);
        verify(mockStub).withCallCredentials(captured.capture());
        String method = ServiceIdentityServerInterceptor.ACADEMIC_GET_SEMESTER_STATE;
        assertCredentialResult(
                captured.getValue(),
                method,
                "academic-service",
                SecurityLevel.PRIVACY_AND_INTEGRITY,
                token,
                null);
        assertCredentialResult(
                captured.getValue(),
                method,
                "academic-service",
                SecurityLevel.NONE,
                token,
                Status.Code.UNAUTHENTICATED);
        assertCredentialResult(
                captured.getValue(),
                method,
                "foreign-service",
                SecurityLevel.PRIVACY_AND_INTEGRITY,
                token,
                Status.Code.PERMISSION_DENIED);
        assertCredentialResult(
                captured.getValue(),
                ServiceIdentityServerInterceptor.ACADEMIC_GET_PREPARED_ASSIGNMENT_CLOSE_OPERATION,
                "academic-service",
                SecurityLevel.PRIVACY_AND_INTEGRITY,
                token,
                Status.Code.PERMISSION_DENIED);
        when(mockStub.getGroup(any())).thenReturn(GroupResponse.getDefaultInstance());
        client.getGroup(7L);
        // One call only: unrelated Academic reads keep their existing uncredentialed stub.
        verify(mockStub, org.mockito.Mockito.times(1)).withCallCredentials(any());
    }

    @Test
    void semesterStateMissingOrMalformedIdentityFailsBeforeRpcAndKeepsResponseChecks()
            throws Exception {
        when(mockStub.withCallCredentials(any())).thenReturn(mockStub);
        setField("academicAuthority", "academic-service");
        byte[] bytes = new byte[DirectedServiceCredential.TOKEN_BYTES];
        java.util.Arrays.fill(bytes, (byte) 0x67);
        setField(
                "attendanceToAcademicToken",
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        when(mockStub.getSemesterState(any()))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(4)
                                .build());
        assertThrows(AcademicServiceUnavailableException.class, () -> client.getSemesterState(3));
        for (String missing : new String[] {null, "", "malformed"}) {
            setField("attendanceToAcademicToken", missing);
            assertThrows(
                    AcademicServiceUnavailableException.class, () -> client.getSemesterState(3));
        }
        verify(mockStub, org.mockito.Mockito.times(1)).getSemesterState(any());
    }

    private void setField(String name, String value) throws Exception {
        Field field = AcademicGrpcClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(client, value);
    }

    private static void assertCredentialResult(
            CallCredentials credentials,
            String method,
            String authority,
            SecurityLevel security,
            String token,
            Status.Code expectedFailure) {
        AtomicReference<Metadata> applied = new AtomicReference<>();
        AtomicReference<Status> failed = new AtomicReference<>();
        credentials.applyRequestMetadata(
                new CallCredentials.RequestInfo() {
                    @Override
                    public MethodDescriptor<?, ?> getMethodDescriptor() {
                        MethodDescriptor<?, ?> descriptor =
                                org.mockito.Mockito.mock(MethodDescriptor.class);
                        when(descriptor.getFullMethodName()).thenReturn(method);
                        return descriptor;
                    }

                    @Override
                    public SecurityLevel getSecurityLevel() {
                        return security;
                    }

                    @Override
                    public String getAuthority() {
                        return authority;
                    }

                    @Override
                    public Attributes getTransportAttrs() {
                        return Attributes.EMPTY;
                    }
                },
                Runnable::run,
                new CallCredentials.MetadataApplier() {
                    @Override
                    public void apply(Metadata headers) {
                        applied.set(headers);
                    }

                    @Override
                    public void fail(Status status) {
                        failed.set(status);
                    }
                });
        if (expectedFailure == null) {
            assertThat(failed.get()).isNull();
            assertThat(applied.get() != null).as("directed identity must be emitted").isTrue();
            assertThat(
                            token.equals(
                                    applied.get()
                                            .get(DirectedServiceCredential.TOKEN_METADATA_KEY)))
                    .as("directed identity must match without disclosing the token")
                    .isTrue();
        } else {
            assertThat(applied.get() == null).as("denied scope must never emit identity").isTrue();
            assertThat(failed.get().getCode()).isEqualTo(expectedFailure);
        }
    }
}
