from dataclasses import replace
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from aiogram.exceptions import TelegramBadRequest, TelegramForbiddenError, TelegramRetryAfter
from aiogram.methods import SendMessage

from bot.grpc_client import auth_authority_pb2 as pb
from bot.handlers.prefs import HOMEWORK_TODAY_LABEL, keyboard_signature
from bot.observability import (
    KEYBOARD_SYNC_CATALOG_AVAILABLE,
    KEYBOARD_SYNC_PENDING,
    KEYBOARD_SYNC_REGISTRY_AVAILABLE,
    KEYBOARD_SYNC_RETRYABLE,
    KEYBOARD_SYNC_TERMINAL,
    KEYBOARD_SYNC_USERS_FAILED,
    STARTUP_COMPLETED,
)
from bot.services.bot_menu import BotGrant, MenuScope
from bot.services.keyboard_sync import KeyboardSyncClient, receipt_for, sync_registered_keyboards


class Authority:
    def __init__(self, count=3):
        self.scopes = {1000 + i: MenuScope(i, 1, 1, (BotGrant("STUDENT", 10),)) for i in range(1, count + 1)}
        self.fail = False
        self.pages = []

    async def list_keyboard_bindings(self, after, upper, size):
        self.pages.append((after, upper, size))
        if self.fail:
            raise RuntimeError("Auth down")
        upper = upper or max((s.user_id for s in self.scopes.values()), default=0)
        rows = [
            (t, s) for t, s in sorted(self.scopes.items(), key=lambda r: r[1].user_id) if after < s.user_id <= upper
        ]
        page = rows[:size]
        return pb.ListBotKeyboardBindingsResponse(
            bindings=[
                pb.BotKeyboardBinding(
                    user_id=s.user_id,
                    telegram_id=t,
                    telegram_binding_version=s.telegram_binding_version,
                    roles_version=s.roles_version,
                )
                for t, s in page
            ],
            through_user_id=upper,
            next_after_user_id=page[-1][1].user_id if len(rows) > size else 0,
        )

    async def menu_scope(self, telegram_id):
        if self.fail:
            raise RuntimeError("Auth down")
        return self.scopes.get(telegram_id)


async def cycle(client, authority, bot, *, retry=0):
    await sync_registered_keyboards(bot, client, bot_menu_client=authority, delay_seconds=0, retry_delay_seconds=retry)


