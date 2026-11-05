package ru.rutcampustrack.schedule.constructor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.schedule.contract.dto.item.SemesterMaterializationResponse;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;
import ru.rutcampustrack.schedule.recurring.RecurringScheduleItemWriter;
import ru.rutcampustrack.schedule.security.RequestContext;

/** Fresh public admission and explicit, persisted system admission; neither path calls Attendance in a SQL transaction. */
@Service
public class SemesterMaterializationCoordinator implements SemesterMaterializationOperations {
    private final JdbcTemplate jdbc;
    private final AcademicGrpcClient academic;
    private final RequestContext context;
    private final RecurringScheduleItemWriter writer;
    private final ScheduleSemesterArchiveWriteFence archive;
    private final TransactionTemplate transactions;
    private final ConstructorWriteAdmission admission;

    public SemesterMaterializationCoordinator(JdbcTemplate jdbc, AcademicGrpcClient academic,
            RequestContext context, RecurringScheduleItemWriter writer,
            ScheduleSemesterArchiveWriteFence archive, PlatformTransactionManager manager,ConstructorWriteAdmission admission) {
        this.jdbc=jdbc; this.academic=academic; this.context=context; this.writer=writer;
        this.archive=archive; this.transactions=new TransactionTemplate(manager);
        this.admission=admission;
    }

    public SemesterMaterializationResponse materializeExisting(long itemId, UUID key) {
        Long actor=context.getUserId();
        if (actor==null || actor<=0 || key==null) throw new AccessDeniedException("Нужна действующая учётная запись");
        var replay=jdbc.queryForList("SELECT operation_id,group_id,requested_item_id,request_kind FROM constructor_semester_materializations WHERE actor_id=? AND request_key=? AND source_kind='USER'", actor,key);
        if (!replay.isEmpty()) {
            var row=replay.getFirst(); requireHeadman(number(row.get("group_id")));
            if (number(row.get("requested_item_id"))!=itemId || !"BACKFILL".equals(row.get("request_kind")))
                throw ConstructorLifecycleException.conflict("MATERIALIZATION_KEY_CONFLICT");
            return receipt((UUID)row.get("operation_id"));
        }
        admission.requireEnabled();
        var item=item(itemId); requireHeadman(number(item.get("group_id")));
        return receipt(generate(itemId,key,actor,number(item.get("group_id")),number(item.get("semester_id"))));
    }

    public SemesterMaterializationResponse operation(UUID operationId) {
        var rows=jdbc.queryForList("SELECT group_id FROM constructor_semester_materializations WHERE operation_id=?",operationId);
        if (rows.isEmpty()) throw absent();
        requireHeadman(number(rows.getFirst().get("group_id")));
        return receipt(operationId);
    }

