"""Private admission for /start and /credentials; generic RPC never supplies a password."""

import asyncio
from dataclasses import replace
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.grpc_client.academic_pb2 import UserResponse, UserRoleGrant
from bot.handlers.prefs import HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL, keyboard_signature, main_keyboard
from bot.handlers.start import cmd_credentials, cmd_links, cmd_start
from bot.services.bot_menu import BotGrant, MenuScope
from bot.services.keyboard_sync import KeyboardSyncClient, sync_registered_keyboards


def _message(user_id=12345, *, chat_id=12345, chat_type="private", sender=True):
    return SimpleNamespace(
        from_user=SimpleNamespace(id=user_id) if sender else None,
        chat=SimpleNamespace(id=chat_id, type=chat_type),
        answer=AsyncMock(),
    )


def _profile(found=True, *, changed=False):
    return SimpleNamespace(
        found=found,
        user_id=17,
        telegram_binding_version=2,
        roles_version=1,
        display_name="Иван Иванов",
        login="current_login",
        initial_password="generic_ignored_marker",
        password_changed=changed,
        group_name="ИБ-21",
    )


async def _invoke(handler, message, academic, cards=None, menu=None):
    menu = menu or SimpleNamespace(
        own_profile=academic.get_user_by_telegram_id,
        menu_scope=AsyncMock(return_value=MenuScope(17, 2, 1, (BotGrant("STUDENT", 10),))),
    )
    if not hasattr(menu, "own_profile"):
        menu.own_profile = academic.get_user_by_telegram_id
    if handler is cmd_start:
        await handler(
            message, academic_client=academic, prefs_client=MagicMock(), credential_cards=cards, bot_menu_client=menu
        )
    else:
        await handler(message, academic_client=academic, credential_cards=cards, bot_menu_client=menu)


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
@pytest.mark.parametrize("scope", ["group", "supergroup", "channel", "missing", "negative", "mismatch", "bool"])
async def test_privacy_guard_precedes_every_profile_or_credential_lookup(handler, scope):
    message = _message(
        user_id=-1 if scope == "negative" else True if scope == "bool" else 12345,
        chat_id=999 if scope == "mismatch" else 12345,
        chat_type=scope if scope in {"group", "supergroup", "channel"} else "private",
        sender=scope != "missing",
    )
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock())
    cards = SimpleNamespace(deliver=AsyncMock())
    await _invoke(handler, message, academic, cards)
    academic.get_user_by_telegram_id.assert_not_awaited()
    cards.deliver.assert_not_awaited()
    assert message.answer.await_count == 1


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_unknown_binding_never_requests_credentials(handler):
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=_profile(False)))
    cards = SimpleNamespace(deliver=AsyncMock())
    await _invoke(handler, message, academic, cards)
    cards.deliver.assert_not_awaited()
    assert "Аккаунт не найден" in message.answer.await_args.args[0]


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_private_bound_handler_uses_only_dedicated_card_service(handler):
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=_profile()))
    cards = SimpleNamespace(deliver=AsyncMock(return_value=True))
    await _invoke(handler, message, academic, cards)
    academic.get_user_by_telegram_id.assert_awaited_once_with(12345)
    cards.deliver.assert_awaited_once()
    message.answer.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_generic_profile_marker_never_rendered_without_capability(handler):
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=_profile(changed=True)))
    await _invoke(handler, message, academic)
    text = message.answer.await_args.args[0]
    assert bool("generic_ignored_marker" not in text)
    assert "current_login" in text


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_authority_outage_has_safe_reply(handler):
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(side_effect=RuntimeError("synthetic outage")))
    await _invoke(handler, message, academic)
    assert "Сервис временно недоступен" in message.answer.await_args.args[0]


@pytest.mark.asyncio
async def test_links_retains_site_and_pwa_links():
    message = _message()
    await cmd_links(message)
    text = message.answer.await_args.args[0]
    assert "https://ru.ruttrack.site/app" in text
    assert "https://ruttrack.site/app" in text
    assert "PWA" in text


