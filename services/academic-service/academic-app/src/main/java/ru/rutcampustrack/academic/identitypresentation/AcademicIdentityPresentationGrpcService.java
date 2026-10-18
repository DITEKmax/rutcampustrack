package ru.rutcampustrack.academic.identitypresentation;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.authauthority.grpc.AcademicIdentityPresentationServiceGrpc;
import ru.rutcampustrack.authauthority.grpc.GroupPresentation;
import ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibility;
import ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibilityRequest;
import ru.rutcampustrack.authauthority.grpc.IdentityPresentationRequest;
import ru.rutcampustrack.authauthority.grpc.IdentityPresentationResponse;
import ru.rutcampustrack.authauthority.grpc.UserPresentation;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.List;

/** Pure bounded labels. It has no Auth client and cannot create a callback admission cycle. */
@GrpcService
public class AcademicIdentityPresentationGrpcService
        extends AcademicIdentityPresentationServiceGrpc
                .AcademicIdentityPresentationServiceImplBase {
    private final JdbcTemplate jdbc;
    private final AcademicIdentityReadFence fences;

    public AcademicIdentityPresentationGrpcService(
            JdbcTemplate jdbc, AcademicIdentityReadFence fences) {
        this.jdbc = jdbc;
        this.fences = fences;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void resolveHeadmanCredentialEligibility(
            HeadmanCredentialEligibilityRequest request,
            StreamObserver<HeadmanCredentialEligibility> observer) {
        try {
            if (!IdentityPresentationServiceIdentityInterceptor.AUTH.equals(
                    ServicePrincipal.CONTEXT_KEY.get()))
                throw Status.UNAUTHENTICATED
                        .withDescription("verified Auth service required")
                        .asRuntimeException();
            if (request.getActorUserId() <= 0
                    || request.getStudentUserId() <= 0
                    || request.getExpectedActorRolesVersion() <= 0
                    || request.getExpectedStudentRolesVersion() <= 0)
                throw Status.INVALID_ARGUMENT.asRuntimeException();
            var scopes =
                    jdbc.query(
                            """
SELECT h.group_id,a.academic_revision actor_revision,s.academic_revision student_revision,
  a.applied_auth_roles_version actor_rv,s.applied_auth_roles_version student_rv
  FROM academic_profiles a
  JOIN academic_role_eligibility h ON h.user_id=a.id AND h.role='HEADMAN' AND h.status='ACTIVE'
  JOIN academic_role_eligibility ast ON ast.user_id=a.id AND ast.role='STUDENT'
    AND ast.status='ACTIVE' AND ast.group_id=h.group_id
  JOIN groups g ON g.id=h.group_id AND g.is_active
  JOIN academic_profiles s ON s.id=? AND s.archived_at IS NULL
  JOIN academic_role_eligibility st ON st.user_id=s.id AND st.role='STUDENT'
    AND st.status='ACTIVE' AND st.group_id=h.group_id
 WHERE a.id=? AND a.archived_at IS NULL
 ORDER BY h.group_id LIMIT 2
""",
                            (rs, n) ->
                                    HeadmanCredentialEligibility.newBuilder()
                                            .setGroupId(rs.getLong("group_id"))
                                            .setActorRolesVersion(rs.getLong("actor_rv"))
                                            .setStudentRolesVersion(rs.getLong("student_rv"))
                                            .setActorAcademicRevision(rs.getLong("actor_revision"))
                                            .setStudentAcademicRevision(
                                                    rs.getLong("student_revision"))
                                            .build(),
                            request.getStudentUserId(),
                            request.getActorUserId());
            if (scopes.size() != 1) throw Status.PERMISSION_DENIED.asRuntimeException();
            var scope = scopes.getFirst();
            if (scope.getActorRolesVersion() != request.getExpectedActorRolesVersion()
                    || scope.getStudentRolesVersion() != request.getExpectedStudentRolesVersion())
                throw Status.FAILED_PRECONDITION.asRuntimeException();
            fences.requireUserReadable(request.getActorUserId());
            fences.requireUserReadable(request.getStudentUserId());
            fences.requireGroupReadable(scope.getGroupId());
            observer.onNext(scope);
            observer.onCompleted();
        } catch (StatusRuntimeException denied) {
            observer.onError(denied);
        } catch (RuntimeException unavailable) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("credential domain eligibility unavailable")
                            .asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void resolveIdentityPresentation(
            IdentityPresentationRequest request,
            StreamObserver<IdentityPresentationResponse> observer) {
        try {
            if (!IdentityPresentationServiceIdentityInterceptor.AUTH.equals(
                    ServicePrincipal.CONTEXT_KEY.get())) {
                throw Status.UNAUTHENTICATED
                        .withDescription("verified Auth service required")
                        .asRuntimeException();
            }
            validate(request.getUserIdsList(), true);
            validate(request.getGroupIdsList(), false);
            var response = IdentityPresentationResponse.newBuilder();
            response.addAllUsers(
                    jdbc.query(
                            "SELECT id,last_name,first_name,middle_name FROM academic_profiles"
                                    + " WHERE id IN ("
                                    + placeholders(request.getUserIdsCount())
                                    + ") ORDER BY id",
                            (rs, n) -> {
                                String middle = rs.getString("middle_name");
                                String last = rs.getString("last_name");
                                String first = rs.getString("first_name");
                                return UserPresentation.newBuilder()
                                        .setUserId(rs.getLong("id"))
                                        .setLastName(last)
                                        .setFirstName(first)
                                        .setMiddleName(middle == null ? "" : middle)
                                        .setDisplayName(
                                                last
                                                        + " "
                                                        + first
                                                        + (middle == null || middle.isBlank()
                                                                ? ""
                                                                : " " + middle))
                                        .build();
                            },
                            request.getUserIdsList().toArray()));
            if (request.getGroupIdsCount() > 0) {
                response.addAllGroups(
                        jdbc.query(
                                "SELECT id,name FROM groups WHERE id IN ("
                                        + placeholders(request.getGroupIdsCount())
                                        + ") ORDER BY id",
                                (rs, n) ->
                                        GroupPresentation.newBuilder()
                                                .setGroupId(rs.getLong("id"))
                                                .setLabel(rs.getString("name"))
                                                .build(),
                                request.getGroupIdsList().toArray()));
            }
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (StatusRuntimeException error) {
            observer.onError(error);
        } catch (RuntimeException error) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("identity presentation unavailable")
                            .asRuntimeException());
        }
    }

    private static void validate(List<Long> ids, boolean required) {
        if (ids.size() > 200
                || required && ids.isEmpty()
                || ids.stream().anyMatch(id -> id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("bounded distinct positive IDs required")
                    .asRuntimeException();
        }
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
