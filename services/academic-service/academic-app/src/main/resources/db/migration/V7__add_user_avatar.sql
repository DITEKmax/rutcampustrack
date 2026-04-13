-- BUG-004: добавить выбор аватара пользователя.
-- Хранится только идентификатор пресета (например "avatar_03"); SVG живут на фронте.
-- NULL = пользователь не выбрал — рисуем инициалы.

ALTER TABLE users
    ADD COLUMN avatar_id VARCHAR(32);

COMMENT ON COLUMN users.avatar_id IS
    'Identifier of a preset avatar (e.g. avatar_03). NULL means render initials.';
