-- Ephemeral e2e database only; real Telegram/provider data is never used.
DO $$
BEGIN
    UPDATE
        users
    SET
        telegram_id = 900000001
    WHERE
        login = 'teacher';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'E2E teacher seed is missing';
    END IF;
END
$$;
