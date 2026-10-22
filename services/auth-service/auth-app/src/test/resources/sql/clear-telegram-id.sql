UPDATE
    auth_identities
SET
    telegram_id = NULL
WHERE
    login = 'student';
