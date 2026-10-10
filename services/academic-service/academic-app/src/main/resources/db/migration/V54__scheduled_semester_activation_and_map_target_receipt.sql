ALTER TABLE semesters ADD COLUMN activation_scheduled boolean NOT NULL DEFAULT false;
CREATE INDEX semesters_scheduled_activation_due ON semesters (date_from, id)
    WHERE activation_scheduled = true;

ALTER TABLE campus_map_deletion_receipt
    ADD COLUMN action_kind varchar(16) NOT NULL DEFAULT 'INVENTORY',
    ADD COLUMN building_code varchar(64),
    ADD COLUMN floor_code varchar(64),
    ADD COLUMN format varchar(3),
    ADD CONSTRAINT campus_map_receipt_action CHECK (
        (action_kind = 'INVENTORY' AND building_code IS NULL AND floor_code IS NULL AND format IS NULL)
        OR (action_kind = 'PLAN_CLEAR' AND target_type = 'FLOOR'
            AND building_code IS NOT NULL AND building_code ~ '^[1-9][0-9]*$'
            AND floor_code IS NOT NULL AND floor_code ~ '^[1-9][0-9]*$'
            AND (format IS NULL OR format IN ('png', 'svg')))
    );