@pytest.mark.parametrize("student", [False, True])
async def test_start_menu_uses_current_grants_and_preserves_dedicated_card_capability(student):
    message = _message()
    roles = [UserRoleGrant(role="TEACHER", status="ACTIVE")]
    if student:
        roles.append(UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10))
    academic = SimpleNamespace(
        get_user_by_telegram_id=AsyncMock(return_value=_profile()),
        get_user_by_id=AsyncMock(return_value=UserResponse(roles_version=1, id=17, telegram_id=12345, roles=roles)),
    )
    cards = SimpleNamespace(deliver=AsyncMock(return_value=True))
    menu = SimpleNamespace(
        menu_scope=AsyncMock(
            return_value=MenuScope(
                17, 2, 1, tuple(BotGrant(grant.role, grant.group_id, grant.status) for grant in roles)
            )
        )
    )
    await _invoke(cmd_start, message, academic, cards, menu)
    labels = [button.text for row in cards.deliver.await_args.kwargs["reply_markup"].keyboard for button in row]
    assert (HOMEWORK_TODAY_LABEL in labels) is student
    assert (HOMEWORK_WEEK_LABEL in labels) is student
    academic.get_user_by_id.assert_not_awaited()
    menu.menu_scope.assert_awaited_once_with(12345)
    cards.deliver.assert_awaited_once()
    message.answer.assert_not_awaited()


async def test_start_authority_outage_denies_profile_and_credential_delivery():
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=_profile()))
    menu = SimpleNamespace(menu_scope=AsyncMock(side_effect=RuntimeError("PRIVATE_MARKER")))
    cards = SimpleNamespace(deliver=AsyncMock(return_value=True))
    await _invoke(cmd_start, message, academic, cards, menu)
    cards.deliver.assert_not_awaited()
    assert "current_login" not in message.answer.await_args.args[0]
    assert "Иван Иванов" not in message.answer.await_args.args[0]
    assert "PRIVATE_MARKER" not in message.answer.await_args.args[0]


@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
@pytest.mark.parametrize("changed", [None, "outage", "uid", "binding", "roles", "no_grant"])
async def test_found_profile_needs_positive_matching_current_authority_before_any_card_or_profile(handler, changed):
    current = MenuScope(17, 2, 1, (BotGrant("STUDENT", 10),))
    changed_scope = {
        None: None,
        "uid": replace(current, user_id=18),
        "binding": replace(current, telegram_binding_version=3),
        "roles": replace(current, roles_version=2),
        "no_grant": replace(current, grants=()),
    }.get(changed)
    menu = SimpleNamespace(
        own_profile=AsyncMock(return_value=_profile()), menu_scope=AsyncMock(return_value=changed_scope)
    )
    if changed == "outage":
        menu.menu_scope.side_effect = RuntimeError("PRIVATE_MARKER")
    cards = SimpleNamespace(deliver=AsyncMock(return_value=True))
    message = _message()
    await _invoke(handler, message, SimpleNamespace(), cards, menu)
    cards.deliver.assert_not_awaited()
    rendered = message.answer.await_args.args[0]
    assert all(value not in rendered for value in ("current_login", "Иван Иванов", "ИБ-21", "PRIVATE_MARKER"))
    assert "временно недоступен" in rendered
    if handler is cmd_start:
        assert len([button for row in message.answer.await_args.kwargs["reply_markup"].keyboard for button in row]) == 4


