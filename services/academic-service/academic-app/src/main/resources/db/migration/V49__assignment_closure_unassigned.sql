-- Directed closure/adoption use the same durable protocol, with explicit absent identities.
ALTER TABLE assignment_replacement_operations
    ADD COLUMN operation_kind VARCHAR(24) NOT NULL DEFAULT 'REPLACE',
    ADD COLUMN requested_cutoff DATE,
    ALTER COLUMN source_assignment_id DROP NOT NULL,
    ALTER COLUMN target_assignment_id DROP NOT NULL,
    DROP CONSTRAINT assignment_replacement_operation_source_chk,
    DROP CONSTRAINT assignment_replacement_operation_target_chk,
    DROP CONSTRAINT assignment_replacement_operation_source_target_chk;

ALTER TABLE assignment_replacement_operations ADD CONSTRAINT assignment_lifecycle_tuple_chk CHECK (
    (operation_kind = 'REPLACE' AND source_assignment_id IS NOT NULL AND target_assignment_id IS NOT NULL
       AND source_assignment_id > 0 AND target_assignment_id > 0
       AND source_assignment_id <> target_assignment_id AND requested_cutoff IS NULL)
 OR (operation_kind = 'CLOSE_UNASSIGN' AND source_assignment_id IS NOT NULL AND source_assignment_id > 0 AND target_assignment_id IS NULL
       AND requested_cutoff IS NOT NULL AND source_valid_until IS NOT NULL)
 OR (operation_kind = 'ADOPT_UNASSIGNED' AND source_assignment_id IS NULL AND target_assignment_id IS NOT NULL AND target_assignment_id > 0
       AND requested_cutoff IS NOT NULL AND requested_cutoff = effective_from AND source_valid_until IS NOT NULL)
);
CREATE UNIQUE INDEX assignment_adoption_target_uq ON assignment_replacement_operations(target_assignment_id)
 WHERE operation_kind = 'ADOPT_UNASSIGNED';

ALTER TABLE assignments DROP CONSTRAINT assignments_lifecycle_state_chk,
 ADD CONSTRAINT assignments_lifecycle_state_chk CHECK (lifecycle_state IN ('ACTIVE', 'PREPARED', 'CLOSED'));
-- A terminal close before valid_from retains its positive historical interval,
-- but it no longer reserves that teacher's future admission interval.
ALTER TABLE assignments DROP CONSTRAINT assignments_no_same_teacher_overlap,
 ADD CONSTRAINT assignments_no_same_teacher_overlap EXCLUDE USING gist
 (teacher_id WITH =,subject_id WITH =,group_id WITH =,semester_id WITH =,
  lesson_type WITH =,daterange(valid_from,valid_until_exclusive,'[)') WITH &&)
 WHERE (lifecycle_state <> 'CLOSED');

CREATE FUNCTION protect_assignment_lifecycle_discriminator() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'UPDATE' AND (NEW.operation_kind IS DISTINCT FROM OLD.operation_kind
       OR NEW.requested_cutoff IS DISTINCT FROM OLD.requested_cutoff) THEN
   RAISE EXCEPTION 'assignment lifecycle discriminator is immutable';
 END IF;
 IF NEW.operation_kind = 'ADOPT_UNASSIGNED' AND NOT EXISTS (
   SELECT 1 FROM assignments target WHERE target.id = NEW.target_assignment_id
    AND target.valid_from = NEW.effective_from
    AND (target.valid_until_exclusive = NEW.target_valid_until OR target.valid_until_exclusive IS NULL)
    AND target.lifecycle_state IN ('PREPARED', 'ACTIVE')) THEN
   RAISE EXCEPTION 'adoption requires its exact admitted target';
 END IF;
 IF NEW.operation_kind = 'CLOSE_UNASSIGN' AND NOT EXISTS (
   SELECT 1 FROM assignments source WHERE source.id = NEW.source_assignment_id
    AND NEW.effective_from = GREATEST(NEW.requested_cutoff, source.valid_from)) THEN
   RAISE EXCEPTION 'closure requires its exact clamped source date';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER assignment_lifecycle_discriminator_guard BEFORE INSERT OR UPDATE
 ON assignment_replacement_operations FOR EACH ROW EXECUTE FUNCTION protect_assignment_lifecycle_discriminator();

