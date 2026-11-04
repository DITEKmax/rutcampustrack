package ru.rutcampustrack.schedule.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.google.protobuf.ByteString;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.constructor.grpc.PastLessonIdentity;
import ru.rutcampustrack.constructor.grpc.PastLessonMaterializationCommand;
import ru.rutcampustrack.constructor.grpc.PastLessonMaterializationReceipt;
import ru.rutcampustrack.constructor.grpc.PastLessonMaterializationResult;

class SemesterMaterializationReceiptTest {
    private final PastLessonMaterializationCommand command=PastLessonMaterializationCommand.newBuilder()
            .setOperationId(UUID.randomUUID().toString()).setBatchIndex(0).setBatchCount(1)
            .setBatchHash(ByteString.copyFrom(new byte[32]))
            .addLessons(PastLessonIdentity.newBuilder().setLessonId(11))
            .addLessons(PastLessonIdentity.newBuilder().setLessonId(12)).build();

    private PastLessonMaterializationReceipt.Builder receipt() {
        return PastLessonMaterializationReceipt.newBuilder().setOperationId(command.getOperationId())
                .setBatchIndex(0).setBatchHash(command.getBatchHash());
    }
    private static PastLessonMaterializationResult result(long id,String state) {
        return PastLessonMaterializationResult.newBuilder().setLessonId(id).setState(state).build();
    }
    @Test void coverageUnknownIsNotAnEmptyRosterOrZeroFailure() {
        assertThat(SemesterMaterializationRecoveryJob.validateReceipt(command,receipt()
                .addLessons(result(11,"NO_ELIGIBLE_MEMBERS")).addLessons(result(12,"COVERAGE_UNAVAILABLE")).build())).isEqualTo(1);
    }
    @Test void duplicateMissingForeignAndUnknownResultsCannotCompleteWork() {
        for (var value:java.util.List.of(
                receipt().addLessons(result(11,"APPLIED")).build(),
                receipt().addLessons(result(11,"APPLIED")).addLessons(result(11,"APPLIED")).build(),
                receipt().addLessons(result(11,"APPLIED")).addLessons(result(99,"APPLIED")).build(),
                receipt().addLessons(result(11,"APPLIED")).addLessons(result(12,"UNKNOWN")).build()))
            assertThatThrownBy(()->SemesterMaterializationRecoveryJob.validateReceipt(command,value)).isInstanceOf(IllegalStateException.class);
    }
    @Test void foreignOperationBatchAndHashCannotAcknowledgeAcceptedWork() {
        var valid=receipt().addLessons(result(11,"APPLIED")).addLessons(result(12,"TOMBSTONED")).build();
        assertThat(SemesterMaterializationRecoveryJob.validateReceipt(command,valid)).isZero();
        for (var value:java.util.List.of(valid.toBuilder().setOperationId(UUID.randomUUID().toString()).build(),
                valid.toBuilder().setBatchIndex(1).build(),valid.toBuilder().setBatchHash(ByteString.copyFromUtf8("wrong")).build()))
            assertThatThrownBy(()->SemesterMaterializationRecoveryJob.validateReceipt(command,value)).isInstanceOf(IllegalStateException.class);
    }
}
