package ru.rutcampustrack.attendance.report.planned;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The closed Academic read surface used by the existing report calculations and renderers. */
public interface ReportAcademicReadPort {
    SemesterResponse getActiveSemester();

    GroupResponse getGroup(Long groupId);

    GroupMembersResponse getGroupMembers(Long groupId);

    GroupMembersResponse getGroupMembers(Long groupId, LocalDate asOfDate, Long semesterId);

    Map<Long, AcademicGrpcClient.SubjectDetails> getSubjectDetailsByIds(List<Long> subjectIds);

    TeacherSubjectsResponse getCurrentTeacherSubjects(Long teacherId);

    boolean hasAssistantPermission(Long groupId, String permission);

    HeadmanStatsScopeResponse resolveHeadmanStatsScope(long groupId, Long semesterId);

    HeadmanStatsScopeResponse resolveHeadmanStatsScope(
            long groupId, Long semesterId, Long studentId);

    SemesterStateResponse getHeadmanJournalSemesterState(long groupId, long semesterId);

    HeadmanGroupCompositionResponse getHeadmanGroupComposition(long groupId);

    static ReportAcademicReadPort live(AcademicGrpcClient client) {
        if (client == null) return null;
        return new ReportAcademicReadPort() {
            @Override
            public SemesterResponse getActiveSemester() {
                return client.getActiveSemester();
            }

            @Override
            public GroupResponse getGroup(Long id) {
                return client.getGroup(id);
            }

            @Override
            public GroupMembersResponse getGroupMembers(Long id) {
                return client.getGroupMembers(id);
            }

            @Override
            public GroupMembersResponse getGroupMembers(Long id, LocalDate date, Long semester) {
                return client.getGroupMembers(id, date, semester);
            }

            @Override
            public Map<Long, AcademicGrpcClient.SubjectDetails> getSubjectDetailsByIds(
                    List<Long> ids) {
                return client.getSubjectDetailsByIds(ids);
            }

            @Override
            public TeacherSubjectsResponse getCurrentTeacherSubjects(Long id) {
                return client.getCurrentTeacherSubjects(id);
            }

            @Override
            public boolean hasAssistantPermission(Long id, String permission) {
                return client.hasAssistantPermission(id, permission);
            }

            @Override
            public HeadmanStatsScopeResponse resolveHeadmanStatsScope(long id, Long semester) {
                return client.resolveHeadmanStatsScope(id, semester);
            }

            @Override
            public HeadmanStatsScopeResponse resolveHeadmanStatsScope(
                    long id, Long semester, Long student) {
                return client.resolveHeadmanStatsScope(id, semester, student);
            }

            @Override
            public SemesterStateResponse getHeadmanJournalSemesterState(long id, long semester) {
                return client.getHeadmanJournalSemesterState(id, semester);
            }

            @Override
            public HeadmanGroupCompositionResponse getHeadmanGroupComposition(long id) {
                return client.getHeadmanGroupComposition(id);
            }
        };
    }
}
