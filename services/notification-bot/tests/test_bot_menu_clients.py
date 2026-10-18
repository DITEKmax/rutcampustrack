import base64
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock

import grpc
import pytest

from bot.grpc_client import auth_authority_pb2 as auth_pb
from bot.grpc_client import bot_menu_client, bot_read_client
from bot.grpc_client import directed_bot_reads_pb2 as read_pb
from bot.grpc_client.bot_service_credentials import BotMenuCredentials, BotReadCredentials
from bot.services.bot_menu import BotGrant, BotReadDenied, BotReadUnavailable, MenuScope, resolve_menu_scope


def token():
    return base64.urlsafe_b64encode(bytes(range(32))).decode().rstrip("=")


def authority():
    return MenuScope(7, 2, 4, (BotGrant("TEACHER"), BotGrant("HEADMAN", 10)))


def auth_client(**methods):
    client = object.__new__(bot_menu_client.BotMenuGrpcClient)
    client._stub = SimpleNamespace(**methods)
    client._credentials = object()
    return client


def read_client(**methods):
    client = object.__new__(bot_read_client.BotReadGrpcClient)
    client._stub = SimpleNamespace(**methods)
    client._credentials = object()
    return client


@pytest.mark.parametrize(
    "plugin,service,allowed",
    [
        (
            BotMenuCredentials,
            "rutcampustrack.authauthority.AuthIdentityGrpcService",
            {"GetOwnBotMenuScope", "GetOwnBotProfile"},
        ),
        (
            BotReadCredentials,
            "rutcampustrack.botreads.DirectedBotReadService",
            {"GetOwnTeacherToday", "GetOwnHeadmanCurrentAttendance"},
        ),
    ],
)
def test_directed_tokens_never_reach_other_authorities_services_or_internal_methods(plugin, service, allowed):
    identity = plugin("own.test:19098", token())
    cases = [(f"https://own.test:19098/{service}", method, True) for method in allowed]
    cases.extend(
        (url, method, False)
        for url, method in [
            (f"https://other.test:19098/{service}", next(iter(allowed))),
            (f"http://own.test:19098/{service}", next(iter(allowed))),
            (f"https://own.test:19099/{service}", next(iter(allowed))),
            ("https://own.test:19098/rutcampustrack.academic.AcademicGrpcService", next(iter(allowed))),
            (f"https://own.test:19098/{service}", "CheckBotOwnAuthority"),
            (f"https://own.test:19098/{service}", "ResolveOwnBotReadScope"),
            (f"https://own.test:19098/{service}", "GetOwnBotLessonAttendance"),
        ]
    )
    for url, method, permitted in cases:
        observed = []
        identity(
            SimpleNamespace(service_url=url, method_name=method),
            lambda metadata, error: observed.append((metadata, error)),
        )
        metadata, error = observed[0]
        assert (error is None) is permitted
        assert bool(metadata) is permitted
        if permitted:
            assert metadata == (("x-service-token", token()),)


@pytest.mark.parametrize(
    "module,cls,stub_name",
    [
        (bot_menu_client, bot_menu_client.BotMenuGrpcClient, "AuthIdentityGrpcServiceStub"),
        (bot_read_client, bot_read_client.BotReadGrpcClient, "DirectedBotReadServiceStub"),
    ],
)
@pytest.mark.asyncio
async def test_capabilities_require_configured_tls_ca_and_exact_per_call_identity(
    tmp_path, monkeypatch, module, cls, stub_name
):
    ca = tmp_path / "synthetic-ca.pem"
    ca.write_bytes(b"synthetic trust fixture")
    channel = SimpleNamespace(close=AsyncMock())
    secure = Mock(return_value=channel)
    plaintext = Mock(side_effect=AssertionError("No plaintext fallback"))
    monkeypatch.setattr(module.grpc.aio, "secure_channel", secure)
    monkeypatch.setattr(module.grpc.aio, "insecure_channel", plaintext)
    monkeypatch.setattr(module.grpc, "ssl_channel_credentials", Mock(return_value=object()))
    monkeypatch.setattr(module.grpc, "metadata_call_credentials", lambda plugin: plugin)
    stub_module = module.auth_authority_pb2_grpc if module is bot_menu_client else module.directed_bot_reads_pb2_grpc
    monkeypatch.setattr(stub_module, stub_name, lambda channel: SimpleNamespace())
    for config in ({}, {"tls_enabled": True}, {"tls_enabled": True, "tls_ca_path": str(ca), "service_token": "bad"}):
        denied = cls("own.test", 19098, **config)
        with pytest.raises(BotReadUnavailable):
            await denied._call("unused", object())
    assert not secure.called and not plaintext.called
    configured = cls("own.test", 19098, tls_enabled=True, tls_ca_path=str(ca), service_token=token())
    assert secure.call_args.args[0] == "own.test:19098"
    assert isinstance(configured._credentials, BotMenuCredentials if module is bot_menu_client else BotReadCredentials)
    await configured.close()
    channel.close.assert_awaited_once()
    assert not plaintext.called