    /** Deployment-only explicit manifest admission. Not exposed by a controller or a generic sweep. */
    public void materializeSystemBackfill(UUID run, long semester, long group, List<Long> selected) {
        if (selected==null || selected.stream().anyMatch(id->id==null || id<=0))
            throw new IllegalArgumentException("Invalid bounded system materialization manifest");
        List<Long> ids=selected.stream().distinct().sorted().toList();
        if (run==null || group<=0 || semester<=0 || ids.isEmpty() || ids.size()>128
                || ids.size()!=selected.size() || ids.stream().anyMatch(id->id==null || id<=0))
            throw new IllegalArgumentException("Invalid bounded system materialization manifest");
        byte[] hash=ConstructorHashes.json(Map.of("group",group,"semester",semester,"items",ids));
        transactions.executeWithoutResult(status->{
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,835131))",Object.class,run.toString());
            var prior=jdbc.queryForList("SELECT scope_hash FROM constructor_system_materialization_runs WHERE operation_id=? FOR UPDATE",run);
            if (!prior.isEmpty()) {
                if (!java.security.MessageDigest.isEqual(hash,(byte[])prior.getFirst().get("scope_hash")))
                    throw ConstructorLifecycleException.conflict("SYSTEM_MATERIALIZATION_SCOPE_CHANGED");
                return;
            }
            admission.requireEnabled();
            for (long id:ids) {
                var row=item(id);
                if (!Boolean.TRUE.equals(row.get("is_active")) || number(row.get("group_id"))!=group || number(row.get("semester_id"))!=semester)
                    throw ConstructorLifecycleException.conflict("SYSTEM_MATERIALIZATION_SCOPE_CHANGED");
            }
            jdbc.update("INSERT INTO constructor_system_materialization_runs(operation_id,group_id,semester_id,item_ids,scope_hash) VALUES(?,?,?,?::bigint[],?)",
                    run,group,semester,array(ids),hash);
        });
        resumeSystemRun(run);
    }

    public void resumeSystemRuns() {
        for (UUID id:jdbc.queryForList("SELECT operation_id FROM constructor_system_materialization_runs WHERE state='PENDING' AND next_attempt_at<=NOW() ORDER BY created_at LIMIT 1",UUID.class))
            resumeSystemRun(id);
    }

    private void resumeSystemRun(UUID run) {
        var scope=jdbc.queryForMap("SELECT * FROM constructor_system_materialization_runs WHERE operation_id=?",run);
        if (!"PENDING".equals(scope.get("state"))) return;
        try {
            var ids=jdbc.queryForList("SELECT unnest(item_ids) FROM constructor_system_materialization_runs WHERE operation_id=?",Long.class,run);
            for (long item:ids) {
                UUID key=UUID.nameUUIDFromBytes(("SYSTEM_BACKFILL|"+run+"|"+item).getBytes(StandardCharsets.UTF_8));
                generate(item,key,null,number(scope.get("group_id")),number(scope.get("semester_id")));
            }
            jdbc.update("UPDATE constructor_system_materialization_runs SET state='COMPLETED',updated_at=NOW() WHERE operation_id=? AND state='PENDING'",run);
        } catch (RuntimeException unavailable) {
            jdbc.update("UPDATE constructor_system_materialization_runs SET attempts=attempts+1,next_attempt_at=NOW()+LEAST(3600,30*power(2,LEAST(attempts,7))) * interval '1 second',updated_at=NOW() WHERE operation_id=? AND state='PENDING'",run);
        }
    }

    private UUID generate(long id,UUID key,Long actor,long expectedGroup,long expectedSemester) {
        var row=item(id);
        if (!Boolean.TRUE.equals(row.get("is_active")) || number(row.get("group_id"))!=expectedGroup || number(row.get("semester_id"))!=expectedSemester)
            throw ConstructorLifecycleException.conflict("MATERIALIZATION_SCOPE_CHANGED");
        var group=academic.validateGroup(expectedGroup);
        var semester=academic.getActiveSemester();
        if (group==null || group.getId()!=expectedGroup || !group.getIsActive() || semester==null || semester.getId()!=expectedSemester)
            throw ConstructorLifecycleException.conflict("MATERIALIZATION_SCOPE_CHANGED");
        var assignments=academic.getAssignmentsByIds(List.of(number(row.get("assignment_id"))));
        if (assignments==null || assignments.size()!=1) throw ConstructorLifecycleException.conflict("MATERIALIZATION_AUTHORITY_UNKNOWN");
        var a=assignments.getFirst();
        if (a.getId()!=number(row.get("assignment_id")) || a.getGroupId()!=expectedGroup
                || a.getSubjectId()!=number(row.get("subject_id")) || a.getSemesterId()!=expectedSemester)
            throw ConstructorLifecycleException.conflict("MATERIALIZATION_AUTHORITY_UNKNOWN");
        var authority=new RecurringAssignmentAuthority(a.getId(),a.getTeacherId(),a.getSubjectId(),a.getGroupId(),a.getSemesterId(),
                a.getLessonType(),LocalDate.parse(a.getValidFrom()),LocalDate.parse(a.getValidUntilExclusive()));
        return writer.materializeExisting(id,key,actor,authority,LocalDate.parse(semester.getDateFrom()),
                LocalDate.parse(semester.getDateTo()),archive.prepareBusinessWrite(expectedSemester));
    }

    private void requireHeadman(long group) {
        if (context.getRole()==UserRole.ADMIN) return;
        Long actor=context.getUserId();
        if (!context.isHeadman() || actor==null || actor<=0 || !academic.isHeadman(actor,group))
            throw new AccessDeniedException("Нужно действующее право старосты своей группы");
    }

    private Map<String,Object> item(long id) {
        var rows=jdbc.queryForList("SELECT * FROM schedule_items WHERE id=?",id);
        if (rows.isEmpty()) throw absent(); return rows.getFirst();
    }

    SemesterMaterializationResponse receipt(UUID id) {
        var row=jdbc.queryForMap("SELECT * FROM constructor_semester_materializations WHERE operation_id=?",id);
        long created=jdbc.queryForObject("SELECT COUNT(*) FROM constructor_generation_origins WHERE operation_id=?",Long.class,id);
        var counts=jdbc.queryForMap("SELECT COALESCE(sum(lesson_count) FILTER(WHERE state='PENDING'),0) AS pending, COALESCE(sum(coverage_unavailable_count),0) AS unknown FROM constructor_materialization_work WHERE operation_id=?",id);
        return new SemesterMaterializationResponse(id,(String)row.get("state"),created,number(counts.get("pending")),number(counts.get("unknown")));
    }

    private static ConstructorLifecycleException absent() { return new ConstructorLifecycleException(404,"MATERIALIZATION_NOT_FOUND","Операция или шаблон не найдены"); }
    private static long number(Object value) { return ((Number)value).longValue(); }
    private static String array(List<Long> ids) { return "{"+ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+"}"; }
}
