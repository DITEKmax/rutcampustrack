ALTER TABLE campus_map_deletion_receipt
    DROP CONSTRAINT campus_map_receipt_action,
    ADD CONSTRAINT campus_map_receipt_action CHECK (
        (action_kind = 'INVENTORY' AND building_code IS NULL AND floor_code IS NULL AND format IS NULL)
        OR (action_kind = 'PLAN_CLEAR' AND target_type = 'FLOOR'
            AND building_code IS NOT NULL AND building_code ~ '^[1-9][0-9]*$'
            AND floor_code IS NOT NULL AND floor_code ~ '^(0|-?[1-9][0-9]*)$'
            AND (format IS NULL OR format IN ('png', 'svg')))
    );
