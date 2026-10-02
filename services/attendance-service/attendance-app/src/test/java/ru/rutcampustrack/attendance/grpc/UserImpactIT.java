package ru.rutcampustrack.attendance.grpc;

import io.grpc.stub.StreamObserver;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers(disabledWithoutDocker=true)
@Import(AttendanceUserImpactGrpcServiceImpl.class)
class UserImpactIT {
    @Container static final MongoDBContainer MONGO=new MongoDBContainer("mongo:7.0");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",MONGO::getReplicaSetUrl);
    }
    @Autowired MongoTemplate mongo;
    @Autowired AttendanceUserImpactGrpcServiceImpl service;
    @Test void countsEveryRetainedTargetMarkWithoutReadingPrivateFieldsOrChangingRows() {
        List<Document> rows=List.of(
                new Document("_id","user-impact-current").append("user_id",88001L).append("lesson_id",87001L).append("semester_id",7001L).append("excuse_comment","private-sentinel"),
                new Document("_id","user-impact-historical").append("user_id",88001L).append("lesson_id",87002L).append("semester_id",7002L),
                new Document("_id","user-impact-foreign").append("user_id",88002L).append("lesson_id",87001L).append("semester_id",7001L));
        mongo.insert(rows,"attendances");
        try {
            List<Document> before=mongo.findAll(Document.class,"attendances");
            AtomicReference<UserImpactSnapshot> result=new AtomicReference<>();
            AtomicReference<Throwable> error=new AtomicReference<>();
            service.previewUserImpact(UserImpactRequest.newBuilder().setUserId(88001L).build(),new StreamObserver<>() {
                public void onNext(UserImpactSnapshot value) { result.set(value); }
                public void onError(Throwable failure) { error.set(failure); }
                public void onCompleted() { }
            });
            assertThat(error.get()).isNull();
            assertThat(result.get().getAttendanceMarksCount()).isEqualTo(2);
            assertThat(result.get().getObservedAtEpochMs()).isPositive();
            assertThat(result.get().getSnapshotDigest()).matches("[0-9a-f]{64}");
            assertThat(result.get().toString()).doesNotContain("private-sentinel");
            assertThat(mongo.findAll(Document.class,"attendances")).containsExactlyInAnyOrderElementsOf(before);
        } finally {
            mongo.remove(org.springframework.data.mongodb.core.query.Query.query(
                    org.springframework.data.mongodb.core.query.Criteria.where("_id").in(rows.stream().map(row->row.getString("_id")).toList())),"attendances");
        }
    }
}
