-- V27 added recurring-template restore admission but replaced the V25 one-off
-- admission branch. Keep all three exact authority paths together; retained
-- lifecycle rows and applied migration checksums are unchanged.
CREATE OR REPLACE FUNCTION validate_lesson_restore_lifecycle_entry()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    one_off_restore_row one_off_lesson_restore_authorities%ROWTYPE;
    template_row lesson_template_restore_authorities%ROWTYPE;
    source_row lessons%ROWTYPE;
    restore_row lesson_restore_authorities%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    target_lesson_row lessons%ROWTYPE;
BEGIN
    IF NEW.one_off_restore_operation_id IS NOT NULL THEN
        SELECT * INTO one_off_restore_row FROM one_off_lesson_restore_authorities
         WHERE operation_id = NEW.one_off_restore_operation_id AND occurrence_id = NEW.occurrence_id;
        SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
        SELECT * INTO target_lesson_row FROM lessons WHERE id = one_off_restore_row.target_lesson_id;
        IF one_off_restore_row.operation_id IS NULL OR occurrence_row.id IS NULL OR target_lesson_row.id IS NULL
           OR NEW.restore_operation_id IS NOT NULL OR NEW.transfer_operation_id IS NOT NULL
           OR NEW.template_operation_id IS NOT NULL
           OR target_lesson_row.occurrence_id <> NEW.occurrence_id
           OR NEW.action <> 'RESTORED' OR NEW.lesson_id <> one_off_restore_row.source_lesson_id
           OR NEW.target_lesson_id IS DISTINCT FROM one_off_restore_row.target_lesson_id
           OR NEW.actor_id <> one_off_restore_row.actor_id OR NEW.reason IS NOT NULL
           OR NEW.revision <> one_off_restore_row.expected_occurrence_revision + 1
           OR NEW.generation <> one_off_restore_row.target_generation
           OR occurrence_row.current_lesson_id IS DISTINCT FROM one_off_restore_row.target_lesson_id
           OR occurrence_row.revision <> NEW.revision OR occurrence_row.generation <> NEW.generation
           OR target_lesson_row.status::text <> 'planned' THEN
            RAISE EXCEPTION 'one-off RESTORED history differs from exact completed generation';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.action = 'RESTORED' AND NEW.template_operation_id IS NOT NULL THEN
        SELECT * INTO template_row FROM lesson_template_restore_authorities
         WHERE operation_id = NEW.template_operation_id AND occurrence_id = NEW.occurrence_id;
        SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
        SELECT * INTO source_row FROM lessons WHERE id = template_row.source_lesson_id AND occurrence_id = NEW.occurrence_id;
        SELECT * INTO target_lesson_row FROM lessons WHERE id = template_row.target_lesson_id AND occurrence_id = NEW.occurrence_id;
        IF template_row.operation_id IS NULL OR source_row.id IS NULL OR target_lesson_row.id IS NULL
           OR NEW.restore_operation_id IS NOT NULL
           OR NULLIF(current_setting('rutcampustrack.template_restore_operation_id', true),'')::UUID
                IS DISTINCT FROM NEW.template_operation_id
           OR NEW.lesson_id IS DISTINCT FROM template_row.source_lesson_id
           OR NEW.target_lesson_id IS DISTINCT FROM template_row.target_lesson_id
           OR NEW.revision <> template_row.expected_revision + 1
           OR NEW.generation <> template_row.expected_generation + 1
           OR NEW.actor_id <> template_row.actor_id OR NEW.occurred_at <> template_row.created_at
           OR NEW.reason IS NOT NULL OR source_row.status::text <> 'cancelled'
           OR source_row.revision <> template_row.expected_lesson_revision
           OR occurrence_row.current_lesson_id <> template_row.target_lesson_id
           OR occurrence_row.revision <> NEW.revision OR occurrence_row.generation <> NEW.generation
           OR occurrence_row.schedule_item_id <> template_row.target_schedule_item_id
           OR occurrence_row.assignment_id <> template_row.target_assignment_id
           OR occurrence_row.assigned_teacher_id <> template_row.target_teacher_id
           OR target_lesson_row.schedule_item_id <> template_row.target_schedule_item_id
           OR target_lesson_row.assignment_id <> template_row.target_assignment_id
           OR target_lesson_row.assigned_teacher_id <> template_row.target_teacher_id
           OR target_lesson_row.generation <> NEW.generation OR target_lesson_row.status::text <> 'planned' THEN
            RAISE EXCEPTION 'RESTORED history is outside its exact template current-generation authority';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.restore_operation_id IS NULL THEN
        IF NEW.action = 'RESTORED' AND NEW.target_lesson_id IS NOT NULL THEN
            RAISE EXCEPTION 'replacement restore history requires its exact restore operation';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.action <> 'RESTORED' THEN
        RAISE EXCEPTION 'restore operation can authorize only RESTORED history';
    END IF;
    SELECT * INTO restore_row FROM lesson_restore_authorities
     WHERE operation_id = NEW.restore_operation_id
       AND occurrence_id = NEW.occurrence_id;
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
    SELECT * INTO target_lesson_row FROM lessons
     WHERE id = restore_row.target_lesson_id
       AND occurrence_id = restore_row.occurrence_id;
    IF NOT FOUND OR NEW.revision <> restore_row.expected_occurrence_revision + 1
       OR NEW.lesson_id <> restore_row.source_lesson_id
       OR NEW.target_lesson_id <> restore_row.target_lesson_id
       OR NEW.generation <> restore_row.target_generation
       OR NEW.actor_id <> restore_row.actor_id
       OR NEW.reason IS NOT NULL
       OR occurrence_row.current_lesson_id <> restore_row.target_lesson_id
       OR occurrence_row.revision <> NEW.revision
       OR occurrence_row.generation <> NEW.generation
       OR target_lesson_row.status::text <> 'planned' THEN
        RAISE EXCEPTION 'RESTORED history is outside its exact current-generation authority';
    END IF;
    RETURN NEW;
END
$$;
