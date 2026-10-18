-- Auth-owned leases for credential/security outbox and QR cleanup only.
-- The existing scheduler provider uses this exact table; no domain report job lives here.
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
