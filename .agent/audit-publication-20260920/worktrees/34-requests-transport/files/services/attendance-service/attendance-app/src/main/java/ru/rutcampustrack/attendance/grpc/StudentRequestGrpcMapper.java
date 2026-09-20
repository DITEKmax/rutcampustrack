package ru.rutcampustrack.attendance.grpc;

import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus;
import ru.rutcampustrack.attendance.studentrequest.AttachmentState;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;

/** Domain-to-proto projection. No identity/peer fields are exposed. */
final class StudentRequestGrpcMapper {
    private StudentRequestGrpcMapper() {
    }

    static StudentRequestDetail detail(StudentRequestModels.RequestDetail detail) {
        StudentRequestDetail.Builder result = StudentRequestDetail.newBuilder()
                .setSummary(summary(detail.summary()));
        if (detail.reason() != null) result.setReason(reason(detail.reason()));
        if (detail.comment() != null) result.setComment(detail.comment());
        detail.attachments().forEach(item -> result.addAttachments(attachment(item)));
        if (detail.decision() != null) result.setDecision(decision(detail.decision()));
        return result.build();
    }

    static StudentRequestPage page(StudentRequestModels.RequestPage page) {
        StudentRequestPage.Builder result = StudentRequestPage.newBuilder()
                .setPage(page.page()).setSize(page.size())
                .setTotalElements(page.totalElements()).setTotalPages(page.totalPages());
        page.content().forEach(item -> result.addContent(summary(item)));
        return result.build();
    }

    static StudentRequestOptions options(StudentRequestModels.RequestOptions options) {
        StudentRequestOptions.Builder result = StudentRequestOptions.newBuilder()
                .setFiles(fileLimits(options.files()))
                .setBudget(budget(options.budget()));
        options.reasons().forEach(item -> result.addReasons(StudentRequestReasonOption.newBuilder()
                .setCode(reason(item.code())).setLabel(item.label()).build()));
        options.lessons().forEach(item -> {
            StudentRequestLessonOption.Builder lesson = StudentRequestLessonOption.newBuilder()
                    .setLesson(lesson(item.lesson()))
                    .setExcuseEligible(item.excuseEligible())
                    .setLateCheckinEligible(item.lateCheckinEligible());
            item.pendingRequests().forEach(ref -> lesson.addPendingRequests(StudentRequestPendingRef.newBuilder()
                    .setId(ref.id()).setKind(kind(ref.kind())).setOrigin(origin(ref.origin())).build()));
            result.addLessons(lesson.build());
        });
        return result.build();
    }

    static StudentRequestAttachmentDownload download(StudentRequestModels.AttachmentDownload download) {
        byte[] bytes = download.bytes() == null ? new byte[0] : download.bytes();
        return StudentRequestAttachmentDownload.newBuilder()
                .setData(com.google.protobuf.ByteString.copyFrom(bytes))
                .setContentType(value(download.contentType()))
                .setFilename(value(download.filename()))
                .setSize(bytes.length)
                .build();
    }

    static StudentRequestAttachmentUpload upload(com.google.protobuf.ByteString data,
                                                 String name, String contentType) {
        return StudentRequestAttachmentUpload.newBuilder().setData(data)
                .setName(value(name)).setDeclaredContentType(value(contentType)).build();
    }

    private static StudentRequestSummary summary(StudentRequestModels.RequestSummary summary) {
        StudentRequestSummary.Builder result = StudentRequestSummary.newBuilder()
                .setId(value(summary.id())).setKind(kind(summary.kind())).setStatus(status(summary.status()))
                .setOrigin(origin(summary.origin()));
        summary.lessons().forEach(item -> result.addLessons(lesson(item)));
        if (summary.createdAt() != null) result.setCreatedAt(summary.createdAt().toString());
        if (summary.updatedAt() != null) result.setUpdatedAt(summary.updatedAt().toString());
        return result.build();
    }

