from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.grpc_client.academic_pb2 import UserByTelegramIdResponse, UserResponse, UserRoleGrant
from bot.handlers.prefs import (
    CREDENTIALS_LABEL,
    HOMEWORK_TODAY_LABEL,
    HOMEWORK_WEEK_LABEL,
    LINKS_LABEL,
    LOGIN_LABEL,
    SETTINGS_LABEL,
    keyboard_signature,
    main_keyboard,
)
from bot.services.bot_menu import BotGrant, MenuScope
from bot.services.keyboard_sync import KeyboardSyncClient, mark_keyboard_synced, sync_registered_keyboards


@pytest.mark.asyncio
async def test_keyboard_registry_roundtrip(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)

    await client.register_user(222)
    await client.register_user(111)
    await client.mark_user_synced(111, "v1")

    assert await client.get_registered_user_ids() == [111, 222]
    assert await client.get_user_keyboard_version(111) == "v1"
    assert await client.get_user_keyboard_version(222) is None


@pytest.mark.asyncio
async def test_mark_keyboard_synced_noops_without_client():
    await mark_keyboard_synced(None, 111, "v1")


@pytest.mark.asyncio
async def test_sync_registered_keyboards_sends_only_stale_users(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    await client.register_user(111)
    await client.register_user(222)
    await client.mark_user_synced(111, keyboard_signature())

    bot = MagicMock()
    bot.send_message = AsyncMock()

    academic = MagicMock()
    academic.get_user_by_telegram_id = AsyncMock(return_value=UserByTelegramIdResponse(found=False))
    await sync_registered_keyboards(
        bot=bot,
        keyboard_sync=client,
        academic_client=academic,
        delay_seconds=0,
        bot_menu_client=SimpleNamespace(menu_scope=AsyncMock(return_value=MenuScope(17, 2, 1, ()))),
    )

    bot.send_message.assert_awaited_once()
    assert bot.send_message.call_args.kwargs["chat_id"] == 222
    assert bot.send_message.call_args.kwargs["disable_notification"] is True
    assert await client.get_user_keyboard_version(222) == keyboard_signature()


@pytest.mark.parametrize("enabled", [False, True])
def test_common_actions_first_once_and_role_actions_are_explicit(enabled):
    keyboard = main_keyboard(homework_enabled=enabled)
    rows = [[button.text for button in row] for row in keyboard.keyboard]
    labels = sum(rows, [])
    assert rows[:3] == [[CREDENTIALS_LABEL, LINKS_LABEL], [LOGIN_LABEL], [SETTINGS_LABEL]]
    assert len(labels) == len(set(labels))
    assert (HOMEWORK_TODAY_LABEL in labels) is enabled
    assert (HOMEWORK_WEEK_LABEL in labels) is enabled
    assert keyboard.is_persistent is True
    assert keyboard_signature(homework_enabled=True) != keyboard_signature()


async def test_sync_resolves_each_current_user_and_removes_old_homework_keyboard(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    for user_id in [111, 222, 333]:
        await client.register_user(user_id)
    await client.mark_user_synced(111, keyboard_signature(homework_enabled=True))
    await client.mark_user_synced(222, keyboard_signature())
    bot = MagicMock(send_message=AsyncMock())
    academic = MagicMock()
    academic.get_user_by_telegram_id = AsyncMock(
        side_effect=[UserByTelegramIdResponse(found=True, user_id=user_id) for user_id in [111, 222, 333]]
    )
    academic.get_user_by_id = AsyncMock(
        side_effect=[
            UserResponse(
                roles_version=1, id=111, telegram_id=111, roles=[UserRoleGrant(role="TEACHER", status="ACTIVE")]
            ),
            UserResponse(
                roles_version=1,
                id=222,
                telegram_id=222,
                roles=[
                    UserRoleGrant(role="TEACHER", status="ACTIVE"),
                    UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10),
                ],
            ),
            RuntimeError("PRIVATE_MARKER"),
        ]
    )
    menu = SimpleNamespace(
        menu_scope=AsyncMock(
            side_effect=[
                MenuScope(111, 2, 1, (BotGrant("TEACHER"),)),
                MenuScope(222, 2, 1, (BotGrant("TEACHER"), BotGrant("STUDENT", 10))),
                RuntimeError("PRIVATE_MARKER"),
            ]
        )
    )
    await sync_registered_keyboards(bot, client, academic, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    sent = {call.kwargs["chat_id"]: call.kwargs["reply_markup"] for call in bot.send_message.await_args_list}
    assert set(sent) == {111, 222, 333}

    def labels(user):
        return [button.text for row in sent[user].keyboard for button in row]

    assert HOMEWORK_WEEK_LABEL not in labels(111)
    assert HOMEWORK_WEEK_LABEL in labels(222) and HOMEWORK_TODAY_LABEL in labels(222)
    assert HOMEWORK_WEEK_LABEL not in labels(333)
    assert await client.get_user_keyboard_version(111) == keyboard_signature(teacher_enabled=True)
    assert await client.get_user_keyboard_version(222) == keyboard_signature(
        homework_enabled=True, teacher_enabled=True
    )
    assert await client.get_user_keyboard_version(333) is None


async def test_startup_recovers_rpc_outage_even_when_old_role_signature_matched(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    await client.register_user(111)
    scope = MenuScope(17, 2, 1, (BotGrant("TEACHER"), BotGrant("STUDENT", 10)))
    signature = keyboard_signature(**scope.keyboard_options)
    await client.mark_user_synced(111, signature)
    menu = SimpleNamespace(menu_scope=AsyncMock(side_effect=[RuntimeError("outage"), scope]))
    delivered = []

    async def send(**kwargs):
        assert await client.get_user_keyboard_version(111) is None
        delivered.append([button.text for row in kwargs["reply_markup"].keyboard for button in row])

    bot = SimpleNamespace(send_message=AsyncMock(side_effect=send))
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    assert len(delivered[0]) == 4
    assert HOMEWORK_TODAY_LABEL in delivered[1]
    assert len(delivered[1]) == len(set(delivered[1]))
    assert await client.get_user_keyboard_version(111) == signature


@pytest.mark.parametrize("failure", ["rpc", "send", "fallback_send"])
async def test_startup_failure_is_bounded_and_leaves_user_pending(fake_redis, failure):
    client = KeyboardSyncClient(redis_client=fake_redis)
    await client.register_user(111)
    await client.mark_user_synced(111, keyboard_signature(teacher_enabled=True))
    scope = MenuScope(17, 2, 1, (BotGrant("STUDENT", 10),))
    menu = SimpleNamespace(menu_scope=AsyncMock(return_value=scope))
    bot = SimpleNamespace(send_message=AsyncMock())
    if failure in {"rpc", "fallback_send"}:
        menu.menu_scope.side_effect = RuntimeError("outage")
    if failure in {"send", "fallback_send"}:
        bot.send_message.side_effect = RuntimeError("provider failure")
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    assert menu.menu_scope.await_count == 3
    assert bot.send_message.await_count == (1 if failure == "rpc" else 3)
    assert await client.get_user_keyboard_version(111) is None
    menu.menu_scope.side_effect = None
    bot.send_message.side_effect = None
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    assert await client.get_user_keyboard_version(111) == keyboard_signature(**scope.keyboard_options)


async def test_startup_retries_failed_role_delivery_and_marks_only_success(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    await client.register_user(111)
    scope = MenuScope(17, 2, 1, (BotGrant("TEACHER"),))
    menu = SimpleNamespace(menu_scope=AsyncMock(return_value=scope))

    attempts = 0

    async def send(**kwargs):
        nonlocal attempts
        assert await client.get_user_keyboard_version(111) is None
        attempts += 1
        if attempts == 1:
            raise RuntimeError("provider failure")

    bot = SimpleNamespace(send_message=AsyncMock(side_effect=send))
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    assert bot.send_message.await_count == 2
    assert await client.get_user_keyboard_version(111) == keyboard_signature(teacher_enabled=True)


@pytest.mark.parametrize("outage_attempts", [1, 2])
async def test_failed_invalidation_cannot_replace_current_role_keyboard_with_fallback(
    fake_redis, monkeypatch, outage_attempts
):
    client = KeyboardSyncClient(redis_client=fake_redis)
    scope = MenuScope(17, 2, 1, (BotGrant("TEACHER"), BotGrant("STUDENT", 10)))
    signature = keyboard_signature(**scope.keyboard_options)
    await client.register_user(111)
    await client.mark_user_synced(111, signature)
    visible = main_keyboard(**scope.keyboard_options)
    delivered = []
    original_hdel = fake_redis.hdel
    invalidations = 0

    async def hdel(*args, **kwargs):
        nonlocal invalidations
        invalidations += 1
        if invalidations == 1:
            raise RuntimeError("one-shot Redis failure")
        return await original_hdel(*args, **kwargs)

    monkeypatch.setattr(fake_redis, "hdel", hdel)

    async def send(**kwargs):
        nonlocal visible
        # Any fallback replacement must first make the old role signature stale.
        assert await client.get_user_keyboard_version(111) is None
        visible = kwargs["reply_markup"]
        delivered.append([button.text for row in visible.keyboard for button in row])

    menu = SimpleNamespace(
        menu_scope=AsyncMock(side_effect=[RuntimeError("role RPC unavailable")] * outage_attempts + [scope])
    )
    bot = SimpleNamespace(send_message=AsyncMock(side_effect=send))
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    expected = [button.text for row in main_keyboard(**scope.keyboard_options).keyboard for button in row]
    assert [button.text for row in visible.keyboard for button in row] == expected
    assert await client.get_user_keyboard_version(111) == signature
    if outage_attempts == 1:
        # Redis did not accept pending: the previously delivered keyboard stays truthful.
        bot.send_message.assert_not_awaited()
    else:
        assert len(delivered[0]) == 4
        assert delivered[1] == expected
