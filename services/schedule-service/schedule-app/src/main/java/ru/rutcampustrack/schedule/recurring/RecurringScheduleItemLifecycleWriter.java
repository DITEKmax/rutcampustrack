package ru.rutcampustrack.schedule.recurring;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.dto.item.*;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonSlot;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.event.HomeworkBindingArchivedEvent;
import ru.rutcampustrack.schedule.event.LessonCancelledEvent;
import ru.rutcampustrack.schedule.event.LessonRestoredEvent;
import ru.rutcampustrack.schedule.event.ScheduleActionRecordedEvent;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.lesson.LessonTransferWriter;
import ru.rutcampustrack.schedule.week.AcademicWeekPolicy;

/** Local template writer. Remote authorities are obtained by the coordinator before entry. */
@Service
public class RecurringScheduleItemLifecycleWriter {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final RecurringScheduleItemWriter creation;
    private final LessonTransferWriter transfers;
    private final ScheduleSemesterArchiveWriteFence archiveFence;
    private final ApplicationEventPublisher events;
    private ru.rutcampustrack.schedule.lesson.RecurringLessonLifecycleWriter naturalLifecycle;

    @org.springframework.beans.factory.annotation.Autowired
    public void setNaturalLifecycle(ru.rutcampustrack.schedule.lesson.RecurringLessonLifecycleWriter writer) {
        this.naturalLifecycle=writer;
    }

    public RecurringScheduleItemLifecycleWriter(
            JdbcTemplate jdbc,
            Clock clock,
            ObjectMapper mapper,
            RecurringScheduleItemWriter creation,
            LessonTransferWriter transfers,
            ScheduleSemesterArchiveWriteFence archiveFence,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.mapper = mapper;
        this.creation = creation;
        this.transfers = transfers;
        this.archiveFence = archiveFence;
        this.events = events;
    }

    public ScheduleItemResponse createReplayResponse(long actor, UUID key, CreateScheduleItemRequest request) {
        List<String> snapshots =
                jdbc.query(
                        "SELECT response_snapshot::text FROM schedule_recurring_lifecycle_replay WHERE actor_id = ? AND request_key = ? AND action = 'REACTIVATE'",
                        (rs, row) -> rs.getString(1),
                        actor,
                        key);
        if (!snapshots.isEmpty()) {
            try {
                return mapper.readValue(snapshots.getFirst(), ScheduleItemResponse.class);
            } catch (Exception failure) {
                throw new IllegalStateException("Invalid recurring replay snapshot", failure);
            }
        }
        // The command has already passed current authorization and idempotency checks.
        // Reconstruct its accepted receipt from the immutable ledger, not the current slot.
        var accepted = jdbc.queryForList("""
                SELECT item.id, item.created_at, replay.generated_count, replay.generated_from, replay.generated_until
                  FROM schedule_recurring_create_replay replay JOIN schedule_items item ON item.id = replay.schedule_item_id
                 WHERE replay.actor_id = ? AND replay.request_key = ? AND replay.payload_hash = ?
                   AND item.placement_changed_at IS NOT NULL
                """, actor, key, RecurringScheduleItemWriter.payloadHash(request));
        if (accepted.isEmpty()) return null;
        var row = accepted.getFirst();
        var slot = LessonSlot.forNumber(request.lessonNumber());
        return new ScheduleItemResponse(number(row.get("id")), request.assignmentId(), request.groupId(),
                request.subjectId(), request.semesterId(), request.dayOfWeek(), request.lessonNumber(),
                request.startTime() == null ? slot.startTime() : request.startTime(),
                request.endTime() == null ? slot.endTime() : request.endTime(), request.weekType(), request.room(),
                true, timestamp(row.get("created_at")), number(row.get("generated_count")),
                row.get("generated_from") == null ? null : date(row.get("generated_from")),
                row.get("generated_until") == null ? null : date(row.get("generated_until")));
    }

    public List<Long> seriesItemIds(long itemId) {
        List<Long> ids =
                jdbc.query(
                        """
                WITH RECURSIVE edges(a, b) AS (
                    SELECT source_schedule_item_id, target_schedule_item_id
                      FROM schedule_assignment_replacement_templates
                    UNION SELECT target_schedule_item_id, source_schedule_item_id
                      FROM schedule_assignment_replacement_templates
                ), family(id) AS (
                    SELECT id FROM schedule_items WHERE id = ?
                    UNION SELECT edges.b FROM edges JOIN family ON edges.a = family.id
                ) SELECT id FROM family ORDER BY id
                """,
                        (rs, row) -> rs.getLong(1),
                        itemId);
        if (ids.isEmpty()) throw new ResourceNotFoundException("ScheduleItem", "id", itemId);
        return ids;
    }

    public List<Long> assignmentIds(long itemId) {
        return readItems(seriesItemIds(itemId)).stream()
                .map(i -> number(i.get("assignment_id")))
                .distinct()
                .sorted()
                .toList();
    }