@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
@pytest.mark.parametrize(
    "changed",
    [
        "menu_missing",
        "menu_outage",
        "binding",
        "roles",
        "no_grant",
        "profile_unbound",
        "profile_rebound",
        "profile_outage",
    ],
)
async def test_denied_card_never_exposes_prior_profile_when_fallback_authority_changes(handler, changed):
    current = MenuScope(17, 2, 1, (BotGrant("STUDENT", 10),))
    fresh = _profile(changed=True)
    changed_scope = current
    if changed == "menu_missing":
        changed_scope = None
    elif changed == "menu_outage":
        changed_scope = RuntimeError("PRIVATE_MARKER")
    elif changed == "binding":
        changed_scope = replace(current, telegram_binding_version=3)
    elif changed == "roles":
        changed_scope = replace(current, roles_version=2)
    elif changed == "no_grant":
        changed_scope = replace(current, grants=())
    elif changed == "profile_unbound":
        fresh.found = False
    elif changed == "profile_rebound":
        fresh.user_id = 18
    elif changed == "profile_outage":
        fresh = RuntimeError("PRIVATE_MARKER")
    menu = SimpleNamespace(
        own_profile=AsyncMock(side_effect=[_profile(), fresh]),
        menu_scope=AsyncMock(side_effect=[current, changed_scope]),
    )
    cards = SimpleNamespace(deliver=AsyncMock(return_value=False))
    message = _message()
    await _invoke(handler, message, SimpleNamespace(), cards, menu)
    cards.deliver.assert_awaited_once()
    rendered = message.answer.await_args.args[0]
    assert all(value not in rendered for value in ("current_login", "Иван Иванов", "ИБ-21", "PRIVATE_MARKER"))
    assert "временно недоступен" in rendered
    if handler is cmd_start:
        assert len([button for row in message.answer.await_args.kwargs["reply_markup"].keyboard for button in row]) == 4


@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_password_changed_fallback_uses_only_newly_confirmed_profile_with_current_grant(handler):
    current = MenuScope(17, 2, 1, (BotGrant("STUDENT", 10),))
    fresh = _profile(changed=True)
    fresh.login, fresh.display_name, fresh.group_name = "fresh_login", "Текущее имя", "Текущая группа"
    menu = SimpleNamespace(
        own_profile=AsyncMock(side_effect=[_profile(), fresh]), menu_scope=AsyncMock(return_value=current)
    )
    cards = SimpleNamespace(deliver=AsyncMock(return_value=False))
    message = _message()
    await _invoke(handler, message, SimpleNamespace(), cards, menu)
    rendered = message.answer.await_args.args[0]
    assert "fresh_login" in rendered and "Текущее имя" in rendered and "Текущая группа" in rendered
    assert all(value not in rendered for value in ("current_login", "Иван Иванов", "ИБ-21", "generic_ignored_marker"))
    if handler is cmd_credentials:
        assert "Пароль: уже изменён" in rendered
    assert menu.own_profile.await_count == menu.menu_scope.await_count == 2


@pytest.mark.parametrize("failure", ["profile", "role", "card", "refresh", "send", "fallback_send"])
async def test_start_failure_never_acknowledges_role_keyboard_and_startup_recovers(fake_redis, failure):
    client = KeyboardSyncClient(redis_client=fake_redis)
    scope = MenuScope(17, 2, 1, (BotGrant("TEACHER"), BotGrant("STUDENT", 10)))
    signature = keyboard_signature(**scope.keyboard_options)
    # A previous successful /start must not conceal the common-keyboard fallback.
    await client.mark_user_synced(12345, signature)
    menu = SimpleNamespace(own_profile=AsyncMock(return_value=_profile()), menu_scope=AsyncMock(return_value=scope))
    cards = SimpleNamespace(deliver=AsyncMock(return_value=False))
    message = _message()
    if failure in {"profile", "fallback_send"}:
        menu.own_profile.side_effect = RuntimeError("outage")
    elif failure == "role":
        menu.menu_scope.side_effect = RuntimeError("outage")
    elif failure == "card":
        cards.deliver.side_effect = RuntimeError("render unavailable")
    elif failure == "refresh":
        menu.own_profile.side_effect = [_profile(), RuntimeError("outage")]
    elif failure == "send":
        message.answer.side_effect = [RuntimeError("provider failure"), None]
    if failure == "fallback_send":
        message.answer.side_effect = RuntimeError("provider failure")
        with pytest.raises(RuntimeError):
            await cmd_start(message, None, None, client, cards, menu)
    else:
        await cmd_start(message, None, None, client, cards, menu)
        labels = [b.text for row in message.answer.await_args.kwargs["reply_markup"].keyboard for b in row]
        assert len(labels) == 4
    assert await client.get_registered_user_ids() == [12345]
    assert await client.get_user_keyboard_version(12345) is None
    menu.menu_scope = AsyncMock(return_value=scope)
    bot = SimpleNamespace(send_message=AsyncMock())
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, bot_menu_client=menu)
    labels = [b.text for row in bot.send_message.await_args.kwargs["reply_markup"].keyboard for b in row]
    assert HOMEWORK_TODAY_LABEL in labels
    assert await client.get_user_keyboard_version(12345) == signature


