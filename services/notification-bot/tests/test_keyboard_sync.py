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
    assert rows[:3] == [[CREDENTIALS_LABEL, LINKS_LABEL], [SETTINGS_LABEL], [LOGIN_LABEL]]
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
    await sync_registered_keyboards(bot, client, academic, delay_seconds=0)
    sent = {call.kwargs["chat_id"]: call.kwargs["reply_markup"] for call in bot.send_message.await_args_list}
    assert set(sent) == {111, 222, 333}

    def labels(user):
        return [button.text for row in sent[user].keyboard for button in row]

    assert HOMEWORK_WEEK_LABEL not in labels(111)
    assert HOMEWORK_WEEK_LABEL in labels(222) and HOMEWORK_TODAY_LABEL in labels(222)
    assert HOMEWORK_WEEK_LABEL not in labels(333)
    assert await client.get_user_keyboard_version(111) == keyboard_signature()
    assert await client.get_user_keyboard_version(222) == keyboard_signature(homework_enabled=True)
