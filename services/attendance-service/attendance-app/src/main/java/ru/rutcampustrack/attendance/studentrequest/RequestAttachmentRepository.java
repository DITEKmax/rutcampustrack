package ru.rutcampustrack.attendance.studentrequest;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;

public interface RequestAttachmentRepository
        extends MongoRepository<RequestAttachmentDocument, String> {

    List<RequestAttachmentDocument> findByRequestIdAndOwnerStudentIdOrderByPositionAsc(
            String requestId, Long ownerStudentId);
}
