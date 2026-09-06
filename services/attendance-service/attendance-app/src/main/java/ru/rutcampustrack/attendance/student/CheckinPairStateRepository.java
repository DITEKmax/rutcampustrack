package ru.rutcampustrack.attendance.student;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CheckinPairStateRepository extends MongoRepository<CheckinPairStateDocument, String> {
}
