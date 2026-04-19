-- M02 Группа 1 — ShedLock table для distributed scheduler locks.
-- Документация: https://github.com/lukas-krecan/ShedLock#jdbctemplate
-- Используется JdbcTemplateLockProvider в schedule-service
-- (@SchedulerLock на LessonStatusTransitionJob.runTransitions()).
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
