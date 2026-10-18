from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from bot.grpc_client.academic_pb2 import UserByTelegramIdResponse, UserResponse, UserRoleGrant
from bot.services.menu_scope import HomeworkScope, resolve_homework_scope


def client(*roles, **fields):
    user = UserResponse(roles_version=1, id=77, telegram_id=12345, role="student", group_id=999, roles=roles)
    for name, value in fields.items():
        setattr(user, name, value)
    return SimpleNamespace(
        get_user_by_telegram_id=AsyncMock(return_value=UserByTelegramIdResponse(found=True, user_id=77)),
        get_user_by_id=AsyncMock(return_value=user),
    )


@pytest.mark.parametrize(
    "roles",
    [
        [],
        [UserRoleGrant(role="TEACHER", status="ACTIVE")],
        [UserRoleGrant(role="HEADMAN", status="ACTIVE", group_id=10)],
        [UserRoleGrant(role="STUDENT", status="SUSPENDED", group_id=10)],
        [UserRoleGrant(role="STUDENT", status="ACTIVE")],
        [
            UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10),
            UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=11),
        ],
    ],
)
async def test_legacy_student_does_not_grant_homework_when_current_grant_does_not(roles):
    assert await resolve_homework_scope(client(*roles), 12345) is None


@pytest.mark.parametrize("fields", [{"id": 78}, {"telegram_id": 54321}, {"roles_version": 0}])
async def test_user_or_telegram_binding_mismatch_denies_access(fields):
    academic = client(UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10), **fields)
    assert await resolve_homework_scope(academic, 12345) is None


async def test_fresh_role_revocation_removes_access_without_reusing_old_scope():
    academic = client(UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10))
    assert await resolve_homework_scope(academic, 12345) == HomeworkScope(77, 10, 1)
    academic.get_user_by_id.return_value.roles[0].status = "SUSPENDED"
    assert await resolve_homework_scope(academic, 12345) is None
    assert academic.get_user_by_id.await_count == 2


async def test_unknown_binding_and_authority_outage_do_not_grant_access(caplog):
    academic = client()
    academic.get_user_by_telegram_id.return_value.found = False
    assert await resolve_homework_scope(academic, 12345) is None
    academic.get_user_by_id.assert_not_awaited()
    academic.get_user_by_telegram_id.side_effect = RuntimeError("PRIVATE_MARKER")
    assert await resolve_homework_scope(academic, 12345) is None

    assert "PRIVATE_MARKER" not in caplog.text


async def test_protected_homework_client_sends_only_identity_version_and_dates_on_directed_call():
    from bot.grpc_client.academic_pb2 import HomeworkInfo, HomeworksForWeekResponse
    from bot.grpc_client.bot_credentials_client import BotCredentialsGrpcClient

    observed = AsyncMock(return_value=HomeworksForWeekResponse(homeworks=[HomeworkInfo(homework_id=88)]))
    client = object.__new__(BotCredentialsGrpcClient)
    client._stub = SimpleNamespace(GetOwnBotHomeworksForWeek=observed)
    client._call_credentials = object()
    client._metadata = (("x-grpc-secret", "synthetic-shared"),)
    result = await client.homeworks(
        telegram_sender_id=12345,
        expected_user_id=77,
        expected_roles_version=2,
        date_from="2026-05-07",
        date_to="2026-05-13",
    )
    assert [item.homework_id for item in result] == [88]
    request = observed.await_args.args[0]
    assert set(request.DESCRIPTOR.fields_by_name) == {
        "telegram_sender_id",
        "expected_user_id",
        "expected_roles_version",
        "expected_telegram_binding_version",
        "date_from",
        "date_to",
    }
    assert request.telegram_sender_id == 12345 and request.expected_user_id == 77
    assert request.expected_roles_version == 2 and request.date_from == "2026-05-07"
    assert request.date_to == "2026-05-13"
    assert observed.await_args.kwargs == {
        "metadata": client._metadata,
        "credentials": client._call_credentials,
        "timeout": 3.0,
    }