@pytest.mark.asyncio
async def test_outage_beyond_three_attempts_recovers_three_pending_without_restart(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority()
    for t in authority.scopes:
        await client.register_user(t)
    authority.fail = True
    bot = SimpleNamespace(send_message=AsyncMock())
    before = KEYBOARD_SYNC_USERS_FAILED._value.get()
    for _ in range(5):
        await cycle(client, authority, bot)
    assert KEYBOARD_SYNC_PENDING._value.get() == KEYBOARD_SYNC_RETRYABLE._value.get() == 3
    assert KEYBOARD_SYNC_CATALOG_AVAILABLE._value.get() == 0
    bot.send_message.assert_not_awaited()
    authority.fail = False
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 3
    assert KEYBOARD_SYNC_PENDING._value.get() == 0
    assert STARTUP_COMPLETED.labels("keyboard_sync")._value.get() == 1
    assert KEYBOARD_SYNC_USERS_FAILED._value.get() > before  # history remains after recovery
    assert all([await client.get_receipt(t) == receipt_for(s) for t, s in authority.scopes.items()])


@pytest.mark.asyncio
async def test_restart_resumes_pending_and_same_content_authority_skips_deploy(fake_redis):
    first = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    bot = SimpleNamespace(send_message=AsyncMock(side_effect=RuntimeError("network")))
    await cycle(first, authority, bot)
    pending = await first.state(1001)
    assert pending["status"] == "retryable"
    restarted = KeyboardSyncClient(redis_client=fake_redis)
    bot.send_message.side_effect = None
    await cycle(restarted, authority, bot)
    assert await restarted.get_receipt(1001) == receipt_for(authority.scopes[1001])
    count = bot.send_message.await_count
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    assert bot.send_message.await_count == count
    # A role version change needs fresh delivery even when visible buttons match.
    authority.scopes[1001] = replace(authority.scopes[1001], roles_version=2)
    await cycle(restarted, authority, bot)
    assert bot.send_message.await_count == count + 1


@pytest.mark.asyncio
async def test_receipt_write_failure_never_acknowledges_and_retries_after_ack(fake_redis, monkeypatch):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    bot = SimpleNamespace(send_message=AsyncMock())
    original = fake_redis.pipeline

    def pipeline(*args, **kwargs):
        pipe = original(*args, **kwargs)
        execute = pipe.execute

        async def failing(*a, **k):
            if any(cmd[0][0] == "HSET" and cmd[0][1] == client._RECEIPTS_KEY for cmd in pipe.command_stack):
                raise RuntimeError("Redis receipt outage")
            return await execute(*a, **k)

        pipe.execute = failing
        return pipe

    monkeypatch.setattr(fake_redis, "pipeline", pipeline)
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 1
    assert await client.get_receipt(1001) is None
    assert (await client.state(1001))["status"] == "retryable"
    monkeypatch.setattr(fake_redis, "pipeline", original)
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 2  # unavoidable ACK/storage ambiguity
    assert await client.get_receipt(1001) is not None


@pytest.mark.asyncio
async def test_receipt_read_failure_is_error_not_missing_and_other_recipient_progresses(fake_redis, monkeypatch):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(2)
    original = fake_redis.hget

    async def hget(key, field):
        if key == client._RECEIPTS_KEY and field == "1001":
            raise RuntimeError("read outage")
        return await original(key, field)

    monkeypatch.setattr(fake_redis, "hget", hget)
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    assert [c.kwargs["chat_id"] for c in bot.send_message.await_args_list] == [1002]
    assert (await client.state(1001))["status"] == "retryable"


@pytest.mark.asyncio
async def test_partial_success_and_429_durable_global_rate_backoff(fake_redis, monkeypatch):
    import bot.services.keyboard_sync as module

    clock = [1000.0]
    monkeypatch.setattr(module.time, "time", lambda: clock[0])
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(3)
    method = SendMessage(chat_id=1002, text="synthetic")
    bot = SimpleNamespace(
        send_message=AsyncMock(side_effect=[None, TelegramRetryAfter(method, "limited", 60), None, None])
    )
    await cycle(client, authority, bot)
    assert await client.get_receipt(1001) is not None
    assert await client.get_receipt(1002) is None and await client.get_receipt(1003) is None
    assert bot.send_message.await_count == 2
    restarted = KeyboardSyncClient(redis_client=fake_redis)
    clock[0] += 59
    await cycle(restarted, authority, bot)
    assert bot.send_message.await_count == 2
    clock[0] += 2
    await cycle(restarted, authority, bot)
    assert bot.send_message.await_count == 4 and KEYBOARD_SYNC_PENDING._value.get() == 0


@pytest.mark.parametrize("kind", ["blocked", "not_found"])
@pytest.mark.asyncio
async def test_terminal_provider_failure_reactivates_on_start_or_new_binding(fake_redis, kind):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    method = SendMessage(chat_id=1001, text="synthetic")
    error = (
        TelegramForbiddenError(method, "bot was blocked")
        if kind == "blocked"
        else TelegramBadRequest(method, "Bad Request: chat not found")
    )
    bot = SimpleNamespace(send_message=AsyncMock(side_effect=error))
    await cycle(client, authority, bot)
    assert KEYBOARD_SYNC_TERMINAL._value.get() == 1 and KEYBOARD_SYNC_PENDING._value.get() == 0
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    assert bot.send_message.await_count == 1
    bot.send_message.side_effect = None
    if kind == "blocked":
        await client.invalidate_user_keyboard(1001)  # same operation used by /start
    else:
        authority.scopes[1001] = replace(authority.scopes[1001], telegram_binding_version=2)
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 2 and KEYBOARD_SYNC_TERMINAL._value.get() == 0


@pytest.mark.asyncio
async def test_complete_paginated_authoritative_coverage_discovers_unregistered_bindings(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(403)
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    assert len(await client.get_registered_user_ids()) == 403
    assert authority.pages == [(0, 0, 200), (200, 403, 200), (400, 403, 200)]
    assert bot.send_message.await_count == 403
    assert KEYBOARD_SYNC_PENDING._value.get() == 0


@pytest.mark.asyncio
async def test_missing_menu_is_retryable_not_unbound_and_failed_sweep_cannot_remove_recipient(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    catalogue = authority.list_keyboard_bindings
    authority.menu_scope = AsyncMock(return_value=None)  # fenced/no grants is not unbound
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    assert (await client.state(1001))["status"] == "retryable"
    authority.list_keyboard_bindings = AsyncMock(side_effect=RuntimeError("catalogue partial failure"))
    await cycle(client, authority, bot)
    assert (await client.state(1001))["status"] == "retryable"
    authority.scopes.clear()
    authority.list_keyboard_bindings = catalogue
    await cycle(client, authority, bot)
    assert (await client.state(1001)) == dict(status="terminal", reason="unbound")
    authority.scopes[1001] = MenuScope(1, 2, 1, (BotGrant("TEACHER"),))
    authority.menu_scope.return_value = authority.scopes[1001]
    await cycle(client, authority, bot)
    assert await client.get_receipt(1001) is not None


@pytest.mark.parametrize("when", ["before", "during"])
@pytest.mark.asyncio
async def test_role_race_never_records_stale_receipt_and_next_pass_delivers_current_keyboard(fake_redis, when):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    old = authority.scopes[1001]
    new = replace(old, roles_version=2, grants=(BotGrant("TEACHER"),))
    original = authority.menu_scope
    calls = 0

    async def menu(t):
        nonlocal calls
        calls += 1
        if when == "before" and calls == 2:
            authority.scopes[t] = new
        return await original(t)

    authority.menu_scope = menu

    async def send(**kwargs):
        if when == "during":
            authority.scopes[1001] = new

    bot = SimpleNamespace(send_message=AsyncMock(side_effect=send))
    await cycle(client, authority, bot)
    assert await client.get_receipt(1001) is None
    if when == "before":
        bot.send_message.assert_not_awaited()
    await cycle(client, authority, bot)
    assert await client.get_receipt(1001) == receipt_for(new)
    labels = [b.text for row in bot.send_message.await_args.kwargs["reply_markup"].keyboard for b in row]
    assert HOMEWORK_TODAY_LABEL not in labels


@pytest.mark.asyncio
async def test_registry_unavailable_not_empty_success(fake_redis):
    client = KeyboardSyncClient(redis_client=SimpleNamespace(smembers=AsyncMock(side_effect=RuntimeError("down"))))
    with pytest.raises(RuntimeError, match="Keyboard registry unavailable"):
        await sync_registered_keyboards(SimpleNamespace(send_message=AsyncMock()), client, delay_seconds=0)
    assert KEYBOARD_SYNC_REGISTRY_AVAILABLE._value.get() == 0


@pytest.mark.asyncio
async def test_legacy_content_signature_cannot_skip_new_authoritative_receipt(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await client.register_user(1001)
    await client.mark_user_synced(1001, keyboard_signature(**authority.scopes[1001].keyboard_options))
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    bot.send_message.assert_awaited_once()
    assert await client.get_receipt(1001) == receipt_for(authority.scopes[1001])


@pytest.mark.asyncio
async def test_periodic_worker_recovers_after_fifth_failed_pass_without_restarting(fake_redis):
    import asyncio

    from bot.services.keyboard_sync import run_keyboard_sync

    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(3)
    for t in authority.scopes:
        await client.register_user(t)
    authority.fail = True
    bot = SimpleNamespace(send_message=AsyncMock())
    worker = asyncio.create_task(
        run_keyboard_sync(
            bot,
            client,
            bot_menu_client=authority,
            interval_seconds=0.001,
            delay_seconds=0,
            retry_delay_seconds=0,
        )
    )
    try:
        async with asyncio.timeout(5):
            while (await client.state(1003)).get("attempts", 0) < 5:
                await asyncio.sleep(0.001)
            assert not worker.done() and KEYBOARD_SYNC_PENDING._value.get() == 3
            authority.fail = False
            while KEYBOARD_SYNC_PENDING._value.get() != 0:
                await asyncio.sleep(0.001)
        assert bot.send_message.await_count == 3 and not worker.done()
        assert STARTUP_COMPLETED.labels("keyboard_sync")._value.get() == 1
    finally:
        worker.cancel()
        with pytest.raises(asyncio.CancelledError):
            await worker


@pytest.mark.asyncio
async def test_missing_or_fenced_menu_clears_old_role_ui_once_but_never_acknowledges_fallback(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    original = authority.menu_scope
    authority.menu_scope = AsyncMock(return_value=None)
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 2
    assert await client.get_receipt(1001) is None
    assert (await client.state(1001))["status"] == "retryable"
    labels = [b.text for row in bot.send_message.await_args.kwargs["reply_markup"].keyboard for b in row]
    assert HOMEWORK_TODAY_LABEL not in labels
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 2
    authority.menu_scope = original
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 3
    assert await client.get_receipt(1001) == receipt_for(authority.scopes[1001])
