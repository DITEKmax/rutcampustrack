package ru.rutcampustrack.attendance.checkin;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AttendanceRepository extends MongoRepository<AttendanceDocument, String> {

    /**
     * Look up an attendance document for a specific (lesson, student) pair.
     * Used by the excuse approve cascade (D-16) to implement upsert semantics
     * without resorting to a raw Mongo update.
     */
    Optional<AttendanceDocument> findByLessonIdAndUserId(Long lessonId, Long userId);
}
