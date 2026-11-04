-- Constructor deletion never deletes homework. The participant attests an empty
-- exact lesson scope and serializes it with every homework mutation.
CREATE TABLE academic_constructor_deletion_participants (
    operation_id UUID PRIMARY KEY,
    group_id BIGINT NOT NULL CHECK(group_id>0),
    subject_id BIGINT NOT NULL CHECK(subject_id>0),
    semester_id BIGINT NOT NULL CHECK(semester_id>0),
    request_hash BYTEA NOT NULL CHECK(octet_length(request_hash)=32),
    scope_hash BYTEA NOT NULL CHECK(octet_length(scope_hash)=32),
    participant_digest BYTEA NOT NULL CHECK(octet_length(participant_digest)=32),
    command BYTEA NOT NULL,
    lesson_scope JSONB NOT NULL CHECK(jsonb_typeof(lesson_scope)='array'),
    state VARCHAR(16) NOT NULL CHECK(state IN('PREPARED','SEALED','DELETED','RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE FUNCTION guard_academic_constructor_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'constructor participant receipt is retained'; END IF;
    IF (to_jsonb(NEW)-'state') IS DISTINCT FROM (to_jsonb(OLD)-'state')
       OR OLD.state IN('DELETED','RELEASED')
       OR NOT(NEW.state=OLD.state OR OLD.state='PREPARED' AND NEW.state IN('SEALED','RELEASED')
           OR OLD.state='SEALED' AND NEW.state IN('DELETED','RELEASED')) THEN
        RAISE EXCEPTION 'constructor participant identity or transition changed';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER academic_constructor_receipt_guard
    BEFORE UPDATE OR DELETE ON academic_constructor_deletion_participants
    FOR EACH ROW EXECUTE FUNCTION guard_academic_constructor_receipt();

-- Existing group/subject/semester guards run first on ordinary writes. This
-- advisory-only suffix never waits for a parent row while holding a new lock.
CREATE FUNCTION academic_constructor_lock_scope(semester_value bigint, subject_value bigint)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF semester_value IS NOT NULL THEN
        PERFORM pg_advisory_xact_lock(5452097,(semester_value%2147483647)::integer);
    END IF;
    IF subject_value IS NOT NULL THEN
        PERFORM pg_advisory_xact_lock(1129270868,(subject_value%2147483647)::integer);
    END IF;
END $$;

CREATE FUNCTION academic_constructor_ids(value jsonb)
RETURNS TABLE(kind text,id bigint) LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE field record; item jsonb; normalized text;
BEGIN
    IF jsonb_typeof(value)='array' THEN
        FOR item IN SELECT * FROM jsonb_array_elements(value) LOOP
            RETURN QUERY SELECT * FROM academic_constructor_ids(item);
        END LOOP;
    ELSIF jsonb_typeof(value)='object' THEN
        FOR field IN SELECT * FROM jsonb_each(value) LOOP
            normalized:=lower(replace(field.key,'_',''));
            IF jsonb_typeof(field.value)='number' AND field.value::text ~ '^[1-9][0-9]*$' THEN
                IF normalized=ANY(ARRAY['lessonid','sourcelessonid','targetlessonid','originallessonid','snapshotlessonid','placementlessonid','currentlessonid']) THEN
                    RETURN QUERY SELECT 'lesson'::text,(field.value::text)::bigint;
                ELSIF normalized=ANY(ARRAY['occurrenceid','targetoccurrenceid','sourceoccurrenceid','originaloccurrenceid','snapshotoccurrenceid','placementoccurrenceid','selectedoccurrenceid','scheduleoccurrenceid']) THEN
                    RETURN QUERY SELECT 'occurrence'::text,(field.value::text)::bigint;
                END IF;
            END IF;
            RETURN QUERY SELECT * FROM academic_constructor_ids(field.value);
        END LOOP;
    END IF;
END $$;

CREATE FUNCTION academic_constructor_lock_lessons(value jsonb)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE resource record;
BEGIN
    FOR resource IN SELECT DISTINCT kind,id FROM academic_constructor_ids(value) ORDER BY kind,id LOOP
        PERFORM pg_advisory_xact_lock(CASE WHEN resource.kind='lesson' THEN 1129270869 ELSE 1129270870 END,
            (resource.id%2147483647)::integer);
    END LOOP;
END $$;

CREATE FUNCTION academic_constructor_mentions(value jsonb, lesson jsonb, natural boolean)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE item jsonb; pair text[];
BEGIN
    IF EXISTS(SELECT 1 FROM academic_constructor_ids(value) value_id
        WHERE value_id.kind='lesson' AND value_id.id=(lesson->>'lesson_id')::bigint
           OR value_id.kind='occurrence' AND value_id.id=(lesson->>'occurrence_id')::bigint) THEN RETURN true; END IF;
    IF NOT natural THEN RETURN false; END IF;
    IF jsonb_typeof(value)='array' THEN
        FOR item IN SELECT * FROM jsonb_array_elements(value) LOOP
            IF academic_constructor_mentions(item,lesson,true) THEN RETURN true; END IF;
        END LOOP;
    ELSIF jsonb_typeof(value)='object' THEN
        FOREACH pair SLICE 1 IN ARRAY ARRAY[['lesson_date','lesson_number'],['lessonDate','lessonNumber'],
                ['source_date','source_lesson_number'],['target_date','target_lesson_number'],['date','lessonNumber']] LOOP
            IF value->>pair[1]=lesson->>'lesson_date'
                AND (value->>pair[2] IS NULL OR value->>pair[2]=lesson->>'lesson_number') THEN RETURN true; END IF;
        END LOOP;
        FOR item IN SELECT v FROM jsonb_each(value) e(k,v) LOOP
            IF academic_constructor_mentions(item,lesson,true) THEN RETURN true; END IF;
        END LOOP;
    END IF;
    RETURN false;
END $$;

CREATE FUNCTION academic_constructor_has_locator(value jsonb)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE item jsonb;
BEGIN
    IF EXISTS(SELECT 1 FROM academic_constructor_ids(value)) THEN RETURN true; END IF;
    IF jsonb_typeof(value)='object' THEN
        IF COALESCE(value->>'lesson_date',value->>'lessonDate',value->>'source_date',value->>'target_date') IS NOT NULL THEN RETURN true; END IF;
        FOR item IN SELECT v FROM jsonb_each(value) e(k,v) LOOP
            IF academic_constructor_has_locator(item) THEN RETURN true; END IF;
        END LOOP;
    ELSIF jsonb_typeof(value)='array' THEN
        FOR item IN SELECT * FROM jsonb_array_elements(value) LOOP
            IF academic_constructor_has_locator(item) THEN RETURN true; END IF;
        END LOOP;
    END IF;
    RETURN false;
END $$;

-- Join retained children to their immutable homework scope. Orphan binding
-- evidence is included only through a proved subject/binding or exact global
-- occurrence ID; no unrelated group's history is attributed by guess.
CREATE FUNCTION academic_constructor_history_rows(g bigint,s bigint,sem bigint,lessons jsonb)
RETURNS TABLE(kind text,data jsonb,unknown_scope boolean) LANGUAGE sql STABLE AS $$
    SELECT 'homeworks',to_jsonb(h),h.lesson_date IS NULL FROM homeworks h WHERE h.group_id=g AND h.subject_id=s AND h.semester_id=sem
    UNION ALL SELECT 'homework_edit_history',jsonb_build_object('record',to_jsonb(e),'homework',to_jsonb(h)),NOT academic_constructor_has_locator(e.before_snapshot) OR NOT academic_constructor_has_locator(e.after_snapshot)
        FROM homework_edit_history e JOIN homeworks h ON h.id=e.homework_id WHERE h.group_id=g AND h.subject_id=s AND h.semester_id=sem
    UNION ALL SELECT 'homework_edit_receipts',jsonb_build_object('record',to_jsonb(e),'homework',to_jsonb(h)),NOT academic_constructor_has_locator(e.result_snapshot)
        FROM homework_edit_receipts e JOIN homeworks h ON h.id=e.homework_id WHERE h.group_id=g AND h.subject_id=s AND h.semester_id=sem
    UNION ALL SELECT 'homework_edit_operations',to_jsonb(e),NOT academic_constructor_has_locator(e.before_snapshot) OR NOT academic_constructor_has_locator(e.desired_snapshot) FROM homework_edit_operations e WHERE e.group_id=g AND e.subject_id=s AND e.semester_id=sem
    UNION ALL SELECT 'homework_edit_archive_admissions',jsonb_build_object('record',to_jsonb(a),'edit',to_jsonb(e)),false
        FROM homework_edit_archive_admissions a JOIN homework_edit_operations e ON e.operation_id=a.edit_operation_id WHERE e.group_id=g AND e.subject_id=s AND e.semester_id=sem
    UNION ALL SELECT 'homework_binding_transfer_markers',to_jsonb(e),false FROM homework_binding_transfer_markers e WHERE e.group_id=g AND e.subject_id=s AND e.semester_id=sem
    UNION ALL SELECT 'homework_binding_transfer_history',to_jsonb(e),false FROM homework_binding_transfer_history e WHERE e.group_id=g AND e.subject_id=s AND e.semester_id=sem
    UNION ALL SELECT 'academic_semester_archive_publication_admissions',jsonb_build_object('record',to_jsonb(a),'homework',to_jsonb(h),'transfer',to_jsonb(t)),false
        FROM academic_semester_archive_publication_admissions a LEFT JOIN homeworks h ON h.binding_id=a.binding_id
        LEFT JOIN homework_binding_transfer_markers t ON t.binding_id=a.binding_id
        WHERE (a.semester_id=sem OR a.semester_id IS NULL) AND ((h.group_id=g AND h.subject_id=s) OR (t.group_id=g AND t.subject_id=s)
            OR EXISTS(SELECT 1 FROM jsonb_array_elements(lessons) l WHERE (l->>'occurrence_id')::bigint=a.schedule_occurrence_id))
    UNION ALL SELECT 'homework_binding_archives',jsonb_build_object('record',to_jsonb(a),'homework',to_jsonb(h),'transfer',to_jsonb(t),'admission',to_jsonb(p)),false
        FROM homework_binding_archives a LEFT JOIN homeworks h ON h.binding_id=a.binding_id
        LEFT JOIN homework_binding_transfer_markers t ON t.binding_id=a.binding_id
        LEFT JOIN academic_semester_archive_publication_admissions p ON p.binding_id=a.binding_id AND p.semester_id=sem
        WHERE (a.semester_id=sem OR a.semester_id IS NULL) AND ((h.group_id=g AND h.subject_id=s) OR (t.group_id=g AND t.subject_id=s)
            OR EXISTS(SELECT 1 FROM jsonb_array_elements(lessons) l WHERE (l->>'occurrence_id')::bigint=p.schedule_occurrence_id))
    UNION ALL SELECT 'lesson_transfer_receipts',to_jsonb(r),false FROM lesson_transfer_receipts r
        WHERE EXISTS(SELECT 1 FROM jsonb_array_elements(lessons) l WHERE academic_constructor_mentions(to_jsonb(r),l,false))
$$;

CREATE FUNCTION guard_constructor_homework_write() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE value jsonb; homework jsonb; edit jsonb; target_group bigint; target_subject bigint; target_semester bigint;
BEGIN
    FOR value IN SELECT v FROM unnest(ARRAY[CASE WHEN TG_OP<>'INSERT' THEN to_jsonb(OLD) END,
        CASE WHEN TG_OP<>'DELETE' THEN to_jsonb(NEW) END]) v WHERE v IS NOT NULL LOOP
        homework:=NULL; edit:=NULL;
        IF value->>'homework_id' IS NOT NULL THEN
            SELECT to_jsonb(h) INTO homework FROM homeworks h WHERE h.id=(value->>'homework_id')::bigint;
        ELSIF TG_TABLE_NAME IN('homework_binding_archives','academic_semester_archive_publication_admissions','academic_semester_archive_effect_receipts') THEN
            SELECT to_jsonb(h) INTO homework FROM homeworks h WHERE h.binding_id=(value->>'binding_id')::bigint;
        END IF;
        IF value->>'edit_operation_id' IS NOT NULL THEN
            SELECT to_jsonb(e) INTO edit FROM homework_edit_operations e WHERE e.operation_id=(value->>'edit_operation_id')::uuid;
        END IF;
        target_group:=COALESCE((value->>'group_id')::bigint,(homework->>'group_id')::bigint,(edit->>'group_id')::bigint);
        target_subject:=COALESCE((value->>'subject_id')::bigint,(homework->>'subject_id')::bigint,(edit->>'subject_id')::bigint);
        target_semester:=COALESCE((value->>'semester_id')::bigint,(homework->>'semester_id')::bigint,(edit->>'semester_id')::bigint);
        PERFORM academic_constructor_lock_scope(target_semester,target_subject);
        value:=jsonb_build_object('record',value,'homework',homework,'edit',edit);
        PERFORM academic_constructor_lock_lessons(value);
        IF EXISTS(SELECT 1 FROM academic_constructor_deletion_participants operation
            WHERE operation.state IN('PREPARED','SEALED','DELETED')
              AND EXISTS(SELECT 1 FROM jsonb_array_elements(operation.lesson_scope) lesson
                WHERE academic_constructor_mentions(value,lesson,false)
                   OR operation.state<>'DELETED' AND operation.group_id=target_group AND operation.subject_id=target_subject
                      AND operation.semester_id=target_semester AND (
                        academic_constructor_mentions(value,lesson,true)
                        OR TG_TABLE_NAME='homework_edit_history' AND (
                            NOT academic_constructor_has_locator(value->'record'->'before_snapshot')
                            OR NOT academic_constructor_has_locator(value->'record'->'after_snapshot'))
                        OR TG_TABLE_NAME='homework_edit_receipts' AND NOT academic_constructor_has_locator(value->'record'->'result_snapshot')
                        OR TG_TABLE_NAME='homework_edit_operations' AND (
                            NOT academic_constructor_has_locator(value->'record'->'before_snapshot')
                            OR NOT academic_constructor_has_locator(value->'record'->'desired_snapshot'))
                        OR TG_TABLE_NAME IN('homework_binding_archives','academic_semester_archive_effect_receipts','academic_semester_archive_publication_admissions')
                            AND NOT EXISTS(SELECT 1 FROM academic_constructor_ids(value))))) THEN
            RAISE EXCEPTION 'homework lesson is fenced for constructor deletion' USING ERRCODE='23514';
        END IF;
    END LOOP;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
DO $$ DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['homeworks','homework_completions','homework_edit_history','homework_edit_receipts',
        'homework_edit_operations','homework_edit_archive_admissions','homework_binding_archives','homework_binding_transfer_markers',
        'homework_binding_transfer_history','lesson_transfer_receipts','academic_semester_archive_publication_admissions','academic_semester_archive_effect_receipts'] LOOP
        EXECUTE format('CREATE TRIGGER zz_constructor_scope_fence BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION guard_constructor_homework_write()',table_name);
    END LOOP;
END $$;

-- Parent lifecycle admissions share the same advisory suffix. They either
-- commit before PREPARE (which then rejects them), or see the prepared fence.
CREATE FUNCTION guard_constructor_parent_transition() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE value jsonb; scope record; target_group bigint; target_subject bigint; target_semester bigint;
BEGIN
    IF TG_TABLE_NAME='semesters' AND TG_OP='UPDATE' AND (to_jsonb(OLD)-'name')=(to_jsonb(NEW)-'name') THEN RETURN NEW; END IF;
    value:=CASE WHEN TG_OP='DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
    IF TG_TABLE_NAME='groups' AND TG_OP='UPDATE' AND value->'is_active'=to_jsonb(OLD)->'is_active' THEN RETURN NEW; END IF;
    target_group:=CASE WHEN TG_TABLE_NAME='groups' THEN (value->>'id')::bigint ELSE (value->>'group_id')::bigint END;
    target_subject:=CASE WHEN TG_TABLE_NAME='subjects' THEN (value->>'id')::bigint ELSE (value->>'subject_id')::bigint END;
    target_semester:=CASE WHEN TG_TABLE_NAME='semesters' THEN (value->>'id')::bigint ELSE (value->>'semester_id')::bigint END;
    FOR scope IN SELECT id,semester_id FROM subjects WHERE (target_subject IS NOT NULL AND id=target_subject)
        OR (TG_TABLE_NAME IN('group_deletion_operations','academic_group_availability_guards','groups') AND group_id=target_group)
        OR (TG_TABLE_NAME='semesters' AND semester_id=target_semester) ORDER BY semester_id,id LOOP
        PERFORM academic_constructor_lock_scope(scope.semester_id,scope.id);
    END LOOP;
    IF EXISTS(SELECT 1 FROM academic_constructor_deletion_participants operation
        WHERE operation.state IN('PREPARED','SEALED') AND (
            operation.subject_id=target_subject OR TG_TABLE_NAME IN('group_deletion_operations','academic_group_availability_guards','groups') AND operation.group_id=target_group
            OR TG_TABLE_NAME='semesters' AND operation.semester_id=target_semester)) THEN
        RAISE EXCEPTION 'constructor participant blocks parent lifecycle transition' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER zz_constructor_parent_fence BEFORE DELETE ON subjects FOR EACH ROW EXECUTE FUNCTION guard_constructor_parent_transition();
CREATE TRIGGER zz_constructor_parent_fence BEFORE INSERT ON subject_deletion_operations FOR EACH ROW EXECUTE FUNCTION guard_constructor_parent_transition();
CREATE TRIGGER zz_constructor_parent_fence BEFORE INSERT ON group_deletion_operations FOR EACH ROW EXECUTE FUNCTION guard_constructor_parent_transition();
CREATE TRIGGER zz_constructor_parent_fence BEFORE UPDATE OR DELETE ON semesters FOR EACH ROW EXECUTE FUNCTION guard_constructor_parent_transition();
CREATE TRIGGER zz_constructor_parent_fence BEFORE INSERT ON academic_group_availability_guards FOR EACH ROW EXECUTE FUNCTION guard_constructor_parent_transition();
CREATE TRIGGER zz_constructor_parent_fence BEFORE UPDATE OR DELETE ON groups FOR EACH ROW EXECUTE FUNCTION guard_constructor_parent_transition();
