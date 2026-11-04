package ru.rutcampustrack.schedule.constructor;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.constructor.grpc.PastLessonMaterializationCommand;
import ru.rutcampustrack.constructor.grpc.PastLessonMaterializationReceipt;

/** Exact committed batch retries; no row regeneration, stored JWT, or business event replay. */
@Component
public class SemesterMaterializationRecoveryJob {
    private static final Logger log=LoggerFactory.getLogger(SemesterMaterializationRecoveryJob.class);
    private final JdbcTemplate jdbc;
    private final ConstructorParticipantClients clients;
    private final SemesterMaterializationCoordinator coordinator;
    private final TransactionTemplate transactions;

    public SemesterMaterializationRecoveryJob(JdbcTemplate jdbc,ConstructorParticipantClients clients,
            SemesterMaterializationCoordinator coordinator,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.clients=clients;this.coordinator=coordinator;this.transactions=new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay=5000)
    public void recover() {
        coordinator.resumeSystemRuns();
        for (int i=0;i<8;i++) {
            var claimed=transactions.execute(status->{
                var rows=jdbc.queryForList("SELECT work.*,operation.group_id AS accepted_group,operation.semester_id AS accepted_semester FROM constructor_materialization_work work JOIN constructor_semester_materializations operation ON operation.operation_id=work.operation_id WHERE work.state='PENDING' AND operation.state='PENDING' AND next_attempt_at<=NOW() AND (lease_until IS NULL OR lease_until<NOW()) ORDER BY next_attempt_at,work.operation_id,batch_index FOR UPDATE OF work SKIP LOCKED LIMIT 1");
                if (rows.isEmpty()) return null;
                var row=rows.getFirst(); UUID lease=UUID.randomUUID();
                jdbc.update("UPDATE constructor_materialization_work SET lease_token=?,lease_until=NOW()+interval '45 seconds',attempts=attempts+1,updated_at=NOW() WHERE operation_id=? AND batch_index=?",lease,row.get("operation_id"),row.get("batch_index"));
                row.put("claimed_lease",lease);return row;
            });
            if (claimed==null) return;
            try {
                var command=PastLessonMaterializationCommand.parseFrom((byte[])claimed.get("command"));
                if (!command.getOperationId().equals(claimed.get("operation_id").toString())
                        || command.getBatchIndex()!=((Number)claimed.get("batch_index")).intValue()
                        || command.getGroupId()!=((Number)claimed.get("accepted_group")).longValue()
                        || command.getSemesterId()!=((Number)claimed.get("accepted_semester")).longValue()
                        || command.getBatchCount()<=command.getBatchIndex() || command.getLessonsCount()<1 || command.getLessonsCount()>128
                        || !MessageDigest.isEqual(ConstructorHashes.sha256(command.toBuilder().clearBatchHash().build().toByteArray()),command.getBatchHash().toByteArray())
                        || !MessageDigest.isEqual(command.getBatchHash().toByteArray(),(byte[])claimed.get("batch_hash")))
                    throw new IllegalStateException("Invalid retained materialization identity");
                var admittedIds=jdbc.queryForList("SELECT unnest(lesson_ids) FROM constructor_materialization_work WHERE operation_id=? AND batch_index=?",
                        Long.class,claimed.get("operation_id"),claimed.get("batch_index"));
                if (!admittedIds.equals(command.getLessonsList().stream().map(l->l.getLessonId()).toList()))
                    throw new IllegalStateException("Invalid retained materialization lesson set");
                var receipt=clients.materialize(command);
                int unavailable=validateReceipt(command,receipt);
                transactions.executeWithoutResult(status->settle(claimed,unavailable));
            } catch (Exception unavailable) {
                jdbc.update("UPDATE constructor_materialization_work SET lease_token=NULL,lease_until=NULL,next_attempt_at=NOW()+LEAST(3600,5*power(2,LEAST(attempts,10))) * interval '1 second',updated_at=NOW() WHERE operation_id=? AND batch_index=? AND lease_token=? AND state='PENDING'",
                        claimed.get("operation_id"),claimed.get("batch_index"),claimed.get("claimed_lease"));
                log.warn("Constructor materialization delivery deferred; retained batch will retry with bounded backoff");
            }
        }
    }

    static int validateReceipt(PastLessonMaterializationCommand command,PastLessonMaterializationReceipt receipt) {
        if (receipt==null || !command.getOperationId().equals(receipt.getOperationId())
                || command.getBatchIndex()!=receipt.getBatchIndex()
                || !MessageDigest.isEqual(command.getBatchHash().toByteArray(),receipt.getBatchHash().toByteArray())
                || command.getLessonsCount()!=receipt.getLessonsCount())
            throw new IllegalStateException("Materialization receipt identity mismatch");
        Set<Long> expected=new HashSet<>();command.getLessonsList().forEach(l->expected.add(l.getLessonId()));
        if (expected.size()!=command.getLessonsCount()) throw new IllegalStateException("Duplicate materialization lesson");
        int unavailable=0;
        for (var lesson:receipt.getLessonsList()) {
            if (!expected.remove(lesson.getLessonId()) || !Set.of("APPLIED","UNCHANGED","NO_ELIGIBLE_MEMBERS","COVERAGE_UNAVAILABLE","TOMBSTONED").contains(lesson.getState()))
                throw new IllegalStateException("Materialization result mismatch");
            if ("COVERAGE_UNAVAILABLE".equals(lesson.getState())) unavailable++;
        }
        return unavailable;
    }

    private void settle(Map<String,Object> batch,int unavailable) {
        jdbc.queryForMap("SELECT operation_id FROM constructor_semester_materializations WHERE operation_id=? FOR UPDATE",batch.get("operation_id"));
        if (jdbc.update("UPDATE constructor_materialization_work SET state=?,coverage_unavailable_count=?,lease_token=NULL,lease_until=NULL,updated_at=NOW() WHERE operation_id=? AND batch_index=? AND lease_token=? AND state='PENDING'",
                unavailable==0?"APPLIED":"COVERAGE_UNAVAILABLE",unavailable,batch.get("operation_id"),batch.get("batch_index"),batch.get("claimed_lease"))!=1) return;
        var counts=jdbc.queryForMap("SELECT count(*) FILTER(WHERE state='PENDING') AS pending,count(*) FILTER(WHERE state='COVERAGE_UNAVAILABLE') AS unknown FROM constructor_materialization_work WHERE operation_id=?",batch.get("operation_id"));
        if (((Number)counts.get("pending")).longValue()==0) jdbc.update("UPDATE constructor_semester_materializations SET state=? WHERE operation_id=? AND state='PENDING'",
                ((Number)counts.get("unknown")).longValue()==0?"COMPLETED":"COVERAGE_UNAVAILABLE",batch.get("operation_id"));
    }
}