@pytest.mark.parametrize("card_delivered", [False, True])
async def test_start_acknowledges_actual_role_keyboard_only_after_delivery(fake_redis, card_delivered):
    client = KeyboardSyncClient(redis_client=fake_redis)
    scope = MenuScope(17, 2, 1, (BotGrant("TEACHER"), BotGrant("STUDENT", 10), BotGrant("HEADMAN", 10)))
    menu = SimpleNamespace(own_profile=AsyncMock(return_value=_profile()), menu_scope=AsyncMock(return_value=scope))
    delivered = []

    async def send(*args, **kwargs):
        assert await client.get_user_keyboard_version(12345) is None
        delivered.append(kwargs["reply_markup"])

    async def card(*args, **kwargs):
        if card_delivered:
            await send(**kwargs)
        return card_delivered

    message = _message()
    message.answer.side_effect = send
    cards = SimpleNamespace(deliver=AsyncMock(side_effect=card))
    await cmd_start(message, None, None, client, cards, menu)
    assert len(delivered) == 1
    labels = [b.text for row in delivered[0].keyboard for b in row]
    assert len(labels) == len(set(labels))
    assert HOMEWORK_TODAY_LABEL in labels
    assert await client.get_user_keyboard_version(12345) == keyboard_signature(**scope.keyboard_options)


@pytest.mark.parametrize("failure", ["profile", "role", "card", "card_after_keyboard", "unknown"])
async def test_start_preserves_truthful_role_keyboard_when_pending_invalidation_fails(fake_redis, monkeypatch, failure):
    client = KeyboardSyncClient(redis_client=fake_redis)
    scope = MenuScope(17, 2, 1, (BotGrant("TEACHER"), BotGrant("STUDENT", 10)))
    signature = keyboard_signature(**scope.keyboard_options)
    await client.mark_user_synced(12345, signature)
    visible = main_keyboard(**scope.keyboard_options)
    original_hdel = fake_redis.hdel
    invalidations = 0

    async def hdel(*args, **kwargs):
        nonlocal invalidations
        invalidations += 1
        if invalidations == 1:
            raise RuntimeError("one-shot Redis failure")
        return await original_hdel(*args, **kwargs)

    monkeypatch.setattr(fake_redis, "hdel", hdel)

    async def answer(*args, **kwargs):
        nonlocal visible
        if kwargs.get("reply_markup") is not None:
            visible = kwargs["reply_markup"]

    message = _message()
    message.answer.side_effect = answer
    menu = SimpleNamespace(own_profile=AsyncMock(return_value=_profile()), menu_scope=AsyncMock(return_value=scope))
    cards = SimpleNamespace(deliver=AsyncMock(return_value=False))
    if failure == "profile":
        menu.own_profile.side_effect = RuntimeError("profile RPC unavailable")
    elif failure == "role":
        menu.menu_scope.side_effect = RuntimeError("role RPC unavailable")
    elif failure == "unknown":
        menu.own_profile.return_value = _profile(False)
    else:

        async def failed_card(*args, **kwargs):
            if failure == "card_after_keyboard":
                await message.answer("nonsecret placeholder", reply_markup=kwargs["reply_markup"])
            raise RuntimeError("credential card unavailable")

        cards.deliver.side_effect = failed_card
    await cmd_start(message, None, None, client, cards, menu)
    assert message.answer.await_args.kwargs["reply_markup"] is None
    expected = [button.text for row in main_keyboard(**scope.keyboard_options).keyboard for button in row]
    assert [button.text for row in visible.keyboard for button in row] == expected
    assert await client.get_user_keyboard_version(12345) == signature
    assert await client.get_registered_user_ids() == [12345]
    menu.menu_scope = AsyncMock(return_value=scope)
    bot = SimpleNamespace(send_message=AsyncMock())
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    # Recovery may skip only because /start has preserved the actually visible role keyboard.
    bot.send_message.assert_not_awaited()
    assert [button.text for row in visible.keyboard for button in row] == expected


