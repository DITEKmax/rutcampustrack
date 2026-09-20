package ru.rutcampustrack.attendance.studentrequest;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;

import java.util.List;

public interface RequestAttachmentRepository extends MongoRepository<RequestAttachmentDocument, String> {

    List<RequestAttachmentDocument> findByRequestIdAndOwnerStudentIdOrderByPositionAsc(
            String requestId, Long ownerStudentId);
}
