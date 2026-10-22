-- Keep the original one-cycle transition lock durable while its remote invalidation is pending.
-- Completed cycle records retain their existing lifetime; this guard exists only until publication.
CREATE TABLE academic_group_availability_cycle_guards (
    cycle_semester_id bigint PRIMARY KEY REFERENCES semesters(id) ON DELETE RESTRICT,
    operation_id uuid NOT NULL REFERENCES academic_group_availability_operations(operation_id) ON DELETE RESTRICT
);
CREATE FUNCTION guard_accepted_group_cycle_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM academic_group_availability_cycle_guards WHERE cycle_semester_id=OLD.id)
       AND (TG_OP='DELETE' OR (to_jsonb(OLD)-ARRAY['name']) IS DISTINCT FROM (to_jsonb(NEW)-ARRAY['name'])) THEN
        RAISE EXCEPTION 'accepted group cycle blocks semester transition' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER a0_accepted_group_cycle_identity BEFORE UPDATE OR DELETE ON semesters
    FOR EACH ROW EXECUTE FUNCTION guard_accepted_group_cycle_identity();
