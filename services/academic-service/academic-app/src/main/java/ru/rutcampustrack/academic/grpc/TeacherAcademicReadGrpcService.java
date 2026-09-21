package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.teacher.grpc.TeacherAcademicReadServiceGrpc;
import ru.rutcampustrack.teacher.grpc.TeacherAssignment;
import ru.rutcampustrack.teacher.grpc.TeacherActiveSemesterRequest;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsRequest;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherSemesterResponse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** Dated teacher assignment authority for the teacher PWA/TMA. */
@GrpcService
public final class TeacherAcademicReadGrpcService
        extends TeacherAcademicReadServiceGrpc.TeacherAcademicReadServiceImplBase {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final String TEACHER_ROLE = "teacher";
    private static final String ACTIVE_STATUS = "active";
    private static final int MAX_RANGE_DAYS = 366;

    private final AssignmentRepository assignmentRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final UserRoleGrantRepository grantRepository;

    public TeacherAcademicReadGrpcService(AssignmentRepository assignmentRepository,
                                           GroupRepository groupRepository,
                                           SemesterRepository semesterRepository,
                                           SubjectRepository subjectRepository,
                                           UserRoleGrantRepository grantRepository) {
        this.assignmentRepository = assignmentRepository;
        this.groupRepository = groupRepository;
        this.semesterRepository = semesterRepository;
        this.subjectRepository = subjectRepository;
        this.grantRepository = grantRepository;
    }

    @Override
    public void getTeacherActiveSemester(TeacherActiveSemesterRequest request,
                                         StreamObserver<TeacherSemesterResponse> responseObserver) {
        try {
            InternalJwtClaims claims = TeacherAcademicGrpcIdentity.requireClaims();
            requireTeacher(claims);
            if (grantRepository.findByUserIdAndRoleAndStatus(
                    claims.userId(), TEACHER_ROLE, ACTIVE_STATUS).isEmpty()) {
                throw Status.PERMISSION_DENIED.withDescription("teacher grant is inactive")
                        .asRuntimeException();
            }
            Semester semester = semesterRepository.findByIsActiveTrue()
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("active semester not found")
                            .asRuntimeException());
            if (semester.getDateFrom() == null || semester.getDateTo() == null) {
                throw Status.FAILED_PRECONDITION.withDescription("active semester has no date range")
                        .asRuntimeException();
            }
            responseObserver.onNext(TeacherSemesterResponse.newBuilder()
                    .setSemesterId(semester.getId())
                    .setName(semester.getName())
                    .setDateFrom(semester.getDateFrom().toString())
                    .setDateTo(semester.getDateTo().toString())
                    .build());
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(error);
        }
    }

    @Override
    public void listTeacherAssignments(TeacherAssignmentsRequest request,
                                       StreamObserver<TeacherAssignmentsResponse> responseObserver) {
        try {
            InternalJwtClaims claims = TeacherAcademicGrpcIdentity.requireClaims();
            requireTeacher(claims);
            if (request.getSemesterId() <= 0) {
                throw Status.INVALID_ARGUMENT.withDescription("semester_id must be positive")
                        .asRuntimeException();
            }
            Semester semester = semesterRepository.findById(request.getSemesterId())
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("semester not found")
                            .asRuntimeException());
            if (semester.getDateFrom() == null || semester.getDateTo() == null) {
                throw Status.FAILED_PRECONDITION.withDescription("semester has no date range")
                        .asRuntimeException();
            }
            LocalDate dateFrom = request.getFullSemester()
                    ? semester.getDateFrom()
                    : parseDate(request.getDateFrom(), "date_from");
            LocalDate dateTo = request.getFullSemester()
                    ? semester.getDateTo()
                    : parseDate(request.getDateTo(), "date_to");
            if (dateTo.isBefore(dateFrom)) {
                throw Status.INVALID_ARGUMENT.withDescription("date_to must not precede date_from")
                        .asRuntimeException();
            }
            if (!request.getFullSemester()
                    && dateFrom.plusDays(MAX_RANGE_DAYS).isBefore(dateTo)) {
                throw Status.INVALID_ARGUMENT.withDescription("assignment range is too large")
                        .asRuntimeException();
            }
            if (grantRepository.findByUserIdAndRoleAndStatus(
                    claims.userId(), TEACHER_ROLE, ACTIVE_STATUS).isEmpty()) {
                throw Status.PERMISSION_DENIED.withDescription("teacher grant is inactive")
                        .asRuntimeException();
            }

            List<TeacherAssignment> assignments = assignmentRepository
                    .findByTeacherIdAndSemesterId(claims.userId(), request.getSemesterId())
                    .stream()
                    .filter(assignment -> overlaps(assignment, semester, dateFrom, dateTo))
                    .map(assignment -> toAssignment(assignment, semester))
                    .flatMap(java.util.Optional::stream)
                    .sorted(Comparator.comparing(TeacherAssignment::getGroupName)
                            .thenComparing(TeacherAssignment::getSubjectName)
                            .thenComparing(TeacherAssignment::getLessonType)
                            .thenComparing(TeacherAssignment::getValidFrom)
                            .thenComparingLong(TeacherAssignment::getAssignmentId))
                    .toList();

            responseObserver.onNext(TeacherAssignmentsResponse.newBuilder()
                    .addAllAssignments(assignments)
                    .setServerNow(OffsetDateTime.now(MOSCOW).toString())
                    .build());
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(error);
        }
    }

    private static void requireTeacher(InternalJwtClaims claims) {
        if (!"TEACHER".equals(claims.domainRole())
                || !"ACTIVE".equals(claims.status())
                || claims.userId() <= 0) {
            throw Status.PERMISSION_DENIED.withDescription("teacher role is required")
                    .asRuntimeException();
        }
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription(field + " is required")
                    .asRuntimeException();
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw Status.INVALID_ARGUMENT.withDescription(field + " must be ISO-8601 date")
                    .asRuntimeException();
        }
    }

    private static boolean overlaps(Assignment assignment,
                                    Semester semester,
                                    LocalDate dateFrom,
                                    LocalDate dateTo) {
        LocalDate endExclusive = assignment.getValidUntilExclusive() != null
                ? assignment.getValidUntilExclusive()
                : semester.getDateTo().plusDays(1);
        return !assignment.getValidFrom().isAfter(dateTo)
                && endExclusive.isAfter(dateFrom);
    }

    private java.util.Optional<TeacherAssignment> toAssignment(Assignment assignment,
                                                                 Semester semester) {
        java.util.Optional<Subject> subject = subjectRepository.findById(assignment.getSubjectId());
        java.util.Optional<Group> group = groupRepository.findById(assignment.getGroupId());
        if (subject.isEmpty() || group.isEmpty()) {
            return java.util.Optional.empty();
        }
        String endExclusive = (assignment.getValidUntilExclusive() != null
                ? assignment.getValidUntilExclusive()
                : semester.getDateTo().plusDays(1)).toString();
        return java.util.Optional.of(TeacherAssignment.newBuilder()
                .setAssignmentId(assignment.getId())
                .setTeacherId(assignment.getTeacherId())
                .setGroupId(assignment.getGroupId())
                .setGroupName(group.get().getName())
                .setSubjectId(assignment.getSubjectId())
                .setSubjectName(subject.get().getName())
                .setSemesterId(assignment.getSemesterId())
                .setLessonType(assignment.getLessonType().name().toLowerCase(java.util.Locale.ROOT))
                .setValidFrom(assignment.getValidFrom().toString())
                .setValidUntilExclusive(endExclusive)
                .build());
    }
}
