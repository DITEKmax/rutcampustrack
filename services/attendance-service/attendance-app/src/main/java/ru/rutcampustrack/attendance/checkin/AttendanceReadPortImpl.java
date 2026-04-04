package ru.rutcampustrack.attendance.checkin;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementation of AttendanceReadPort using MongoTemplate (D-14).
 * Lives in checkin/ package — allowed to import AttendanceDocument.
 * The interface (AttendanceReadPort) has zero checkin imports — isolation maintained.
 */
@Component
public class AttendanceReadPortImpl implements AttendanceReadPort {

    private final MongoTemplate mongoTemplate;

    public AttendanceReadPortImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<AttendanceRecord> findByLessonId(Long lessonId) {
        Query query = new Query(Criteria.where("lesson_id").is(lessonId));
        return mongoTemplate.find(query, AttendanceDocument.class)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<AttendanceRecord> findByUserId(Long userId, Long semesterId) {
        Query query = new Query(
                Criteria.where("user_id").is(userId)
                        .and("semester_id").is(semesterId)
        );
        return mongoTemplate.find(query, AttendanceDocument.class)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<AttendanceRecord> findByGroupAndSubject(Long groupId, Long subjectId, LocalDate from, LocalDate to) {
        Query query = new Query(
                Criteria.where("group_id").is(groupId)
                        .and("subject_id").is(subjectId)
                        .and("lesson_date").gte(from).lte(to)
        );
        return mongoTemplate.find(query, AttendanceDocument.class)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    private AttendanceRecord toRecord(AttendanceDocument doc) {
        return new AttendanceRecord(
                doc.getLessonId(),
                doc.getUserId(),
                doc.getGroupId(),
                doc.getSubjectId(),
                doc.getLessonDate(),
                doc.getLessonNumber(),
                doc.getStatus(),
                doc.getSource()
        );
    }
}
