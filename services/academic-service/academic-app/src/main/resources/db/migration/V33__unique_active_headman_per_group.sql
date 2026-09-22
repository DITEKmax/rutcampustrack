-- JS-ADMIN-06/07: one durable active HEADMAN grant per group.
-- Fail closed before creating the invariant. Existing duplicate rows are
-- historical data that require an explicit owner-led repair; this migration
-- never deletes a row or chooses a winner.
DO $$
DECLARE
    duplicate_groups TEXT;
BEGIN
    SELECT string_agg(group_id::text, ', ' ORDER BY group_id)
      INTO duplicate_groups
      FROM (
          SELECT group_id
          FROM user_role_grants
          WHERE role = 'headman'
            AND status = 'active'
            AND group_id IS NOT NULL
          GROUP BY group_id
          HAVING COUNT(*) > 1
      ) duplicates;

    IF duplicate_groups IS NOT NULL THEN
        RAISE EXCEPTION
            'V33 cannot enforce one active headman per group; duplicate group ids: %',
            duplicate_groups;
    END IF;
END
$$;

CREATE UNIQUE INDEX user_role_grants_active_headman_group_uq
    ON user_role_grants (group_id)
    WHERE role = 'headman'
      AND status = 'active'
      AND group_id IS NOT NULL;
