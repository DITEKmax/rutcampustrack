"""Fresh Telegram binding and durable student-grant scope for homework actions."""

import logging
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class HomeworkScope:
    user_id: int
    group_id: int
    roles_version: int


async def resolve_homework_scope(academic_client, telegram_id: int, binding=None) -> HomeworkScope | None:
    """Never infer eligibility from the legacy single-role/group projection."""
    if type(telegram_id) is not int or telegram_id <= 0:
        return None
    try:
        binding = binding if binding is not None else await academic_client.get_user_by_telegram_id(telegram_id)
        if binding.found is not True or type(binding.user_id) is not int or binding.user_id <= 0:
            return None
        user = await academic_client.get_user_by_id(binding.user_id)
        if type(user.id) is not int or type(user.telegram_id) is not int:
            return None
        if user.id != binding.user_id or user.telegram_id != telegram_id:
            return None
        if type(user.roles_version) is not int or user.roles_version <= 0:
            return None
        groups = {
            grant.group_id
            for grant in user.roles
            if grant.role == "STUDENT"
            and grant.status == "ACTIVE"
            and type(grant.group_id) is int
            and grant.group_id > 0
        }
        if len(groups) != 1:
            return None
        return HomeworkScope(binding.user_id, groups.pop(), user.roles_version)
    except Exception:
        # Generic RPC errors may contain private metadata. Missing authority hides role actions.
        logger.warning("Telegram homework authority unavailable")
        return None
