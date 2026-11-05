package ru.rutcampustrack.attendance.studentrequest;

import static org.assertj.core.api.Assertions.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.*;
import java.util.*;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.attendance.audit.LessonActionDocument;
import ru.rutcampustrack.attendance.contract.enums.*;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;

/** Real Mongo verifies exact causal joins, ordered metadata, and byte authorization. */
@Testcontainers
class StudentAttendanceAttachmentIT {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private MongoClient client;
    private MongoTemplate mongo;
    private StudentAttendanceAttachmentService service;
    private String request;

    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(client, "rct_attendance_files_" + UUID.randomUUID().toString().replace("-", ""));
        service = new StudentAttendanceAttachmentService(mongo, Clock.fixed(NOW, ZoneOffset.UTC));
        request = new ObjectId().toHexString();
        mongo.save(ExcuseTicket.builder().id(request).studentId(100L).groupId(10L)
                .semesterId(7L).lessonIds(List.of(11L)).status(ExcuseTicketStatus.APPROVED).build());
    }
    @AfterEach void close() { client.close(); }

    @Test void approvedRequestReturnsAllFilesInPositionOrderAndDownloadsOnlyOwnCurrentAssociation() {
        var second = file(request, 1, NOW.plusSeconds(60));
        var first = file(request, 0, NOW.plusSeconds(60));
        var mark = mark(request, "revision-1", null);
        assertThat(service.documents(7, 100, List.of(mark)).get(11L))
                .extracting(StudentAttendanceAttachmentService.DocumentRef::id)
                .containsExactly(first.getId(), second.getId());
        assertThat(service.download(7, 100, mark, second.getId()).bytes()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(service.documents(8, 100, List.of(mark)).getOrDefault(11L, List.of())).isEmpty();
        assertThat(service.documents(7, 101, List.of(mark))).isEmpty();
        assertThatThrownBy(() -> service.download(7, 101, mark, first.getId())).isInstanceOf(ResourceNotFoundException.class);
        ExcuseTicket ticket = mongo.findById(request, ExcuseTicket.class);
        ticket.setLessonIds(List.of(12L)); mongo.save(ticket);
        assertThatThrownBy(() -> service.download(7, 100, mark, first.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void legacyFallbackRequiresExactRevisionAndUnambiguousStructuredTicket() {
        var file = file(request, 0, NOW.plusSeconds(60));
        var mark = mark(null, "revision-1", null);
        assertThat(service.documents(7, 100, List.of(mark)).getOrDefault(11L, List.of())).isEmpty();
        audit(request, "revision-previous", 100, 10);
        audit(request, "revision-1", 101, 10);
        audit(request, "revision-1", 100, 20);
        assertThat(service.documents(7, 100, List.of(mark)).getOrDefault(11L, List.of())).isEmpty();
        audit(request, "revision-1", 100, 10);
        assertThat(service.download(7, 100, mark, file.getId()).bytes()).hasSize(3);
        audit(new ObjectId().toHexString(), "revision-1", 100, 10);
        assertThat(service.documents(7, 100, List.of(mark)).getOrDefault(11L, List.of())).isEmpty();
    }

    @Test void journalBindingChecksExactIdPairGroupSemesterAndLifecycle() {
        var file = file(PairWriteCoordinator.pairId(100, 11), 0, NOW.plusSeconds(60));
        var mark = mark(null, "journal-revision", file.getId());
        assertThat(service.download(7, 100, mark, file.getId()).bytes()).hasSize(3);
        file.setGroupId(20L); mongo.save(file);
        assertThatThrownBy(() -> service.download(7, 100, mark, file.getId())).isInstanceOf(ResourceNotFoundException.class);
        file.setGroupId(10L); file.setExpiresAt(NOW); mongo.save(file);
        assertThat(service.documents(7, 100, List.of(mark)).get(11L).getFirst().state()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> service.download(7, 100, mark, file.getId())).isInstanceOf(ResponseStatusException.class);
        file.setExpiresAt(NOW.plusSeconds(60)); file.setData(null); mongo.save(file);
        assertThatThrownBy(() -> service.download(7, 100, mark, file.getId())).isInstanceOf(ResponseStatusException.class);
        mongo.remove(file);
        assertThat(service.documents(7, 100, List.of(mark)).getOrDefault(11L, List.of())).isEmpty();
    }

    @Test void rejectedTicketAndUnboundSiblingNeverExposeFiles() {
        var file = file(request, 0, NOW.plusSeconds(60));
        var mark = mark(request, "revision-1", null);
        ExcuseTicket ticket = mongo.findById(request, ExcuseTicket.class);
        ticket.setStatus(ExcuseTicketStatus.REJECTED); mongo.save(ticket);
        assertThatThrownBy(() -> service.download(7, 100, mark, file.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    private RequestAttachmentDocument file(String binding, int position, Instant expiry) {
        return mongo.save(RequestAttachmentDocument.builder().id(new ObjectId().toHexString())
                .requestId(binding).ownerStudentId(100L).groupId(10L).semesterId(7L)
                .position(position).name("proof-" + position + ".pdf").contentType("application/pdf")
                .size(3L).state(AttachmentState.ACTIVE).expiresAt(expiry).data(new Binary(new byte[]{1, 2, 3})).build());
    }
    private static AttendanceRecord mark(String requestId, String revision, String attachmentId) {
        return new AttendanceRecord(11L, 100L, 10L, 501L, LocalDate.of(2026, 9, 15), 1,
                AttendanceStatus.EXCUSED, AttendanceSource.HEADMAN_EXCUSE, "reason", "OTHER", "comment",
                attachmentId, null, null, null, revision, requestId);
    }
    private void audit(String ticket, String revision, long user, long group) {
        LessonActionDocument action = new LessonActionDocument();
        action.setLessonId(11L); action.setGroupId(group); action.setSemesterId(7L);
        action.setPayload(Map.of("student", Map.of("user_id", user),
                "after", Map.of("mark_revision", revision, "status", "EXCUSED"),
                "ticket", Map.of("kind", "EXCUSE", "id", ticket, "status", "APPROVED")));
        mongo.save(action);
    }
}
