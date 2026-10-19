"""Private admission for /start and /credentials; generic RPC never supplies a password."""

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