def _memory_keyboard_client():
    versions = {}
    registered = set()

    async def sadd(key, user):
        registered.add(user)

    async def hset(key, user, version):
        versions[user] = version

    async def hdel(key, user):
        versions.pop(user, None)

    redis = SimpleNamespace(
        sadd=AsyncMock(side_effect=sadd),
        smembers=AsyncMock(side_effect=lambda key: registered.copy()),
        hget=AsyncMock(side_effect=lambda key, user: versions.get(user)),
        hset=AsyncMock(side_effect=hset),
        hdel=AsyncMock(side_effect=hdel),
    )
    return KeyboardSyncClient(redis_client=redis)


@pytest.mark.parametrize("outcome", ["rpc", "unknown", "send", "card_after_keyboard", "success"])
async def test_start_and_startup_publish_one_coherent_keyboard_per_chat(outcome):
    # Immediate in-memory Redis replies make the loop checkpoint causal: startup
    # either finishes or waits for the same-chat publisher, without clock timing.
    client = _memory_keyboard_client()
    scope = MenuScope(17, 2, 1, (BotGrant("TEACHER"), BotGrant("STUDENT", 10)))
    signature = keyboard_signature(**scope.keyboard_options)
    await client.register_user(12345)
    await client.mark_user_synced(12345, signature)
    delivered = []
    profile_entered = asyncio.Event()
    release_profile = asyncio.Event()

    async def own_profile(user):
        profile_entered.set()
        await release_profile.wait()
        if outcome == "rpc":
            raise RuntimeError("profile unavailable")
        return _profile(found=outcome != "unknown")

    menu = SimpleNamespace(own_profile=AsyncMock(side_effect=own_profile), menu_scope=AsyncMock(return_value=scope))
    role_send_failed = False

    async def deliver(*args, **kwargs):
        nonlocal role_send_failed
        markup = kwargs.get("reply_markup")
        if markup is None:
            return
        labels = [button.text for row in markup.keyboard for button in row]
        delivered.append(labels)

    async def start_deliver(*args, **kwargs):
        nonlocal role_send_failed
        markup = kwargs.get("reply_markup")
        if outcome == "send" and markup is not None and len(markup.keyboard) > 3 and not role_send_failed:
            role_send_failed = True
            raise RuntimeError("role send unavailable")
        await deliver(*args, **kwargs)

    async def card(*args, **kwargs):
        if outcome == "card_after_keyboard":
            await deliver(**kwargs)
            raise RuntimeError("card completion unavailable")
        return False

    message = _message()
    message.answer.side_effect = start_deliver
    cards = SimpleNamespace(deliver=AsyncMock(side_effect=card))
    start = asyncio.create_task(cmd_start(message, None, None, client, cards, menu))
    await profile_entered.wait()  # /start invalidated, then blocked in its RPC.
    startup_menu = SimpleNamespace(menu_scope=AsyncMock(return_value=scope))
    bot = SimpleNamespace(send_message=AsyncMock(side_effect=deliver))
    startup = asyncio.create_task(
        sync_registered_keyboards(
            bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=startup_menu
        )
    )
    checkpoint = asyncio.get_running_loop().create_future()
    asyncio.get_running_loop().call_soon(checkpoint.set_result, None)
    await checkpoint  # startup ran until completion or the held per-chat publication.
    before_rpc_returned = list(delivered)
    release_profile.set()
    await asyncio.gather(start, startup)
    expected = [button.text for row in main_keyboard(**scope.keyboard_options).keyboard for button in row]
    stored = await client.get_user_keyboard_version(12345)
    visible_at_completion = delivered[-1]
    sends = len(delivered)
    await sync_registered_keyboards(
        bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=startup_menu
    )
    assert visible_at_completion == expected, (outcome, before_rpc_returned, delivered, stored)
    assert stored == signature
    assert len(delivered) == sends  # skip only when the actually visible keyboard matches.