@pytest.mark.asyncio
async def test_auth_menu_and_nonsecret_profile_use_fresh_private_identity_without_academic_fallback():
    menu = AsyncMock(
        return_value=auth_pb.GetOwnBotMenuScopeResponse(
            found=True,
            user_id=7,
            telegram_binding_version=2,
            roles_version=4,
            grants=[auth_pb.BotActiveGrant(role="TEACHER", status="ACTIVE")],
        )
    )
    profile = AsyncMock(
        return_value=auth_pb.GetOwnBotProfileResponse(
            found=True, user_id=7, telegram_binding_version=2, roles_version=4, login="own-login", display_name="Own"
        )
    )
    client = auth_client(GetOwnBotMenuScope=menu, GetOwnBotProfile=profile)
    assert (await resolve_menu_scope(client, 123)).teacher_enabled
    assert (await client.own_profile(123)).login == "own-login"
    for method in (menu, profile):
        request = method.await_args.args[0]
        assert set(request.DESCRIPTOR.fields_by_name) == {"telegram_sender_id", "private_chat_id"}
        assert request.telegram_sender_id == request.private_chat_id == 123
        assert method.await_args.kwargs == {"metadata": (), "credentials": client._credentials, "timeout": 3.0}
    menu.return_value = auth_pb.GetOwnBotMenuScopeResponse(found=False)
    assert await resolve_menu_scope(client, 123) is None
    await client.menu_scope(123)
    assert menu.await_count == 3
    with pytest.raises(BotReadDenied):
        await client.own_profile(True)


@pytest.mark.asyncio
async def test_domain_requests_carry_only_own_identity_versions_and_revalidated_lesson_selection():
    own = authority()
    teacher = AsyncMock(
        return_value=read_pb.BotTeacherTodayResponse(server_now="2026-10-17T21:30:00Z", educational_date="2026-10-18")
    )
    headman = AsyncMock(
        return_value=read_pb.BotHeadmanCurrentAttendanceResponse(
            server_now="2026-10-17T21:30:00Z",
            educational_date="2026-10-18",
            state=read_pb.BOT_CURRENT_ATTENDANCE_STATE_READY,
            lesson=read_pb.BotLessonSummary(lesson_id=11, group_id=10),
            rows=[read_pb.BotAttendanceRow(student_id=8, display_name="Own", status="ABSENT", record_present=False)],
            statistics=read_pb.BotAttendanceStatistics(roster_count=1, marked_count=0, unmarked_count=1),
        )
    )
    client = read_client(GetOwnTeacherToday=teacher, GetOwnHeadmanCurrentAttendance=headman)
    assert not (await client.teacher_today(123, own)).lessons
    result = await client.headman_current(123, own, 11)
    assert result.state == "READY" and not result.rows[0].record_present
    for identity in (teacher.await_args.args[0], headman.await_args.args[0].identity):
        assert set(identity.DESCRIPTOR.fields_by_name) == {
            "telegram_sender_id",
            "private_chat_id",
            "expected_user_id",
            "expected_binding_version",
            "expected_roles_version",
        }
        assert identity.telegram_sender_id == identity.private_chat_id == 123
        assert (identity.expected_user_id, identity.expected_binding_version, identity.expected_roles_version) == (
            7,
            2,
            4,
        )
    assert headman.await_args.args[0].selected_lesson_id == 11
    assert headman.await_args.kwargs == {"metadata": (), "credentials": client._credentials, "timeout": 3.0}
    headman.return_value.statistics.absent_count = 1
    with pytest.raises(BotReadUnavailable):
        await client.headman_current(123, own, 11)
    for state in (0, 999):
        headman.return_value.state = state
        with pytest.raises(BotReadUnavailable):
            await client.headman_current(123, own, 11)


@pytest.mark.parametrize(
    "code,expected",
    [
        (grpc.StatusCode.PERMISSION_DENIED, BotReadDenied),
        (grpc.StatusCode.FAILED_PRECONDITION, BotReadDenied),
        (grpc.StatusCode.UNAVAILABLE, BotReadUnavailable),
    ],
)
@pytest.mark.asyncio
async def test_authority_and_dependency_errors_never_return_partial_or_private_data(code, expected):
    call = AsyncMock(side_effect=grpc.aio.AioRpcError(code, (), (), "PRIVATE_MARKER"))
    client = auth_client(GetOwnBotProfile=call)
    with pytest.raises(expected) as error:
        await client.own_profile(123)
    assert "PRIVATE_MARKER" not in str(error.value)


def test_optional_directed_read_identity_is_hidden_in_config_repr():
    from bot.config import Settings

    settings = Settings(_env_file=None, bot_to_read_service_token=token())
    assert token() not in repr(settings)
    assert not settings.bot_read_grpc_tls_enabled
