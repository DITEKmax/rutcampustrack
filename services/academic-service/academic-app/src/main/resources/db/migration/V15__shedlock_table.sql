-- M02 Группа 3 — ShedLock table для distributed scheduler locks в academic_db.
-- Требуется для OutboxPublisherJob / OutboxCleanupJob (Группы 4 и 6) —
-- первые @Scheduled методы в academic-service.
-- Документация: https://github.com/lukas-krecan/ShedLock#jdbctemplate
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