@pytest.mark.parametrize("cancelled", ["holder", "waiter"])
async def test_keyboard_publication_cancellation_releases_chat_without_blocking_other_chats(cancelled):
    client = _memory_keyboard_client()
    scope = MenuScope(17, 2, 1, (BotGrant("STUDENT", 10),))
    signature = keyboard_signature(**scope.keyboard_options)
    entered = asyncio.Event()
    release = asyncio.Event()
    delivered = {}

    async def own_profile(user):
        if user == 12345:
            entered.set()
            await release.wait()
            raise RuntimeError("profile unavailable")
        return _profile()

    async def send(user, **kwargs):
        if kwargs.get("reply_markup") is not None:
            delivered[user] = kwargs["reply_markup"]

    async def bot_send(**kwargs):
        await send(kwargs["chat_id"], **kwargs)

    first = _message()

    async def first_answer(*args, **kwargs):
        await send(12345, **kwargs)

    first.answer.side_effect = first_answer
    menu = SimpleNamespace(own_profile=AsyncMock(side_effect=own_profile), menu_scope=AsyncMock(return_value=scope))
    holder = asyncio.create_task(cmd_start(first, None, None, client, None, menu))
    await entered.wait()
    bot = SimpleNamespace(send_message=AsyncMock(side_effect=bot_send))
    waiter = asyncio.create_task(
        sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    )
    checkpoint = asyncio.get_running_loop().create_future()
    asyncio.get_running_loop().call_soon(checkpoint.set_result, None)
    await checkpoint
    assert not waiter.done()
    other = _message(67890, chat_id=67890)

    async def other_answer(*args, **kwargs):
        await send(67890, **kwargs)

    other.answer.side_effect = other_answer
    await cmd_start(other, None, None, client, None, menu)
    assert await client.get_user_keyboard_version(67890) == signature
    assert 67890 in delivered and 12345 not in delivered
    task = holder if cancelled == "holder" else waiter
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task
    release.set()
    await (waiter if cancelled == "holder" else holder)
    if cancelled == "waiter":
        assert len([button for row in delivered[12345].keyboard for button in row]) == 4
        assert await client.get_user_keyboard_version(12345) is None
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    expected = main_keyboard(**scope.keyboard_options)
    assert delivered[12345] == expected and delivered[67890] == expected
    assert await client.get_user_keyboard_version(12345) == signature
    assert await client.get_user_keyboard_version(67890) == signature
    assert not client._publication_locks  # completed/cancelled chats do not accumulate forever.


async def test_start_waits_for_startup_delivery_and_receipt_before_its_fallback():
    client = _memory_keyboard_client()
    await client.register_user(12345)
    scope = MenuScope(17, 2, 1, (BotGrant("TEACHER"), BotGrant("STUDENT", 10)))
    signature = keyboard_signature(**scope.keyboard_options)
    entered = asyncio.Event()
    release = asyncio.Event()
    delivered = []

    async def startup_send(**kwargs):
        entered.set()
        await release.wait()
        delivered.append(kwargs["reply_markup"])

    bot = SimpleNamespace(send_message=AsyncMock(side_effect=startup_send))
    menu = SimpleNamespace(menu_scope=AsyncMock(return_value=scope))
    startup = asyncio.create_task(
        sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    )
    await entered.wait()
    message = _message()

    async def answer(*args, **kwargs):
        delivered.append(kwargs["reply_markup"])

    message.answer.side_effect = answer
    start_menu = SimpleNamespace(own_profile=AsyncMock(side_effect=RuntimeError("profile unavailable")))
    start = asyncio.create_task(cmd_start(message, None, None, client, None, start_menu))
    checkpoint = asyncio.get_running_loop().create_future()
    asyncio.get_running_loop().call_soon(checkpoint.set_result, None)
    await checkpoint
    release.set()
    await asyncio.gather(startup, start)
    assert delivered == [main_keyboard(**scope.keyboard_options), main_keyboard()]
    assert await client.get_user_keyboard_version(12345) is None
    await sync_registered_keyboards(bot, client, None, delay_seconds=0, retry_delay_seconds=0, bot_menu_client=menu)
    assert delivered[-1] == main_keyboard(**scope.keyboard_options)
    assert await client.get_user_keyboard_version(12345) == signature