    public ScheduleItemLifecyclePreviewResponse preview(
            long itemId,
            UpdateScheduleItemRequest request,
            boolean delete,
            Map<Long, RecurringAssignmentAuthority> authorities,
            LocalDate from,
            LocalDate to) {
        List<Map<String, Object>> items = readItems(seriesItemIds(itemId));
        validateUpdate(item(items, itemId), request, delete);
        List<Map<String, Object>> rows = readOccurrences(items, false);
        Plan plan = plan(items, rows, request, delete, false, authorities, from, to);
        if (plan.structural) checkTemplateCollision(items, request, false);
        return counts(previewRevision(items, rows, request, plan), plan);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ScheduleItemResponse mutate(
            long itemId,
            UpdateScheduleItemRequest request,
            boolean delete,
            String expectedRevision,
            UUID key,
            long actor,
            Map<Long, RecurringAssignmentAuthority> authorities,
            LocalDate from,
            LocalDate to,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {
        String action = delete ? "DELETE" : "UPDATE";
        byte[] hash = hash(action + "|" + itemId + "|" + json(request) + "|" + expectedRevision);
        try {
            requireKey(key, actor);
            archiveFence.lockPreparedBusinessWrite(prepared);
            lockKey(actor, key);
            ScheduleItemResponse replay = replay(actor, key, hash);
            if (replay != null) return replay;
            List<Map<String, Object>> items = lockSeries(itemId, authorities);
            Map<String, Object> requested = item(items, itemId);
            validateUpdate(requested, request, delete);
            if (!delete && !Boolean.TRUE.equals(requested.get("is_active"))) {
                throw new ConflictException(
                        "Удалённый слот нужно вернуть через добавление прежней серии");
            }
            List<Map<String, Object>> rows = readOccurrences(items, true);
            Plan plan = plan(items, rows, request, delete, false, authorities, from, to);
            if (!previewRevision(items, rows, request, plan).equals(expectedRevision)) {
                throw new ConflictException(
                        "Серия изменилась после предварительного расчёта; обнови расчёт");
            }
            UUID operation = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(clock);
            checkTemplateCollision(items, request, delete);
            for (Map<String, Object> current : items) {
                jdbc.update(
                        """
                        UPDATE schedule_items
                           SET day_of_week = ?, lesson_number = ?, start_time = ?, end_time = ?,
                               placement_changed_at = CASE WHEN ? THEN ? ELSE placement_changed_at END,
                               week_type = CAST(? AS week_type), room = ?, lifecycle_revision = lifecycle_revision + 1,
                               is_active = CASE WHEN ? THEN FALSE ELSE is_active END,
                               deactivated_at = CASE WHEN ? THEN COALESCE(deactivated_at, ?) ELSE deactivated_at END
                         WHERE id = ? AND lifecycle_revision = ?
                        """,
                        delete ? current.get("day_of_week") : request.dayOfWeek(),
                        delete ? current.get("lesson_number") : request.lessonNumber(),
                        delete ? current.get("start_time") : request.startTime(),
                        delete ? current.get("end_time") : request.endTime(),
                        plan.structural,
                        now,
                        delete ? current.get("week_type") : request.weekType().name().toLowerCase(),
                        delete ? current.get("room") : request.room(),
                        delete,
                        delete,
                        now,
                        number(current.get("id")),
                        number(current.get("lifecycle_revision")));
            }
            UUID materialization=creation.beginLifecycleMaterialization(itemId,actor,key,
                    authorities.get(number(requested.get("assignment_id"))),from,to);
            if (plan.structural) {
                jdbc.queryForObject(
                        "SELECT set_config('rutcampustrack.template_placement_operation_id', ?, true)",
                        String.class, operation.toString());
            }
            List<TransferLessonResponse> accepted = apply(plan, operation, actor, prepared, now);
            creation.finishLifecycleMaterialization(materialization);
            ScheduleItemResponse response = response(itemId);
            response.setTransfers(accepted);
            response.setLifecycleResult(
                    counts(
                            revision(
                                    readItems(seriesItemIds(itemId)),
                                    readOccurrences(items, false)),
                            plan));
            saveReplay(operation, actor, key, hash, itemId, action, response, now);
            return response;
        } catch (DataIntegrityViolationException conflict) {
            throw new ConflictException(
                    "Изменение серии конфликтует с занятым слотом или текущей историей");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecurringCreateResult reactivate(
            long itemId,
            CreateScheduleItemRequest request,
            UUID key,
            long actor,
            Map<Long, RecurringAssignmentAuthority> authorities,
            LocalDate from,
            LocalDate to,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {
        requireKey(key, actor);
        archiveFence.lockPreparedBusinessWrite(prepared);
        lockKey(actor, key);
        byte[] hash = RecurringScheduleItemWriter.payloadHash(request);
        RecurringCreateResult replay = creation.replayIfPresent(actor, key, hash);
        if (replay != null) return replay;
        request = request.withCanonicalTimes();
        try {
            List<Map<String, Object>> items = lockSeries(itemId, authorities);
            Map<String, Object> requested = item(items, itemId);
            if (Boolean.TRUE.equals(requested.get("is_active"))) {
                throw new ConflictException(
                        "Серия уже активна; для изменения нужен предварительный расчёт");
            }
            if (number(requested.get("assignment_id")) != request.assignmentId()
                    || !Objects.equals(creation.inactiveTemplateId(request), itemId)) {
                throw new ConflictException("Продолжение серии не совпадает с текущим назначением");
            }
            UpdateScheduleItemRequest change =
                    new UpdateScheduleItemRequest(
                            request.subjectId(),
                            request.dayOfWeek(),
                            request.lessonNumber(),
                            request.startTime(),
                            request.endTime(),
                            request.weekType(),
                            request.room());
            validateUpdate(requested, change, false);
            checkTemplateCollision(items, change, false);
            List<Map<String, Object>> rows = readOccurrences(items, true);
            Plan plan = plan(items, rows, change, false, true, authorities, from, to);
            UUID operation = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(clock);
            for (Map<String, Object> current : items) {
                boolean active =
                        number(current.get("id")) == itemId
                                || creationCap(number(current.get("assignment_id")))
                                        .isAfter(LocalDate.now(clock.withZone(MOSCOW)));
                jdbc.update(
                        """
                        UPDATE schedule_items SET is_active = ?, room = ?,
                            generation_not_before = ?, lifecycle_revision = lifecycle_revision + 1 WHERE id = ?
                        """,
                        active,
                        request.room(),
                        now,
                        number(current.get("id")));
            }
            UUID materialization=creation.beginLifecycleMaterialization(itemId,actor,key,
                    authorities.get(number(requested.get("assignment_id"))),from,to);
            List<TransferLessonResponse> accepted = apply(plan, operation, actor, prepared, now);
            creation.finishLifecycleMaterialization(materialization);
            List<LocalDate> generated = new ArrayList<>();
            plan.created.forEach(c -> generated.add(c.date));
            plan.restored.forEach(r -> generated.add(date(r.get("occurrence_date"))));
            generated.sort(Comparator.naturalOrder());
            LocalDate first = generated.isEmpty() ? null : generated.get(0);
            LocalDate last = generated.isEmpty() ? null : generated.get(generated.size() - 1);
            jdbc.update(
                    """
                    INSERT INTO schedule_recurring_create_replay
                        (actor_id, request_key, payload_hash, schedule_item_id, assignment_id,
                         generated_count, generated_from, generated_until, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    actor,
                    key,
                    hash,
                    itemId,
                    request.assignmentId(),
                    generated.size(),
                    first,
                    last,
                    now);
            ScheduleItemResponse response = response(itemId);
            response.setTransfers(accepted);
            response.setLifecycleResult(
                    counts(
                            revision(
                                    readItems(seriesItemIds(itemId)),
                                    readOccurrences(items, false)),
                            plan));
            saveReplay(operation, actor, key, hash, itemId, "REACTIVATE", response, now);
            return new RecurringCreateResult(
                    itemId, request.assignmentId(), generated.size(), first, last);
        } catch (DataIntegrityViolationException conflict) {
            throw new ConflictException("Продолжение серии конфликтует с текущим расписанием");
        }
    }

    private List<Map<String, Object>> lockSeries(
            long itemId, Map<Long, RecurringAssignmentAuthority> authorities) {
        List<Long> before = seriesItemIds(itemId);
        List<Long> assignments =
                readItems(before).stream()
                        .map(i -> number(i.get("assignment_id")))
                        .distinct()
                        .sorted()
                        .toList();
        if (!authorities.keySet().equals(new HashSet<>(assignments)))
            throw new ConflictException("Назначение серии изменилось; повтори запрос");
        for (long assignment : assignments)
            creation.lockOrInstallFence(authorities.get(assignment));
        for (long id : before)
            jdbc.queryForMap("SELECT id FROM schedule_items WHERE id = ? FOR UPDATE", id);
        if (!before.equals(seriesItemIds(itemId)))
            throw new ConflictException("Цепочка замен преподавателя изменилась");
        List<Map<String, Object>> items = readItems(before);
        for (Map<String, Object> current : items) {
            RecurringAssignmentAuthority authority =
                    authorities.get(number(current.get("assignment_id")));
            if (number(current.get("group_id")) != authority.groupId()
                    || number(current.get("subject_id")) != authority.subjectId()
                    || number(current.get("semester_id")) != authority.semesterId())
                throw new ConflictException("Шаблон не соответствует подтверждённому назначению");
        }
        Boolean pending =
                jdbc.queryForObject(
                        """
                SELECT EXISTS (SELECT 1 FROM schedule_assignment_replacement_operations operation
                  JOIN schedule_assignment_replacement_templates mapping ON mapping.operation_id = operation.operation_id
                 WHERE (mapping.source_schedule_item_id = ANY (?::bigint[])
                     OR mapping.target_schedule_item_id = ANY (?::bigint[])) AND operation.state <> 'COMMITTED')
                """,
                        Boolean.class,
                        array(before),
                        array(before));
        if (Boolean.TRUE.equals(pending))
            throw new ConflictException("Замена преподавателя ещё не завершена");
        return items;
    }

    private Plan plan(
            List<Map<String, Object>> items,
            List<Map<String, Object>> rows,
            UpdateScheduleItemRequest request,
            boolean delete,
            boolean reactivate,
            Map<Long, RecurringAssignmentAuthority> authorities,
            LocalDate from,
            LocalDate to) {
        Plan plan = new Plan();
        plan.structural = !delete && !reactivate && items.stream().anyMatch(i -> placementDiffers(i, request));
        boolean movedBefore = items.stream().anyMatch(i -> i.get("placement_changed_at") != null);
        LocalDateTime now = LocalDateTime.now(clock.withZone(MOSCOW));
        Map<Long, Set<LocalDate>> desired = new LinkedHashMap<>();
        for (Map<String, Object> current : items) {
            long id = number(current.get("id"));
            if (delete) {
                desired.put(id, Set.of());
                continue;
            }
            long assignment = number(current.get("assignment_id"));
            RecurringAssignmentAuthority authority = authorities.get(assignment);
            LocalDate lower =
                    reactivate || from.isAfter(now.toLocalDate()) ? from : now.toLocalDate();
            if (!reactivate && current.get("generation_not_before") != null) {
                LocalDate floor =
                        timestamp(current.get("generation_not_before"))
                                .atZoneSameInstant(MOSCOW)
                                .toLocalDate();
                if (floor.isAfter(lower)) lower = floor;
            }
            // Preview is read-only and may run before a fence has been installed.
            List<Map<String, Object>> fences =
                    jdbc.queryForList(
                            "SELECT creation_cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id = ?",
                            assignment);
            LocalDate cap =
                    fences.isEmpty()
                            ? authority.validUntilExclusive()
                            : date(fences.get(0).get("creation_cap_until_exclusive"));
            if (cap.isAfter(authority.validUntilExclusive())) cap = authority.validUntilExclusive();
            desired.put(
                    id,
                    new HashSet<>(
                            RecurringDateCalculator.compute(
                                    createRequest(current, request), authority, lower, to, cap)));
        }
        if (!delete && (plan.structural || movedBefore)) {
            plan.weekBased = true;
            planByWeek(items, rows, request, reactivate, authorities, now, desired, plan);
            return plan;
        }
        Set<LocalDate> covered = new HashSet<>();
        for (Map<String, Object> row : rows) {
            LocalDate day = date(row.get("occurrence_date"));
            covered.add(day);
            if (!day.atTime(time(row.get("start_time"))).isAfter(now)
                    || Boolean.TRUE.equals(row.get("manual_transfer"))) continue;
            Map<String, Object> target = targetForDate(items,desired,day,false,reactivate);
            if (row.get("assignment_id")==null && desired.get(number(row.get("schedule_item_id"))).contains(day))
                target=item(items,number(row.get("schedule_item_id")));
            String status = String.valueOf(row.get("status"));
            if ("planned".equals(status)) {
                if (Boolean.TRUE.equals(row.get("pending_transfer")))
                    throw new ConflictException("Предыдущий перенос пары ещё не завершён");
                if (target == null) plan.removed.add(row);
                else if (!Objects.equals(row.get("room_snapshot"), request.room()))
                    plan.updated.add(new ChangedDate(row, day, (short) number(row.get("lesson_number")),
                            time(row.get("start_time")), time(row.get("end_time")), request.room()));
            } else if ("cancelled".equals(status)
                    && target != null
                    && Boolean.TRUE.equals(row.get("template_cancelled"))) {
                Map<String, Object> restore = new LinkedHashMap<>(row);
                restore.put("target_item", target);
                plan.restored.add(restore);
            }
        }
        // A manual transfer retains its original physical date as an intentional exception.
        for (Map<String, Object> lesson :
                jdbc.queryForList(
                        "SELECT DISTINCT date FROM lessons WHERE schedule_item_id = ANY (?::bigint[])",
                        array(itemIds(items)))) {
            covered.add(date(lesson.get("date")));
        }
        if (!delete) {
                Set<LocalDate> allDesired=new java.util.TreeSet<>();desired.values().forEach(allDesired::addAll);
                for (LocalDate day : allDesired) {
                    Map<String,Object> current=targetForDate(items,desired,day,true,reactivate);
                    if (current==null) continue;
                    if ((!reactivate && !day.atTime(time(current.get("start_time"))).isAfter(now))
                            || covered.contains(day)) continue;
                    covered.add(day);
                    plan.created.add(
                            new NewDate(
                                    current,
                                    day,
                                    authorities.get(number(current.get("assignment_id"))),
                                    createRequest(current, request)));
                }
        }
        return plan;
    }

    /** Match the current generation to its new date in the same academic week. */
    private void planByWeek(
            List<Map<String, Object>> items, List<Map<String, Object>> rows,
            UpdateScheduleItemRequest request, boolean reactivate,
            Map<Long, RecurringAssignmentAuthority> authorities, LocalDateTime now,
            Map<Long, Set<LocalDate>> desired, Plan plan) {
        Map<LocalDate, NewDate> targets = new TreeMap<>();
        for (LocalDate day : desired.values().stream().flatMap(Set::stream).distinct().sorted().toList()) {
            if (!day.atTime(request.startTime()).isAfter(now)) continue;
            var target = targetForDate(items, desired, day, true, reactivate);
            if (target != null) targets.put(weekOf(day), new NewDate(target, day,
                    authorities.get(number(target.get("assignment_id"))), createRequest(target, request)));
        }
        Set<LocalDate> reservedWeeks = new HashSet<>();
        Set<LocalDate> retainedDates = new HashSet<>();
        Map<LocalDate, Map<String, Object>> future = new TreeMap<>();
        Map<LocalDate, Map<String, Object>> cancelled = new HashMap<>();
        for (var row : rows) {
            LocalDate day = date(row.get("occurrence_date"));
            LocalDate week = weekOf(day);
            String status = String.valueOf(row.get("status"));
            if (row.get("manual_source_date") != null) {
                reservedWeeks.add(weekOf(date(row.get("manual_source_date"))));
                retainedDates.add(day);
            } else if ("cancelled".equals(status) && Boolean.TRUE.equals(row.get("template_cancelled"))) {
                cancelled.put(day, row);
            } else if (!"planned".equals(status) || !day.atTime(time(row.get("start_time"))).isAfter(now)) {
                // A conducted, started or intentionally cancelled lesson still occupies its natural week.
                reservedWeeks.add(week);
                retainedDates.add(day);
            } else if (future.putIfAbsent(week, row) != null) {
                throw new ConflictException("На одной учебной неделе несколько пар этой серии");
            }
        }
        for (var entry : future.entrySet()) {
            var row = entry.getValue();
            LocalDate source = date(row.get("occurrence_date"));
            var target = targets.remove(entry.getKey());
            if (Boolean.TRUE.equals(row.get("pending_transfer"))) {
                throw new ConflictException("Предыдущий перенос пары ещё не завершён");
            }
            if (target == null) {
                if (plan.structural && (request.weekType() == WeekType.ALL
                        || request.weekType() == AcademicWeekPolicy.weekType(source))) {
                    throw new ConflictException("Для будущей пары нет нового слота в той же учебной неделе: "
                            + "время уже прошло или дата выходит за границы семестра");
                }
                if (plan.structural && Boolean.TRUE.equals(row.get("live_homework"))) {
                    throw new ConflictException("У будущей пары с домашним заданием нет нового слота в этой неделе");
                }
                plan.removed.add(row);
                continue;
            }
            if (reservedWeeks.contains(entry.getKey()) || retainedDates.contains(target.date)
                    || cancelled.containsKey(target.date) && !source.equals(target.date)) {
                throw new ConflictException("Новый слот пересекается с сохранённой парой этой серии");
            }
            boolean moving = !source.equals(target.date)
                    || number(row.get("lesson_number")) != request.lessonNumber()
                    || !time(row.get("start_time")).equals(request.startTime())
                    || !time(row.get("end_time")).equals(request.endTime());
            if (moving) {
                long originId = number(row.get("schedule_item_id"));
                var origin = item(items, originId);
                boolean admitted = originId == number(target.item.get("id"))
                        && Boolean.TRUE.equals(origin.get("is_active"));
                if (!admitted && plan.structural && row.get("assignment_id") != null
                        && !Boolean.TRUE.equals(origin.get("is_active"))
                        && desired.get(originId).contains(target.date)) {
                    admitted = Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT constructor_has_matching_active_replacement_successor(?, ?::bigint[])",
                            Boolean.class, originId, array(itemIds(items))));
                }
                if (!plan.structural || !admitted
                        || !retainsDatedAssignment(row, target.date)) {
                    throw new ConflictException("Новый слот не соответствует действующему назначению пары");
                }
                checkPhysicalCollision(target, number(row.get("current_lesson_id")));
                plan.updated.add(new ChangedDate(row, target.date, request.lessonNumber(),
                        request.startTime(), request.endTime(), request.room()));
            } else if (!Objects.equals(row.get("room_snapshot"), request.room())) {
                plan.updated.add(new ChangedDate(row, source, request.lessonNumber(),
                        request.startTime(), request.endTime(), request.room()));
            }
        }
        Set<LocalDate> coveredDates = creation.coveredLifecycleDates(itemIds(items));
        for (var entry : targets.entrySet()) {
            NewDate target = entry.getValue();
            if (reservedWeeks.contains(entry.getKey())) continue;
            if (retainedDates.contains(target.date)) {
                throw new ConflictException("Новый слот пересекается с сохранённой парой этой серии");
            }
            var cancelledOnDate = cancelled.get(target.date);
            if (cancelledOnDate != null) {
                if (plan.structural || number(cancelledOnDate.get("lesson_number")) != request.lessonNumber()
                        || !time(cancelledOnDate.get("start_time")).equals(request.startTime())
                        || !time(cancelledOnDate.get("end_time")).equals(request.endTime())) {
                    throw new ConflictException("Новый слот пересекается с отменённой парой этой серии");
                }
                var restore = new LinkedHashMap<>(cancelledOnDate);
                restore.put("target_item", target.item);
                plan.restored.add(restore);
            } else {
                if (coveredDates.contains(target.date)) {
                    throw new ConflictException("На новую дату уже сохранена история этой серии");
                }
                checkPhysicalCollision(target, 0);
                plan.created.add(target);
            }
        }
    }

    static LocalDate weekOf(LocalDate day) {
        return day.minusDays(day.getDayOfWeek().getValue() - 1L);
    }

    private boolean retainsDatedAssignment(Map<String, Object> row, LocalDate day) {
        return row.get("assignment_id") == null || Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (SELECT 1 FROM schedule_assignment_fences WHERE assignment_id = ?
                    AND ? >= valid_from AND ? < creation_cap_until_exclusive)
                """, Boolean.class, row.get("assignment_id"), day, day));
    }

    private void checkPhysicalCollision(NewDate target, long source) {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (SELECT 1 FROM lessons WHERE group_id = ? AND date = ? AND lesson_number = ?
                    AND status IN ('planned', 'active', 'closed') AND id <> ?)
                """, Boolean.class, target.request.groupId(), target.date, target.request.lessonNumber(), source))) {
            throw new ConflictException("Новый слот уже занят");
        }
    }

    private Map<String,Object> targetForDate(List<Map<String,Object>> items,Map<Long,Set<LocalDate>> desired,
            LocalDate day,boolean creating,boolean reactivate) {
        Map<String,Object> fallback=null;
        for (var candidate:items) {
            if (!desired.get(number(candidate.get("id"))).contains(day)
                    || creating && !reactivate && !Boolean.TRUE.equals(candidate.get("is_active"))) continue;
            fallback=candidate;
            var fence=jdbc.queryForMap("SELECT valid_from,creation_cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id=?",
                    number(candidate.get("assignment_id")));
            if (!day.isBefore(date(fence.get("valid_from"))) && day.isBefore(date(fence.get("creation_cap_until_exclusive"))))
                return candidate;
        }
        return fallback;
    }

    private List<TransferLessonResponse> apply(
            Plan plan,
            UUID operation,
            long actor,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared,
            OffsetDateTime now) {
        List<TransferLessonResponse> accepted = new ArrayList<>();
        for (Map<String, Object> row : plan.removed) cancel(row, operation, actor, now, plan.structural);
        for (ChangedDate change : plan.updated) {
            Map<String, Object> row = change.source;
            UUID key =
                    UUID.nameUUIDFromBytes(
                            (operation + ":" + row.get("id")).getBytes(StandardCharsets.UTF_8));
            accepted.add(
                    transfers.transferForTemplate(
                            number(row.get("current_lesson_id")),
                            actor,
                            new TransferLessonRequest(
                                    change.date,
                                    (int) change.number,
                                    change.start,
                                    change.end,
                                    change.room,
                                    String.valueOf(row.get("revision")),
                                    key),
                            operation,
                            prepared));
        }
        for (Map<String, Object> row : plan.restored) {
            if (row.get("assignment_id")==null) {
                if (naturalLifecycle==null) throw new ConflictException("Не подтверждено восстановление пары без назначения");
                long restored=naturalLifecycle.restore(number(row.get("current_lesson_id")),actor);
                @SuppressWarnings("unchecked") var observedTarget=(Map<String,Object>)row.get("target_item");
                // The template room has already changed in this transaction.
                var target=readItems(List.of(number(observedTarget.get("id")))).getFirst();
                if (!Objects.equals(row.get("room_snapshot"),target.get("room"))) {
                    String revision=String.valueOf(jdbc.queryForObject("SELECT revision FROM lesson_occurrences WHERE id=?",Long.class,row.get("id")));
                    accepted.add(transfers.transferForTemplate(restored,actor,new TransferLessonRequest(
                            date(row.get("occurrence_date")),(int)number(row.get("lesson_number")),
                            time(row.get("start_time")),time(row.get("end_time")),(String)target.get("room"),revision,
                            UUID.nameUUIDFromBytes((operation+":restore:"+row.get("id")).getBytes(StandardCharsets.UTF_8))),operation,prepared));
                }
            } else restore(row, operation, actor, now);
        }
        for (NewDate generated : plan.created) {
            if (plan.structural && !generated.date.atTime(generated.request.startTime())
                    .isAfter(LocalDateTime.now(clock.withZone(MOSCOW)))) {
                throw new ConflictException("Время нового слота уже наступило; обнови расчёт");
            }
            long itemId = number(generated.item.get("id"));
            long occurrence =
                    creation.insertOccurrence(itemId, generated.date, generated.authority, now);
            long lesson =
                    creation.insertPhysicalLesson(
                            itemId,
                            occurrence,
                            generated.date,
                            generated.request,
                            generated.authority,
                            LessonStatus.PLANNED,
                            now,
                            null);
            jdbc.update(
                    "UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                    lesson,
                    occurrence);
            jdbc.update(
                    """
                    INSERT INTO lesson_lifecycle_entries (occurrence_id, revision, action, lesson_id,
                        generation, actor_id, occurred_at, template_operation_id)
                    VALUES (?, 1, 'CREATED', ?, 1, ?, ?, ?)
                    """,
                    occurrence,
                    lesson,
                    actor,
                    now,
                    operation);
            ScheduleActionRecordedEvent.publish(
                    jdbc, events, "LESSON_CREATED", lesson, actor, null, now);
        }
        return accepted;
    }

    private void cancel(Map<String, Object> row, UUID operation, long actor, OffsetDateTime now,
            boolean protectHomework) {
        long occurrence = number(row.get("id")), lesson = number(row.get("current_lesson_id"));
        Map<String, Object> actionBefore = ScheduleActionRecordedEvent.snapshot(jdbc, lesson);
        List<Map<String, Object>> bindings =
                jdbc.queryForList(
                        """
                SELECT binding_id, actor_id, request_key, homework_id, revision, pending_edit_operation_id
                  FROM lesson_homework_bindings WHERE occurrence_id = ? AND current_lesson_id = ?
                   AND state IN ('PENDING', 'ACTIVE') ORDER BY binding_id FOR UPDATE
                """,
                        occurrence,
                        lesson);
        if (protectHomework && !bindings.isEmpty()) {
            throw new ConflictException("У будущей пары с домашним заданием нет нового слота в этой неделе");
        }
        if (protectHomework && !date(row.get("occurrence_date")).atTime(time(row.get("start_time")))
                .isAfter(LocalDateTime.now(clock.withZone(MOSCOW)))) {
            throw new ConflictException("Пара уже началась; обнови расчёт");
        }
        if (bindings.stream().anyMatch(b -> b.get("pending_edit_operation_id") != null)) {
            throw new ConflictException("Домашнее задание ожидает подтверждения изменения");
        }
        int lessonChanged =
                jdbc.update(
                        """
                UPDATE lessons SET status = 'cancelled'::lesson_status, cancel_reason = 'Слот убран из будущего расписания',
                    cancelled_by = ?, cancelled_at = ?, revision = revision + 1
                 WHERE id = ? AND revision = ? AND status = 'planned'
                """,
                        actor,
                        now,
                        lesson,
                        number(row.get("lesson_revision")));
        int occurrenceChanged =
                jdbc.update(
                        "UPDATE lesson_occurrences SET revision = revision + 1 WHERE id = ? AND revision = ?",
                        occurrence,
                        number(row.get("revision")));
        if (lessonChanged != 1 || occurrenceChanged != 1)
            throw new ConflictException("Пара изменилась во время изменения серии");
        lifecycle(row, "CANCELLED", null, operation, actor, now);
        ScheduleActionRecordedEvent.publish(
                jdbc, events, "LESSON_CANCELLED", lesson, actor, actionBefore, now);
        events.publishEvent(
                new LessonCancelledEvent(
                        this,
                        lesson,
                        number(row.get("group_id")),
                        number(row.get("subject_id")),
                        date(row.get("occurrence_date")),
                        time(row.get("start_time")),
                        time(row.get("end_time")),
                        (int) number(row.get("lesson_number")),
                        "Слот убран из будущего расписания",
                        actor,
                        now,
                        number(row.get("semester_id")),
                        (String) actionBefore.get("lesson_type"),
                        nullableNumber(actionBefore.get("assignment_id")),
                        nullableNumber(actionBefore.get("assigned_teacher_id")),
                        (String) actionBefore.get("room_snapshot")));
        for (Map<String, Object> binding : bindings) {
            int changed =
                    jdbc.update(
                            "UPDATE lesson_homework_bindings SET state = 'ARCHIVED', revision = revision + 1, updated_at = ? WHERE binding_id = ? AND revision = ?",
                            now,
                            number(binding.get("binding_id")),
                            number(binding.get("revision")));
            if (changed != 1)
                throw new ConflictException("Домашнее задание изменилось во время изменения серии");
            events.publishEvent(
                    new HomeworkBindingArchivedEvent(
                            this,
                            number(binding.get("binding_id")),
                            number(binding.get("actor_id")),
                            (UUID) binding.get("request_key"),
                            occurrence,
                            lesson,
                            binding.get("homework_id") == null
                                    ? null
                                    : number(binding.get("homework_id")),
                            number(binding.get("revision")) + 1,
                            number(row.get("semester_id"))));
        }
    }

    private void restore(Map<String, Object> row, UUID operation, long actor, OffsetDateTime now) {
        Map<String, Object> actionBefore =
                ScheduleActionRecordedEvent.snapshot(jdbc, number(row.get("current_lesson_id")));
        @SuppressWarnings("unchecked")
        Map<String, Object> observedTarget = (Map<String, Object>) row.get("target_item");
        // The template has already been updated; use its admitted new room/parity.
        Map<String, Object> target = readItems(List.of(number(observedTarget.get("id")))).get(0);
        long targetItem = number(target.get("id"));
        Map<String, Object> fence =
                jdbc.queryForMap(
                        "SELECT * FROM schedule_assignment_fences WHERE assignment_id = ?",
                        number(target.get("assignment_id")));
        long lesson =
                jdbc.queryForObject(
                        "SELECT nextval(pg_get_serial_sequence('lessons', 'id'))", Long.class);
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.template_restore_operation_id', ?, true)",
                String.class,
                operation.toString());
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.lesson_transfer_operation_id', '', true)",
                String.class);
        jdbc.update(
                """
                INSERT INTO lesson_template_restore_authorities (operation_id, occurrence_id, source_lesson_id, target_lesson_id,
                    expected_revision, expected_generation, expected_lesson_revision, target_schedule_item_id, target_assignment_id, target_teacher_id,
                    target_room, target_week_type, actor_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                operation,
                number(row.get("id")),
                number(row.get("current_lesson_id")),
                lesson,
                number(row.get("revision")),
                number(row.get("generation")),
                number(row.get("lesson_revision")),
                targetItem,
                number(target.get("assignment_id")),
                number(fence.get("assigned_teacher_id")),
                target.get("room"),
                target.get("week_type"),
                actor,
                now);
        int changed =
                jdbc.update(
                        """
                UPDATE lesson_occurrences SET schedule_item_id = ?, assignment_id = ?, assigned_teacher_id = ?,
                    current_lesson_id = ?, generation = generation + 1, revision = revision + 1 WHERE id = ? AND revision = ?
                """,
                        targetItem,
                        number(target.get("assignment_id")),
                        number(fence.get("assigned_teacher_id")),
                        lesson,
                        number(row.get("id")),
                        number(row.get("revision")));
        if (changed != 1) throw new ConflictException("Пара изменилась во время продолжения серии");
        jdbc.update(
                """
                INSERT INTO lessons (id, schedule_item_id, occurrence_id, assignment_id, group_id, subject_id, semester_id,
                    assigned_teacher_id, lesson_type, lesson_number, day_of_week, start_time, end_time, room_snapshot,
                    week_type_snapshot, generation, revision, date, status, is_geo_blocked,
                    is_blocked_by_headman, blocked_by_user_id, blocked_at, created_at)
                OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 'planned', ?, ?, ?, ?, ?)
                """,
                lesson,
                targetItem,
                number(row.get("id")),
                number(target.get("assignment_id")),
                number(row.get("group_id")),
                number(row.get("subject_id")),
                number(row.get("semester_id")),
                number(fence.get("assigned_teacher_id")),
                row.get("lesson_type"),
                target.get("lesson_number"),
                target.get("day_of_week"),
                target.get("start_time"),
                target.get("end_time"),
                target.get("room"),
                target.get("week_type"),
                number(row.get("generation")) + 1,
                date(row.get("occurrence_date")),
                row.get("is_geo_blocked"),
                row.get("is_blocked_by_headman"),
                row.get("blocked_by_user_id"),
                row.get("blocked_at"),
                now);
        lifecycle(row, "RESTORED", lesson, operation, actor, now);
        ScheduleActionRecordedEvent.publishReplacement(
                jdbc,
                events,
                "LESSON_RESTORED",
                number(row.get("current_lesson_id")),
                lesson,
                actor,
                actionBefore,
                now,
                operation);
        events.publishEvent(
                new LessonRestoredEvent(
                        this,
                        number(row.get("current_lesson_id")),
                        actor,
                        ScheduleActionRecordedEvent.snapshot(jdbc, lesson),
                        now));
    }

    private void lifecycle(
            Map<String, Object> row,
            String action,
            Long target,
            UUID operation,
            long actor,
            OffsetDateTime now) {
        jdbc.update(
                """
                INSERT INTO lesson_lifecycle_entries (occurrence_id, revision, action, lesson_id, target_lesson_id,
                    generation, actor_id, occurred_at, template_operation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                number(row.get("id")),
                number(row.get("revision")) + 1,
                action,
                number(row.get("current_lesson_id")),
                target,
                number(row.get("generation")) + (target == null ? 0 : 1),
                actor,
                now,
                operation);
    }

    private LocalDate creationCap(long assignment) {
        return date(
                jdbc.queryForMap(
                                "SELECT creation_cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id = ?",
                                assignment)
                        .get("creation_cap_until_exclusive"));
    }

    private List<Map<String, Object>> readItems(List<Long> ids) {
        return jdbc.queryForList(
                "SELECT *, week_type::text AS week_type FROM schedule_items WHERE id = ANY (?::bigint[]) ORDER BY id",
                array(ids));
    }

    private List<Map<String, Object>> readOccurrences(
            List<Map<String, Object>> items, boolean lock) {
        return jdbc.queryForList(
                """
                SELECT occurrence.*, lesson.start_time, lesson.end_time, lesson.lesson_number, lesson.room_snapshot,
                       lesson.status::text AS status, lesson.revision AS lesson_revision,
                       lesson.is_geo_blocked, lesson.is_blocked_by_headman, lesson.blocked_by_user_id, lesson.blocked_at,
                       EXISTS (SELECT 1 FROM lesson_transfer_operations operation WHERE operation.occurrence_id = occurrence.id
                           AND operation.template_operation_id IS NULL) AS manual_transfer,
                       (SELECT (operation.source_snapshot->>'date')::date FROM lesson_transfer_operations operation
                         WHERE operation.occurrence_id = occurrence.id AND operation.template_operation_id IS NULL
                         ORDER BY operation.source_generation LIMIT 1) AS manual_source_date,
                       EXISTS (SELECT 1 FROM lesson_transfer_operations operation WHERE operation.occurrence_id = occurrence.id
                           AND operation.state <> 'COMPLETED') AS pending_transfer,
                       EXISTS (SELECT 1 FROM lesson_homework_bindings binding
                         WHERE binding.occurrence_id = occurrence.id AND binding.state IN ('PENDING', 'ACTIVE'))
                         AS live_homework,
                       EXISTS (SELECT 1 FROM lesson_lifecycle_entries entry WHERE entry.occurrence_id = occurrence.id
                           AND entry.revision = occurrence.revision AND entry.action = 'CANCELLED'
                           AND entry.template_operation_id IS NOT NULL) AS template_cancelled
                  FROM lesson_occurrences occurrence JOIN lessons lesson ON lesson.id = occurrence.current_lesson_id
                 WHERE occurrence.schedule_item_id = ANY (?::bigint[]) ORDER BY occurrence.id
                """
                        + (lock ? " FOR UPDATE OF occurrence, lesson" : ""),
                array(itemIds(items)));
    }

    private String revision(List<Map<String, Object>> items, List<Map<String, Object>> rows) {
        List<Object> snapshot = new ArrayList<>();
        items.forEach(
                i ->
                        snapshot.add(
                                Arrays.asList(
                                        i.get("id"),
                                        i.get("assignment_id"),
                                        i.get("lifecycle_revision"),
                                        i.get("is_active"),
                                        i.get("week_type"),
                                        i.get("room"))));
        rows.forEach(
                r ->
                        snapshot.add(
                                Arrays.asList(
                                        r.get("id"),
                                        r.get("current_lesson_id"),
                                        r.get("revision"),
                                        r.get("generation"),
                                        r.get("status"),
                                        r.get("lesson_revision"),
                                        r.get("pending_transfer"))));
        return HexFormat.of().formatHex(hash(json(snapshot)));
    }

    private void validateUpdate(
            Map<String, Object> current, UpdateScheduleItemRequest request, boolean delete) {
        if (delete) return;
        if (request == null
                || request.subjectId() == null
                || request.dayOfWeek() == null
                || request.lessonNumber() == null
                || request.startTime() == null
                || request.endTime() == null
                || request.weekType() == null
                || number(current.get("subject_id")) != request.subjectId()
                || request.room() != null && request.room().length() > 64) {
            throw new ConflictException("Предмет серии нельзя изменить; проверь параметры нового слота");
        }
        if (placementDiffers(current, request)) {
            if (request.dayOfWeek() < 1 || request.dayOfWeek() > 7) {
                throw new ConflictException("Выбери день недели для нового слота");
            }
            LessonSlot.forNumber(request.lessonNumber()).validateTimes(request.startTime(), request.endTime());
        }
    }

    private static boolean placementDiffers(Map<String, Object> current, UpdateScheduleItemRequest request) {
        return number(current.get("day_of_week")) != request.dayOfWeek()
                || number(current.get("lesson_number")) != request.lessonNumber()
                || !time(current.get("start_time")).equals(request.startTime())
                || !time(current.get("end_time")).equals(request.endTime());
    }

    private String previewRevision(List<Map<String, Object>> items, List<Map<String, Object>> rows,
            UpdateScheduleItemRequest request, Plan plan) {
        String base = revision(items, rows);
        if (!plan.weekBased) return base;
        return HexFormat.of().formatHex(hash(json(Arrays.asList(
                base, request.subjectId(), request.dayOfWeek(), request.lessonNumber(), request.startTime(),
                request.endTime(), request.weekType(), request.room(),
                plan.updated.stream().map(c -> List.of(c.source.get("id"), c.date)).toList(),
                plan.removed.stream().map(r -> r.get("id")).toList(),
                plan.restored.stream().map(r -> r.get("id")).toList(),
                plan.created.stream().map(c -> List.of(c.item.get("id"), c.date)).toList()))));
    }

    private void checkTemplateCollision(
            List<Map<String, Object>> items, UpdateScheduleItemRequest request, boolean delete) {
        if (delete) return;
        Map<String, Object> first = items.get(0);
        Boolean occupied =
                jdbc.queryForObject(
                        """
                SELECT EXISTS (SELECT 1 FROM schedule_items WHERE group_id = ? AND semester_id = ?
                    AND day_of_week = ? AND lesson_number = ? AND is_active AND id <> ALL (?::bigint[])
                    AND (week_type = 'all' OR ? = 'all' OR week_type::text = ?))
                """,
                        Boolean.class,
                        number(first.get("group_id")),
                        number(first.get("semester_id")),
                        request.dayOfWeek(),
                        request.lessonNumber(),
                        array(itemIds(items)),
                        request.weekType().name().toLowerCase(),
                        request.weekType().name().toLowerCase());
        if (Boolean.TRUE.equals(occupied))
            throw new ConflictException("Эта чётность уже занята другим слотом");
    }

    private CreateScheduleItemRequest createRequest(
            Map<String, Object> current, UpdateScheduleItemRequest request) {
        return new CreateScheduleItemRequest(
                number(current.get("assignment_id")),
                number(current.get("group_id")),
                number(current.get("subject_id")),
                number(current.get("semester_id")),
                request.dayOfWeek(),
                request.lessonNumber(),
                request.startTime(),
                request.endTime(),
                request.weekType(),
                request.room());
    }

    private ScheduleItemResponse response(long itemId) {
        Map<String, Object> i = readItems(List.of(itemId)).get(0);
        return new ScheduleItemResponse(
                itemId,
                number(i.get("assignment_id")),
                number(i.get("group_id")),
                number(i.get("subject_id")),
                number(i.get("semester_id")),
                (short) number(i.get("day_of_week")),
                (short) number(i.get("lesson_number")),
                time(i.get("start_time")),
                time(i.get("end_time")),
                WeekType.valueOf(String.valueOf(i.get("week_type")).toUpperCase()),
                (String) i.get("room"),
                Boolean.TRUE.equals(i.get("is_active")),
                timestamp(i.get("created_at")),
                i.get("generated_count") == null ? null : number(i.get("generated_count")),
                i.get("generated_from") == null ? null : date(i.get("generated_from")),
                i.get("generated_until") == null ? null : date(i.get("generated_until")));
    }

    private void saveReplay(
            UUID operation,
            long actor,
            UUID key,
            byte[] hash,
            long itemId,
            String action,
            ScheduleItemResponse response,
            OffsetDateTime now) {
        jdbc.update(
                "INSERT INTO schedule_recurring_lifecycle_replay (operation_id, actor_id, request_key, payload_hash, schedule_item_id, action, response_snapshot, accepted_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                operation,
                actor,
                key,
                hash,
                itemId,
                action,
                json(response),
                now);
    }

    private ScheduleItemResponse replay(long actor, UUID key, byte[] hash) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT payload_hash, response_snapshot::text AS response_snapshot FROM schedule_recurring_lifecycle_replay WHERE actor_id = ? AND request_key = ?",
                        actor,
                        key);
        if (rows.isEmpty()) return null;
        if (!MessageDigest.isEqual(hash, (byte[]) rows.get(0).get("payload_hash")))
            throw new ConflictException(
                    "Idempotency-Key уже использован для другого изменения серии");
        try {
            return mapper.readValue(
                    String.valueOf(rows.get(0).get("response_snapshot")),
                    ScheduleItemResponse.class);
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid recurring replay snapshot", failure);
        }
    }

    private void lockKey(long actor, UUID key) {
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                Object.class,
                actor + ":recurring:" + key);
    }

    private static ScheduleItemLifecyclePreviewResponse counts(String revision, Plan plan) {
        return new ScheduleItemLifecyclePreviewResponse(
                revision,
                plan.updated.size(),
                plan.removed.size(),
                plan.restored.size(),
                plan.created.size());
    }

    private static void requireKey(UUID key, long actor) {
        if (key == null || actor <= 0) throw new ConflictException("Нужны actor и Idempotency-Key");
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String array(List<Long> ids) {
        return "{" + String.join(",", ids.stream().map(String::valueOf).toList()) + "}";
    }

    private static List<Long> itemIds(List<Map<String, Object>> items) {
        return items.stream().map(i -> number(i.get("id"))).toList();
    }

    private static Map<String, Object> item(List<Map<String, Object>> items, long id) {
        return items.stream().filter(i -> number(i.get("id")) == id).findFirst().orElseThrow();
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }

    private static OffsetDateTime timestamp(Object value) {
        return value instanceof java.sql.Timestamp t
                ? t.toInstant().atOffset(ZoneOffset.UTC)
                : (OffsetDateTime) value;
    }

    private static LocalTime time(Object value) {
        return value instanceof java.sql.Time t ? t.toLocalTime() : (LocalTime) value;
    }

    private static final class Plan {
        boolean structural;
        boolean weekBased;
        final List<ChangedDate> updated = new ArrayList<>();
        final List<Map<String, Object>> removed = new ArrayList<>(),
                restored = new ArrayList<>();
        final List<NewDate> created = new ArrayList<>();
    }

    private record ChangedDate(Map<String, Object> source, LocalDate date, short number,
            LocalTime start, LocalTime end, String room) {}

    private record NewDate(
            Map<String, Object> item,
            LocalDate date,
            RecurringAssignmentAuthority authority,
            CreateScheduleItemRequest request) {}
}
