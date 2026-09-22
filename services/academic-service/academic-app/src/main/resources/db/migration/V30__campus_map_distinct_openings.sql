-- Count every distinct logical floor opening.  V26's owner/floor/day key
-- suppressed a second opening by the same actor; retry idempotency remains
-- protected by the owner/intent key and the immutable open-intent row.
ALTER TABLE campus_map_floor_demand_dedupe
    DROP CONSTRAINT campus_map_floor_demand_dedupe_owner_floor_day_uq;
