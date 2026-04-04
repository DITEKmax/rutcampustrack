package ru.rutcampustrack.attendance.checkin;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AttendanceRepository extends MongoRepository<AttendanceDocument, String> {
}