    private static StudentRequestLesson lesson(StudentRequestModels.LessonSnapshot lesson) {
        StudentRequestLesson.Builder result = StudentRequestLesson.newBuilder()
                .setId(lesson.lessonId()).setLessonNumber(lesson.lessonNumber())
                .setStatus(value(lesson.status())).setBlocked(lesson.blocked());
        if (lesson.subjectId() > 0) result.setSubjectId(lesson.subjectId());
        if (lesson.subjectName() != null) result.setSubjectName(lesson.subjectName());
        if (lesson.subjectType() != null) result.setSubjectType(lesson.subjectType());
        if (lesson.semesterId() > 0) result.setSemesterId(lesson.semesterId());
        if (lesson.date() != null) result.setDate(lesson.date().toString());
        if (lesson.startsAt() != null) result.setStartsAt(lesson.startsAt().toString());
        if (lesson.endsAt() != null) result.setEndsAt(lesson.endsAt().toString());
        return result.build();
    }

    private static StudentRequestDecision decision(StudentRequestModels.Decision decision) {
        StudentRequestDecision.Builder result = StudentRequestDecision.newBuilder();
        if (decision.comment() != null) result.setComment(decision.comment());
        if (decision.decidedAt() != null) result.setDecidedAt(decision.decidedAt().toString());
        return result.build();
    }

    private static StudentRequestAttachment attachment(StudentRequestModels.AttachmentDescriptor item) {
        StudentRequestAttachment.Builder result = StudentRequestAttachment.newBuilder()
                .setId(value(item.id())).setName(value(item.name())).setContentType(value(item.contentType()))
                .setSizeBytes(item.size()).setSha256(value(item.sha256()))
                .setState(item.state() == AttachmentState.EXPIRED
                        ? StudentRequestAttachmentState.STUDENT_REQUEST_ATTACHMENT_STATE_EXPIRED
                        : StudentRequestAttachmentState.STUDENT_REQUEST_ATTACHMENT_STATE_ACTIVE);
        if (item.uploadedAt() != null) result.setUploadedAt(item.uploadedAt().toString());
        if (item.expiresAt() != null) result.setExpiresAt(item.expiresAt().toString());
        if (item.expiredAt() != null) result.setExpiredAt(item.expiredAt().toString());
        return result.build();
    }

    private static StudentRequestFileLimits fileLimits(StudentRequestModels.FileLimits item) {
        return StudentRequestFileLimits.newBuilder().setMaxFiles(item.maxFiles())
                .setMaxBytesPerFile(item.maxBytesPerFile()).setMaxBytesTotal(item.maxBytesTotal())
                .addAllContentTypes(item.contentTypes()).addAllExtensions(item.extensions()).build();
    }

    private static StudentRequestBudget budget(StudentRequestModels.Budget item) {
        return StudentRequestBudget.newBuilder().setSemesterId(item.semesterId()).setLimit(item.limit())
                .setUsed(item.used()).setRemaining(item.remaining()).build();
    }

    private static ru.rutcampustrack.attendance.grpc.StudentRequestKind kind(
            ru.rutcampustrack.attendance.contract.enums.StudentRequestKind kind) {
        if (kind == null) return ru.rutcampustrack.attendance.grpc.StudentRequestKind.STUDENT_REQUEST_KIND_UNSPECIFIED;
        return ru.rutcampustrack.attendance.grpc.StudentRequestKind.valueOf(
                "STUDENT_REQUEST_KIND_" + kind.name());
    }

    private static ru.rutcampustrack.attendance.grpc.StudentRequestStatus status(StudentRequestStatus status) {
        if (status == null) return ru.rutcampustrack.attendance.grpc.StudentRequestStatus.STUDENT_REQUEST_STATUS_UNSPECIFIED;
        return ru.rutcampustrack.attendance.grpc.StudentRequestStatus.valueOf(
                "STUDENT_REQUEST_STATUS_" + status.name());
    }

    private static ru.rutcampustrack.attendance.grpc.StudentRequestOrigin origin(StudentRequestOrigin origin) {
        if (origin == null) return ru.rutcampustrack.attendance.grpc.StudentRequestOrigin.STUDENT_REQUEST_ORIGIN_UNSPECIFIED;
        return ru.rutcampustrack.attendance.grpc.StudentRequestOrigin.valueOf(
                "STUDENT_REQUEST_ORIGIN_" + origin.name());
    }

    private static StudentExcuseReason reason(ExcuseType reason) {
        if (reason == null) return StudentExcuseReason.STUDENT_EXCUSE_REASON_UNSPECIFIED;
        return StudentExcuseReason.valueOf("STUDENT_EXCUSE_REASON_" + reason.name());
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
