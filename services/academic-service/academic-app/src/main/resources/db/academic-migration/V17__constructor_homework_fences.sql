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

CREATE FUNCTION guard_constructor_homework_write() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE value JSONB; target_group BIGINT; target_subject BIGINT; target_semester BIGINT;
BEGIN
    FOR value IN SELECT v FROM unnest(ARRAY[
        CASE WHEN TG_OP<>'INSERT' THEN to_jsonb(OLD) END,
        CASE WHEN TG_OP<>'DELETE' THEN to_jsonb(NEW) END]) v WHERE v IS NOT NULL LOOP
        target_group:=NULLIF(value->>'group_id','')::bigint;
        target_subject:=NULLIF(value->>'subject_id','')::bigint;
        target_semester:=NULLIF(value->>'semester_id','')::bigint;
        IF target_group IS NOT NULL AND target_subject IS NOT NULL AND target_semester IS NOT NULL THEN
            PERFORM pg_advisory_xact_lock(1129270868,(target_subject%2147483647)::integer);
            IF EXISTS(SELECT 1 FROM academic_constructor_deletion_participants operation
                WHERE operation.group_id=target_group AND operation.subject_id=target_subject
                  AND operation.semester_id=target_semester AND operation.state IN('PREPARED','SEALED')
                  AND EXISTS(SELECT 1 FROM jsonb_array_elements(operation.lesson_scope) lesson
                      WHERE lesson->>'lesson_date'=value->>'lesson_date'
                        AND lesson->>'lesson_number'=value->>'lesson_number')) THEN
                RAISE EXCEPTION 'homework lesson is fenced for constructor deletion' USING ERRCODE='23514';
            END IF;
        END IF;
    END LOOP;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER a000_constructor_homework_fence BEFORE INSERT OR UPDATE OR DELETE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION guard_constructor_homework_write();
