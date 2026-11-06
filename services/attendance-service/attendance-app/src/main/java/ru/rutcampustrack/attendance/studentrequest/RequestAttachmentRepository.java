package ru.rutcampustrack.attendance.studentrequest;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;

public interface RequestAttachmentRepository
        extends MongoRepository<RequestAttachmentDocument, String> {

    List<RequestAttachmentDocument> findByRequestIdAndOwnerStudentIdOrderByPositionAsc(
            String requestId, Long ownerStudentId);

    /**
     * Mongo verifies that the binary exists and has the stored length, without returning bytes
     * to the application for a metadata-only roster read. Scope and descriptor are checked by
     * the manual attachment reader.
     */
    @Query(
            value = """
                    { '_id': { '$in': ?0 }, 'state': 'ACTIVE', 'expires_at': { '$gt': ?1 },
                      'size': { '$gt': 0, '$lte': ?2 },
                      '$expr': { '$eq': [ '$size', { '$binarySize': { '$cond': [
                        { '$eq': [ { '$type': '$data' }, 'binData' ] }, '$data', null
                      ] } } ] } }
                    """,
            fields = "{ 'data': 0 }")
    List<RequestAttachmentDocument> findReadableMetadata(
            List<String> ids, Instant now, long maxBytes);
}
