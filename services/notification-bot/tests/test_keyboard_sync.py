import json
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


async def request_menu(client, *telegram_ids):
    for telegram_id in telegram_ids:
        await client.register_user(telegram_id, reactivate=True)


async def cycle(client, authority, bot, *, retry=0):
    await sync_registered_keyboards(bot, client, bot_menu_client=authority, delay_seconds=0, retry_delay_seconds=retry)


@pytest.mark.asyncio
async def test_outage_beyond_three_attempts_recovers_three_pending_without_restart(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority()
    for t in authority.scopes:
        await client.register_user(t, reactivate=True)
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
async def test_restart_resumes_explicit_pending_and_skips_changed_markup_with_same_authority(fake_redis, monkeypatch):
    first = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await request_menu(first, 1001)
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
    import bot.services.keyboard_sync as module

    old_receipt = await restarted.get_receipt(1001)
    monkeypatch.setattr(module, "keyboard_signature", lambda **options: "new-" + keyboard_signature(**options))
    await cycle(restarted, authority, bot)
    assert bot.send_message.await_count == count
    assert await restarted.get_receipt(1001) == old_receipt
    assert KEYBOARD_SYNC_PENDING._value.get() == 0
    await restarted.invalidate_user_keyboard(1001)
    await cycle(restarted, authority, bot)
    assert bot.send_message.await_count == count + 1
    assert await restarted.get_receipt(1001) == receipt_for(authority.scopes[1001])


@pytest.mark.asyncio
async def test_receipt_write_failure_never_acknowledges_and_retries_after_ack(fake_redis, monkeypatch):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await request_menu(client, *authority.scopes)
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
    await request_menu(client, *authority.scopes)
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
    await request_menu(client, *authority.scopes)
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
async def test_terminal_provider_failure_requires_a_new_explicit_request(fake_redis, kind):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await request_menu(client, *authority.scopes)
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
    await client.invalidate_user_keyboard(1001)  # same operation used by /start
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
    bot.send_message.assert_not_awaited()
    assert KEYBOARD_SYNC_PENDING._value.get() == 0
    assert (await client.state(1001))["status"] == "idle"
    # Existing deployments can have legacy pending records from discovery.
    await client.set_state(1001, dict(status="pending", attempts=0, next_at=0, since=1.0))
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    bot.send_message.assert_not_awaited()
    assert (await client.state(1001))["status"] == "idle"
    assert await client.get_receipt(1001) is None


@pytest.mark.asyncio
async def test_auth_outage_and_recovery_do_not_broadcast_to_existing_chats(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority()
    bot = SimpleNamespace(send_message=AsyncMock())
    for telegram_id, scope in authority.scopes.items():
        await client.register_user(telegram_id)
        await client.mark_user_synced(telegram_id, keyboard_signature(**scope.keyboard_options), scope)
    authority.fail = True
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    assert STARTUP_COMPLETED.labels("keyboard_sync")._value.get() == 0
    for telegram_id, scope in authority.scopes.items():
        assert await client.get_receipt(telegram_id) == receipt_for(scope)
    authority.fail = False
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    bot.send_message.assert_not_awaited()
    assert KEYBOARD_SYNC_PENDING._value.get() == 0
    assert STARTUP_COMPLETED.labels("keyboard_sync")._value.get() == 1


@pytest.mark.asyncio
async def test_identical_role_keyboard_keeps_ack_through_authority_and_later_markup_changes(fake_redis, monkeypatch):
    import bot.services.keyboard_sync as module

    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    old = authority.scopes[1001]
    await client.register_user(1001)
    await client.mark_user_synced(1001, keyboard_signature(**old.keyboard_options), old)
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    authority.scopes[1001] = replace(old, roles_version=2, grants=(BotGrant("STUDENT", 11),))
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    bot.send_message.assert_not_awaited()
    assert await client.get_receipt(1001) == receipt_for(old)
    old_receipt = await client.get_receipt(1001)
    monkeypatch.setattr(module, "keyboard_signature", lambda **options: "new-" + keyboard_signature(**options))
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    bot.send_message.assert_not_awaited()
    assert await client.get_receipt(1001) == old_receipt
    assert KEYBOARD_SYNC_PENDING._value.get() == 0


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("evidence", "legacy_signature"),
    [
        ("pre_eb7_receipt", "1c23dcdcaa1560c5"),
        ("eb7_receipt", "c8dbd067ac8457e4"),
        ("common_ack", None),
        ("uncertain_role_send", None),
    ],
)
async def test_common_keyboard_is_not_replaced_on_authority_loss_after_markup_change(
    fake_redis, monkeypatch, evidence, legacy_signature
):
    import bot.services.keyboard_sync as module

    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    scope = replace(authority.scopes[1001], grants=(BotGrant("ADMIN"),))
    authority.scopes[1001] = scope
    await client.register_user(1001)
    if legacy_signature is not None:
        await client.mark_user_synced(1001, legacy_signature)
        identity = [scope.user_id, scope.telegram_binding_version, scope.roles_version]
        old_receipt = json.dumps([legacy_signature, identity])
        await fake_redis.hset(client._RECEIPTS_KEY, "1001", old_receipt)
    else:
        await client.mark_user_synced(1001, keyboard_signature(), scope)
        old_receipt = await client.get_receipt(1001)
        if evidence == "uncertain_role_send":
            await client.note_possible_publication(1001, keyboard_signature(homework_enabled=True))
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    bot.send_message.assert_not_awaited()

    monkeypatch.setattr(module, "keyboard_signature", lambda **options: "new-" + keyboard_signature(**options))
    authority.menu_scope = AsyncMock(return_value=replace(scope, grants=()))
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)

    if evidence == "uncertain_role_send":
        bot.send_message.assert_awaited_once()
        assert await client.get_receipt(1001) is None
    else:
        bot.send_message.assert_not_awaited()
        assert await client.get_receipt(1001) == old_receipt


