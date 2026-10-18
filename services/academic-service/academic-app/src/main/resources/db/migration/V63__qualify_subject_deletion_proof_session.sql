-- Preserve the published signature, owner, grants and every live-proof predicate.
-- PG16 PL/pgSQL qualifies function parameters by the implicit function-name block label.
CREATE OR REPLACE FUNCTION auth_api.subject_proof_admitted(proof uuid, operation uuid, digest text, subject bigint,
    semester bigint, group_scope bigint, actor bigint, sid uuid, cutoff timestamptz)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM p.receipt_id FROM public.subject_deletion_proofs p WHERE p.receipt_id=proof FOR UPDATE;
    RETURN EXISTS(SELECT FROM public.subject_deletion_proofs p JOIN public.users u ON u.id=p.actor_id
        JOIN public.groups g ON g.id=p.group_id JOIN public.auth_sessions s ON s.sid=p.session_id AND s.user_id=p.actor_id
        JOIN public.user_role_grants h ON h.id=s.active_role_grant_id AND h.user_id=p.actor_id
        WHERE p.receipt_id=proof AND p.operation_id=operation AND p.preview_digest=digest AND p.subject_id=subject
          AND p.semester_id=semester AND p.group_id=group_scope AND p.actor_id=actor AND p.session_id=subject_proof_admitted.sid
          AND p.consumed_at IS NOT NULL AND p.expires_at>cutoff
          AND p.credentials_version=u.credentials_version AND p.binding_version=u.telegram_binding_version
          AND p.telegram_id=u.telegram_id AND p.roles_version=u.roles_version AND p.session_version=s.session_version
          AND s.revoked_at IS NULL AND s.refresh_expires_at>cutoff
          AND u.status::text<>'archived' AND g.is_active AND h.role='headman' AND h.status='active' AND h.group_id=p.group_id
          AND NOT EXISTS(SELECT FROM public.user_role_grants peer WHERE peer.role='headman' AND peer.status='active'
              AND peer.group_id=p.group_id AND peer.user_id<>p.actor_id)
          AND EXISTS(SELECT FROM public.user_role_grants student WHERE student.user_id=p.actor_id AND student.role='student'
              AND student.status='active' AND student.group_id=p.group_id));
END $$;
