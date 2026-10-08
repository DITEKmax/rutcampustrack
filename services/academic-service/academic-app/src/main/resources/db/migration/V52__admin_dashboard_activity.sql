-- Keep the established UTC lifetime aggregate unchanged. Moscow calendar
-- history starts at this durable boundary; old UTC days are never re-labelled.
CREATE TABLE campus_map_activity_coverage (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    captured_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO campus_map_activity_coverage (singleton) VALUES (TRUE);

CREATE TABLE campus_map_floor_hourly_activity (
    floor_id BIGINT NOT NULL REFERENCES campus_map_floor(id) ON DELETE CASCADE,
    hour_start TIMESTAMPTZ NOT NULL,
    open_count BIGINT NOT NULL CHECK (open_count > 0),
    PRIMARY KEY (floor_id, hour_start)
);
CREATE INDEX campus_map_activity_hour ON campus_map_floor_hourly_activity(hour_start);
CREATE INDEX campus_map_intent_accepted_at ON campus_map_open_intent(accepted_at);

CREATE FUNCTION increment_campus_map_hourly_activity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- The existing guarded dedupe INSERT is the single acceptance authority.
    -- UTC hours align with Europe/Moscow hours. Retries never fire this trigger.
    INSERT INTO campus_map_floor_hourly_activity(floor_id, hour_start, open_count)
    VALUES (NEW.floor_id, date_trunc('hour', NEW.accepted_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC', 1)
    ON CONFLICT (floor_id, hour_start)
    DO UPDATE SET open_count = campus_map_floor_hourly_activity.open_count + 1;
    RETURN NEW;
END $$;
CREATE TRIGGER campus_map_hourly_activity_count
    AFTER INSERT ON campus_map_floor_demand_dedupe
    FOR EACH ROW EXECUTE FUNCTION increment_campus_map_hourly_activity();