-- Preserve final-semester deletion authority and immutable assignment identity.
CREATE OR REPLACE FUNCTION protect_assignment_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation assignment_replacement_operations%ROWTYPE;
        operation_id_value UUID;
BEGIN
 IF TG_OP = 'DELETE' THEN
   IF academic_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
   RAISE EXCEPTION 'assignments are closed, never deleted';
 END IF;
 IF NEW.id IS DISTINCT FROM OLD.id OR NEW.teacher_id IS DISTINCT FROM OLD.teacher_id
   OR NEW.subject_id IS DISTINCT FROM OLD.subject_id OR NEW.group_id IS DISTINCT FROM OLD.group_id
   OR NEW.semester_id IS DISTINCT FROM OLD.semester_id OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
   OR NEW.valid_from IS DISTINCT FROM OLD.valid_from OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
   RAISE EXCEPTION 'assignment identity is immutable';
 END IF;
 IF OLD.valid_until_exclusive IS NOT NULL AND (NEW.valid_until_exclusive IS NULL
       OR NEW.valid_until_exclusive > OLD.valid_until_exclusive) THEN
   RAISE EXCEPTION 'closed assignment validity cannot be extended or reopened';
 END IF;
 IF NEW.valid_until_exclusive IS NOT NULL AND NEW.valid_until_exclusive <= NEW.valid_from THEN
   RAISE EXCEPTION 'assignment requires a positive retained interval';
 END IF;
 IF NEW.valid_until_exclusive IS DISTINCT FROM OLD.valid_until_exclusive
       OR NEW.lifecycle_state IS DISTINCT FROM OLD.lifecycle_state THEN
   operation_id_value := NULLIF(current_setting('rutcampustrack.assignment_replacement_operation_id', true), '')::UUID;
   SELECT * INTO operation FROM assignment_replacement_operations WHERE operation_id = operation_id_value FOR UPDATE;
   IF NOT FOUND OR operation.state <> 'APPLIED' THEN
     RAISE EXCEPTION 'assignment change requires an exact applied lifecycle operation';
   END IF;
   IF NEW.valid_until_exclusive IS DISTINCT FROM OLD.valid_until_exclusive AND NOT (
       operation.operation_kind IN ('REPLACE', 'CLOSE_UNASSIGN') AND operation.source_assignment_id = OLD.id
       AND NEW.valid_until_exclusive = operation.effective_from
       AND (OLD.valid_until_exclusive IS NULL OR NEW.valid_until_exclusive < OLD.valid_until_exclusive)) THEN
     RAISE EXCEPTION 'assignment end does not match its applied operation';
   END IF;
   IF NEW.lifecycle_state IS DISTINCT FROM OLD.lifecycle_state AND NOT (
       (operation.operation_kind IN ('REPLACE', 'ADOPT_UNASSIGNED') AND operation.target_assignment_id = OLD.id
          AND OLD.lifecycle_state = 'PREPARED' AND NEW.lifecycle_state = 'ACTIVE')
       OR (operation.operation_kind = 'CLOSE_UNASSIGN' AND operation.source_assignment_id = OLD.id
          AND OLD.lifecycle_state = 'ACTIVE' AND NEW.lifecycle_state = 'CLOSED'
          AND ((operation.requested_cutoff <= OLD.valid_from AND NEW.valid_until_exclusive IS NOT DISTINCT FROM OLD.valid_until_exclusive)
            OR (operation.requested_cutoff > OLD.valid_from AND NEW.valid_until_exclusive=operation.effective_from)))) THEN
     RAISE EXCEPTION 'assignment lifecycle does not match its applied operation';
   END IF;
 END IF;
 RETURN NEW;
END $$;