@pytest.mark.asyncio
@pytest.mark.parametrize("evidence", ["legacy_signature", "possible_publication"])
async def test_idle_legacy_role_evidence_only_triggers_affirmative_revocation(fake_redis, evidence):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    scope = authority.scopes[1001]
    signature = keyboard_signature(**scope.keyboard_options)
    await client.register_user(1001)
    if evidence == "legacy_signature":
        await client.mark_user_synced(1001, signature)
    else:
        await client.note_possible_publication(1001, signature)
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    bot.send_message.assert_not_awaited()
    assert await client.get_receipt(1001) is None
    assert await client.ui_needs_cleanup(1001)
    authority.menu_scope = AsyncMock(return_value=None)
    await cycle(client, authority, bot)
    bot.send_message.assert_awaited_once()
    labels = [button.text for row in bot.send_message.await_args.kwargs["reply_markup"].keyboard for button in row]
    assert HOMEWORK_TODAY_LABEL not in labels
    assert not await client.ui_needs_cleanup(1001)


@pytest.mark.asyncio
async def test_missing_menu_is_retryable_not_unbound_and_failed_sweep_cannot_remove_recipient(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await request_menu(client, 1001)
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
    bot.send_message.assert_not_awaited()
    await request_menu(client, 1001)
    await cycle(client, authority, bot)
    assert await client.get_receipt(1001) is not None


@pytest.mark.parametrize("when", ["before", "during"])
@pytest.mark.asyncio
async def test_role_race_never_records_stale_receipt_and_next_pass_delivers_current_keyboard(fake_redis, when):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await request_menu(client, *authority.scopes)
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
    await client.register_user(1001, reactivate=True)
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
        await client.register_user(t, reactivate=True)
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
    await request_menu(client, *authority.scopes)
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    original = authority.menu_scope
    authority.menu_scope = AsyncMock(return_value=None)
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 2
    assert await client.get_receipt(1001) is None
    assert (await client.state(1001))["status"] == "idle"
    labels = [b.text for row in bot.send_message.await_args.kwargs["reply_markup"].keyboard for b in row]
    assert HOMEWORK_TODAY_LABEL not in labels
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 2
    authority.menu_scope = original
    await cycle(client, authority, bot)
    assert bot.send_message.await_count == 2
    assert await client.get_receipt(1001) is None


@pytest.mark.asyncio
async def test_completed_unbinding_keeps_cleanup_pending_until_common_ack_after_restart(fake_redis):
    from bot.handlers.prefs import main_keyboard

    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await request_menu(client, *authority.scopes)
    visible = []

    async def delivered(**kwargs):
        visible.append(kwargs["reply_markup"])

    bot = SimpleNamespace(send_message=AsyncMock(side_effect=delivered))
    await cycle(client, authority, bot)
    role_ui = visible[-1]
    authority.scopes.clear()  # A completed catalogue proves unbound, not safe UI.
    bot.send_message.side_effect = RuntimeError("common replacement unavailable")
    await cycle(client, authority, bot)
    assert visible[-1] == role_ui
    assert (await client.state(1001))["status"] in {"pending", "retryable"}
    assert KEYBOARD_SYNC_PENDING._value.get() == 1
    assert STARTUP_COMPLETED.labels("keyboard_sync")._value.get() == 0

    restarted = KeyboardSyncClient(redis_client=fake_redis)
    bot.send_message.side_effect = delivered
    await cycle(restarted, authority, bot)
    assert visible[-1] == main_keyboard()
    assert (await restarted.state(1001)) == dict(status="terminal", reason="unbound")
    assert await restarted.get_receipt(1001) is None  # A common ACK is not an authority receipt.
    assert KEYBOARD_SYNC_PENDING._value.get() == 0
    sends = bot.send_message.await_count
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    assert bot.send_message.await_count == sends


@pytest.mark.parametrize("loss_at", ["before_send", "after_ack"])
@pytest.mark.asyncio
async def test_authority_loss_preserves_unsafe_ui_across_restart_and_catalogue_reset(fake_redis, loss_at):
    from bot.handlers.prefs import main_keyboard

    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await request_menu(client, *authority.scopes)
    visible = []

    async def delivered(**kwargs):
        visible.append(kwargs["reply_markup"])

    bot = SimpleNamespace(send_message=AsyncMock(side_effect=delivered))
    await cycle(client, authority, bot)
    new = replace(authority.scopes[1001], roles_version=2, grants=(BotGrant("TEACHER"),))
    authority.scopes[1001] = new
    reads = 0

    async def menu(sender):
        nonlocal reads
        reads += 1
        return new if reads <= (1 if loss_at == "before_send" else 2) else None

    authority.menu_scope = menu
    await cycle(client, authority, bot)
    assert await client.get_receipt(1001) is None
    assert visible[-1] != main_keyboard()
    if loss_at == "before_send":
        assert bot.send_message.await_count == 1
    else:
        assert bot.send_message.await_count == 2

    # Fresh discovery resets retry state for the changed version; UI risk must
    # remain durable independently of that state and the deleted receipt.
    authority.scopes[1001] = replace(new, roles_version=3)
    restarted = KeyboardSyncClient(redis_client=fake_redis)
    await cycle(restarted, authority, bot)
    assert visible[-1] == main_keyboard()
    assert (await restarted.state(1001))["status"] == "idle"
    assert await restarted.get_receipt(1001) is None
    sends = bot.send_message.await_count
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    assert bot.send_message.await_count == sends  # Known common ACK is not repeated.
    authority.menu_scope = AsyncMock(return_value=authority.scopes[1001])
    await request_menu(restarted, 1001)
    await cycle(restarted, authority, bot)
    assert await restarted.get_receipt(1001) == receipt_for(authority.scopes[1001])
    assert visible[-1] != main_keyboard()


@pytest.mark.asyncio
async def test_unbound_common_cleanup_provider_terminal_is_not_hot_retried(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    authority = Authority(1)
    await request_menu(client, *authority.scopes)
    bot = SimpleNamespace(send_message=AsyncMock())
    await cycle(client, authority, bot)
    authority.scopes.clear()
    bot.send_message.side_effect = TelegramForbiddenError(SendMessage(chat_id=1001, text="synthetic"), "blocked")
    await cycle(client, authority, bot)
    assert (await client.state(1001)) == dict(status="terminal", reason="provider_unreachable")
    assert KEYBOARD_SYNC_PENDING._value.get() == 0
    sends = bot.send_message.await_count
    await cycle(KeyboardSyncClient(redis_client=fake_redis), authority, bot)
    assert bot.send_message.await_count == sends
