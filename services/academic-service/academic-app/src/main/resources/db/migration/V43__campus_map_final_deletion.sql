-- Owner decision 2026-10-02: final deletion of a floor scheme and all its versions/files.
-- No delete runs here. Commands remain password-confirmed, preview-bound and atomic.
CREATE TABLE campus_map_deletion_receipt (
    operation_id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL CHECK (owner_id > 0),
    target_type VARCHAR(16) NOT NULL CHECK (target_type IN ('FLOOR', 'BUILDING')),
    target_id BIGINT NOT NULL CHECK (target_id > 0),
    preview_digest VARCHAR(64) NOT NULL CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE FUNCTION campus_map_final_delete_allowed(target_floor_id BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT COALESCE(current_setting('rutcampustrack.map_delete_floor_id', true) = target_floor_id::text, FALSE)
       AND EXISTS (SELECT 1 FROM campus_map_floor
                   WHERE id = target_floor_id AND current_version_id IS NULL)
$$;


CREATE OR REPLACE FUNCTION protect_campus_map_asset()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed((SELECT floor_id FROM campus_map_plan_version WHERE id = OLD.plan_version_id)) THEN
        RETURN OLD;
    END IF;

    RAISE EXCEPTION 'campus map assets are immutable and retained';
END
$$;

CREATE OR REPLACE FUNCTION protect_campus_map_open_intent()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed(OLD.floor_id) THEN
        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.owner_hmac IS DISTINCT FROM OLD.owner_hmac
           OR NEW.intent_id IS DISTINCT FROM OLD.intent_id
           OR NEW.building_id IS DISTINCT FROM OLD.building_id
           OR NEW.floor_id IS DISTINCT FROM OLD.floor_id
           OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
           OR NEW.accepted_utc_day IS DISTINCT FROM OLD.accepted_utc_day
           OR NEW.accepted_at IS DISTINCT FROM OLD.accepted_at
           OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
            RAISE EXCEPTION 'campus map open intent identity is immutable';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.expires_at > CURRENT_TIMESTAMP THEN
        RAISE EXCEPTION 'campus map open intent cannot be removed before expiry';
    END IF;
    RETURN OLD;
END
$$;

CREATE OR REPLACE FUNCTION validate_campus_map_ready_asset()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    asset_record       campus_map_asset%ROWTYPE;
    plan_version_id    BIGINT;
    plan_published_at  TIMESTAMPTZ;
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed((SELECT floor_id FROM campus_map_plan_version WHERE id = OLD.plan_version_id)) THEN
        RETURN OLD;
    END IF;

    plan_version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.plan_version_id ELSE NEW.plan_version_id END;

    SELECT published_at INTO plan_published_at
    FROM campus_map_plan_version
    WHERE id = plan_version_id
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'campus map plan version % does not exist', plan_version_id;
    END IF;

    IF TG_OP = 'UPDATE'
       AND (NEW.plan_version_id IS DISTINCT FROM OLD.plan_version_id
            OR NEW.format IS DISTINCT FROM OLD.format) THEN
        RAISE EXCEPTION 'campus map plan format identity is immutable';
    END IF;
    IF plan_published_at IS NOT NULL THEN
        RAISE EXCEPTION 'published campus map plan format is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    IF NEW.state = 'READY' THEN
        SELECT asset.* INTO asset_record
        FROM campus_map_asset AS asset
        WHERE asset.id = NEW.asset_id
          AND asset.plan_version_id = NEW.plan_version_id
          AND asset.format = NEW.format;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'ready campus map format requires a matching immutable asset';
        END IF;
        IF asset_record.bytes <> NEW.bytes
           OR asset_record.sha256 IS DISTINCT FROM NEW.sha256
           OR asset_record.content_type <> NEW.content_type
           OR asset_record.width IS DISTINCT FROM NEW.width
           OR asset_record.height IS DISTINCT FROM NEW.height THEN
            RAISE EXCEPTION 'ready campus map format metadata does not match its asset';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION protect_campus_map_demand_dedupe()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed(OLD.floor_id) THEN
        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'campus map demand dedupe is immutable';
    END IF;

    -- Retain the daily key through the end of its UTC day plus 48 hours,
    -- independent of the particular intent acceptance time.
    IF (OLD.utc_day::timestamp + INTERVAL '3 days')
       > (CURRENT_TIMESTAMP AT TIME ZONE 'UTC') THEN
        RAISE EXCEPTION 'campus map demand dedupe cannot be removed before UTC retention';
    END IF;
    RETURN OLD;
END
$$;

CREATE OR REPLACE FUNCTION protect_campus_map_plan_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed(OLD.floor_id) THEN
        RETURN OLD;
    END IF;

    -- A publication update and a draft graph edit both serialize on this row.
    PERFORM 1
    FROM campus_map_plan_version
    WHERE id = OLD.id
    FOR UPDATE;

    IF TG_OP = 'DELETE' THEN
        IF OLD.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'published campus map plan version is immutable';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.floor_id IS DISTINCT FROM OLD.floor_id
       OR NEW.version IS DISTINCT FROM OLD.version
       OR NEW.catalog_revision_id IS DISTINCT FROM OLD.catalog_revision_id THEN
        RAISE EXCEPTION 'campus map plan version identity is immutable';
    END IF;

    IF OLD.published_at IS NOT NULL THEN
        IF NEW.published_at IS DISTINCT FROM OLD.published_at
           OR NEW.label IS DISTINCT FROM OLD.label
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'published campus map plan version is immutable';
        END IF;
    END IF;

    RETURN NEW;
END
$$;
